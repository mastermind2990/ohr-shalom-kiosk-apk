package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.PaymentIntentStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.external.models.TapToPayConnectionConfiguration
import com.stripe.stripeterminal.external.models.TapToPayDiscoveryConfiguration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Enhanced Stripe Payment Manager for Real NFC/Tap to Pay functionality
 */
class StripePaymentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StripePaymentManager"
    }
    
    private var currentPaymentIntent: PaymentIntent? = null
    private var currentCancelable: Cancelable? = null
    private var connectedReader: Reader? = null
    private var connectionTokenEndpoint: String? = null
    private var locationId: String? = null
    private var discoveryInProgress: Boolean = false
    
    private val httpClient = OkHttpClient()
    
    init {
        initializeStripeTerminal()
        // Auto-configure with hardcoded production values for immediate Terminal registration
        configureProductionDefaults()
    }
    
    private fun initializeStripeTerminal() {
        try {
            if (!Terminal.isInitialized()) {
                Terminal.initTerminal(
                    context.applicationContext,
                    tokenProvider = object : ConnectionTokenProvider {
                        override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
                            fetchConnectionTokenFromBackend(callback)
                        }
                    },
                    listener = object : TerminalListener {
                        override fun onConnectionStatusChange(status: ConnectionStatus) {
                            Log.d(TAG, "Connection status changed: $status")
                            if (status == ConnectionStatus.NOT_CONNECTED) {
                                connectedReader = null
                            }
                        }
                        
                        override fun onPaymentStatusChange(status: PaymentStatus) {
                            Log.d(TAG, "Payment status changed: $status")
                        }
                    }
                )
                Log.d(TAG, "Stripe Terminal initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe Terminal", e)
        }
    }
    
    /**
     * Configure with hardcoded production values for immediate Terminal registration
     */
    private fun configureProductionDefaults() {
        try {
            Log.d(TAG, "Configuring production defaults for automatic Terminal registration")
            
            // Hardcoded production values
            val productionPublishableKey = "pk_live_51Q5QhsJhCdJUSe2h1hl7iqL7YLmprQQMu7FLmkDzULDwacidH6LmzH4dbodT2k2FP7Sh9whkLmZ5YHmGFEi4MrtE0081NqrCtr"
            val productionTokenEndpoint = "http://161.35.140.12/api/stripe/connection_token"
            val productionLocationId = "tml_GKsXoQ8u9cFZJF"
            
            // Configure automatically
            val success = updateConfiguration(
                publishableKey = productionPublishableKey,
                tokenEndpoint = productionTokenEndpoint,
                locationId = productionLocationId,
                isLiveMode = true
            )
            
            if (success) {
                Log.d(TAG, "Production defaults configured successfully - Terminal registration will begin automatically")
            } else {
                Log.w(TAG, "Failed to configure production defaults")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring production defaults", e)
        }
    }
    
    private fun fetchConnectionTokenFromBackend(callback: ConnectionTokenCallback) {
        val endpoint = connectionTokenEndpoint
        if (endpoint.isNullOrBlank()) {
            Log.e(TAG, "Connection token endpoint not configured")
            callback.onFailure(ConnectionTokenException("Connection token endpoint not configured"))
            return
        }
        
        Log.d(TAG, "Fetching connection token from: $endpoint")
        
        val request = Request.Builder()
            .url(endpoint)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch connection token", e)
                callback.onFailure(ConnectionTokenException("Network error: ${e.message}"))
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val secret = json.getString("secret")
                        Log.d(TAG, "Connection token received successfully")
                        callback.onSuccess(secret)
                    } else {
                        Log.e(TAG, "Failed to get connection token: ${response.code}")
                        callback.onFailure(ConnectionTokenException("Server error: ${response.code}"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing connection token response", e)
                    callback.onFailure(ConnectionTokenException("Parse error: ${e.message}"))
                }
            }
        })
    }
    
    /**
     * Process real NFC payment using Stripe Terminal
     */
    suspend fun processNfcPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return try {
            Log.d(TAG, "Processing real NFC payment: $amountCents cents")
            
            // Ensure Tap to Pay reader is connected
            if (connectedReader == null) {
                Log.w(TAG, "Tap to Pay reader not connected, attempting to connect...")
                discoverAndConnectReader()
                
                // Wait a moment for connection
                kotlinx.coroutines.delay(3000)
                
                if (connectedReader == null) {
                    Log.e(TAG, "Cannot process payment - Tap to Pay reader not connected")
                    return false
                }
            }
            
            // Create payment intent
            val paymentIntent = createPaymentIntent(amountCents, currency, email)
            
            // Collect payment method using NFC
            val collectedPaymentIntent = collectPaymentMethod(paymentIntent)
            
            // Confirm payment intent
            val confirmedPaymentIntent = confirmPaymentIntent(collectedPaymentIntent)
            
            val success = confirmedPaymentIntent.status == PaymentIntentStatus.SUCCEEDED
            
            if (success) {
                Log.d(TAG, "Real NFC payment completed successfully: ${confirmedPaymentIntent.id}")
            } else {
                Log.w(TAG, "NFC payment failed or incomplete: ${confirmedPaymentIntent.status}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error processing real NFC payment", e)
            false
        }
    }
    
    private suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Collecting payment method via NFC...")
            
            Terminal.getInstance().collectPaymentMethod(paymentIntent, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "Payment method collected successfully")
                    continuation.resume(paymentIntent)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to collect payment method", exception)
                    continuation.resumeWithException(exception)
                }
            })
        }
    }
    
    private suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Confirming payment intent...")
            
            Terminal.getInstance().confirmPaymentIntent(paymentIntent, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "Payment intent confirmed successfully")
                    continuation.resume(paymentIntent)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to confirm payment intent", exception)
                    continuation.resumeWithException(exception)
                }
            })
        }
    }
    
    private suspend fun createPaymentIntent(amountCents: Int, currency: String, email: String?): PaymentIntent {
        return suspendCancellableCoroutine { continuation ->
            val params = PaymentIntentParameters.Builder()
                .setAmount(amountCents.toLong())
                .setCurrency(currency)
                .apply {
                    email?.let { setReceiptEmail(it) }
                    // Note: PaymentMethodTypes are automatically set for Terminal payments
                }
                .build()
            
            Terminal.getInstance().createPaymentIntent(params, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "Payment intent created: ${paymentIntent.id}")
                    currentPaymentIntent = paymentIntent
                    continuation.resume(paymentIntent)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to create payment intent", exception)
                    continuation.resumeWithException(exception)
                }
            })
        }
    }
    
    /**
     * Get Terminal status information
     */
    fun getTerminalStatus(): String {
        return try {
            val isInitialized = Terminal.isInitialized()
            val hasEndpoint = !connectionTokenEndpoint.isNullOrBlank()
            val hasLocation = !locationId.isNullOrBlank()
            val reader = connectedReader
            
            when {
                !isInitialized -> "Terminal not initialized"
                !hasEndpoint -> "Connection endpoint not configured"
                !hasLocation -> "Location ID not configured"
                reader == null -> "Tap to Pay reader not registered (Location: $locationId)"
                else -> "Ready - Tap to Pay registered (Reader: ${reader.id}, Location: $locationId)"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    /**
     * Cancel current payment if in progress
     */
    fun cancelCurrentPayment() {
        try {
            currentCancelable?.cancel(object : Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Payment cancelled successfully")
                    currentPaymentIntent = null
                    currentCancelable = null
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to cancel payment", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling payment", e)
        }
    }
    
    /**
     * Check if NFC is available on the device
     */
    fun isNfcAvailable(): Boolean {
        return try {
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            nfcAdapter != null && nfcAdapter.isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking NFC availability", e)
            false
        }
    }
    
    /**
     * Get payment status information
     */
    fun getPaymentStatus(): String {
        return when {
            currentPaymentIntent != null -> "processing"
            Terminal.isInitialized() -> "ready"
            else -> "not_initialized"
        }
    }
    
    /**
     * Get current payment intent if any
     */
    fun getCurrentPaymentIntent(): PaymentIntent? {
        return currentPaymentIntent
    }
    
    /**
     * Update Stripe configuration and connect reader
     */
    fun updateConfiguration(
        publishableKey: String,
        tokenEndpoint: String?,
        locationId: String?,
        isLiveMode: Boolean
    ): Boolean {
        return try {
            Log.d(TAG, "Updating Stripe configuration - Live mode: $isLiveMode")
            
            this.connectionTokenEndpoint = tokenEndpoint
            this.locationId = locationId
            
            Log.d(TAG, "Stripe configuration updated:")
            Log.d(TAG, "  - Publishable Key: ${publishableKey.take(10)}...")
            Log.d(TAG, "  - Token Endpoint: $tokenEndpoint")
            Log.d(TAG, "  - Location ID: $locationId")
            Log.d(TAG, "  - Live Mode: $isLiveMode")
            
            // If we have all required info, try to discover and connect reader
            if (!tokenEndpoint.isNullOrBlank() && !locationId.isNullOrBlank()) {
                discoverAndConnectReader()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Stripe configuration", e)
            false
        }
    }
    
    private fun discoverAndConnectReader() {
        try {
            Log.d(TAG, "Discovering and registering Tap to Pay reader...")
            
            val locationId = this.locationId
            if (locationId.isNullOrBlank()) {
                Log.e(TAG, "Location ID not configured for Tap to Pay")
                return
            }
            
            if (discoveryInProgress) {
                Log.d(TAG, "Discovery already in progress")
                return
            }
            
            discoveryInProgress = true
            
            // Discover Tap to Pay readers
            val isSimulated = false // Set to false for production
            val discoveryConfig = TapToPayDiscoveryConfiguration(isSimulated)
            
            Terminal.getInstance().discoverReaders(
                discoveryConfig,
                object : DiscoveryListener {
                    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                        Log.d(TAG, "Discovered ${readers.size} Tap to Pay readers")
                        if (readers.isNotEmpty()) {
                            // Connect to the first discovered reader (the tablet)
                            connectToTapToPayReader(readers[0], locationId)
                        }
                    }
                },
                object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "Tap to Pay reader discovery completed")
                        discoveryInProgress = false
                    }
                    
                    override fun onFailure(exception: TerminalException) {
                        Log.e(TAG, "Tap to Pay reader discovery failed: ${exception.errorMessage}")
                        Log.e(TAG, "Error code: ${exception.errorCode}")
                        discoveryInProgress = false
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering Tap to Pay reader", e)
            discoveryInProgress = false
        }
    }
    
    private fun connectToTapToPayReader(reader: Reader, locationId: String) {
        try {
            Log.d(TAG, "Connecting to Tap to Pay reader and registering to location: $locationId")
            
            val connectionConfig = TapToPayConnectionConfiguration(
                locationId = locationId,
                autoReconnectOnUnexpectedDisconnect = true,
                tapToPayReaderListener = object : TapToPayReaderListener {
                    override fun onDisconnect(reason: com.stripe.stripeterminal.external.models.DisconnectReason) {
                        Log.w(TAG, "Tap to Pay reader disconnected: $reason")
                        connectedReader = null
                    }
                    
                    override fun onReaderEvent(event: com.stripe.stripeterminal.external.models.ReaderEvent) {
                        Log.d(TAG, "Tap to Pay reader event: $event")
                    }
                }
            )
            
            Terminal.getInstance().connectReader(
                reader,
                connectionConfig,
                object : ReaderCallback {
                    override fun onSuccess(connectedReader: Reader) {
                        Log.d(TAG, "Successfully connected and registered Tap to Pay reader: ${connectedReader.id}")
                        Log.d(TAG, "Reader location: ${connectedReader.location}")
                        Log.d(TAG, "Reader device type: ${connectedReader.deviceType}")
                        this@StripePaymentManager.connectedReader = connectedReader
                    }
                    
                    override fun onFailure(exception: TerminalException) {
                        Log.e(TAG, "Failed to connect Tap to Pay reader: ${exception.errorMessage}")
                        Log.e(TAG, "Error code: ${exception.errorCode}")
                        
                        // Provide more specific error information
                        when (exception.errorCode) {
                            com.stripe.stripeterminal.external.models.TerminalErrorCode.UNSUPPORTED_SDK -> {
                                Log.e(TAG, "Device does not support Tap to Pay")
                            }
                            com.stripe.stripeterminal.external.models.TerminalErrorCode.READER_CONNECTION_FAILED -> {
                                Log.e(TAG, "Reader connection failed - check network and device requirements")
                            }
                            else -> {
                                Log.e(TAG, "Other connection error: ${exception.errorCode}")
                            }
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Tap to Pay reader", e)
        }
    }

    fun cleanup() {
        try {
            cancelCurrentPayment()
            
            // Disconnect reader if connected
            connectedReader?.let {
                Terminal.getInstance().disconnectReader(object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "Tap to Pay reader disconnected successfully")
                    }
                    
                    override fun onFailure(exception: TerminalException) {
                        Log.e(TAG, "Failed to disconnect reader", exception)
                    }
                })
            }
            
            connectedReader = null
            currentPaymentIntent = null
            currentCancelable = null
            discoveryInProgress = false
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}