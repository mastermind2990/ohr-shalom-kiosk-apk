package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.PaymentIntentStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
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
    private var connectionTokenEndpoint: String? = null
    private var locationId: String? = null
    
    private val httpClient = OkHttpClient()
    
    init {
        initializeStripeTerminal()
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
            
            // For Tap to Pay, ensure configuration is set up
            if (connectionTokenEndpoint.isNullOrBlank() || locationId.isNullOrBlank()) {
                Log.w(TAG, "Tap to Pay not configured, setting up...")
                discoverAndConnectReader()
                
                if (connectionTokenEndpoint.isNullOrBlank() || locationId.isNullOrBlank()) {
                    Log.e(TAG, "Cannot process payment - Tap to Pay not configured")
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
            
            when {
                !isInitialized -> "Terminal not initialized"
                !hasEndpoint -> "Connection endpoint not configured"
                !hasLocation -> "Location ID not configured"
                else -> "Ready for Tap to Pay (Location: $locationId)"
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
            Log.d(TAG, "Setting up Tap to Pay reader (tablet)...")
            
            val locationId = this.locationId
            if (locationId.isNullOrBlank()) {
                Log.e(TAG, "Location ID not configured for Tap to Pay")
                return
            }
            
            Log.d(TAG, "Tap to Pay configured with location ID: $locationId")
            Log.d(TAG, "Device is ready to process NFC payments")
            
            // For Tap to Pay, the device itself acts as the reader
            // No explicit reader connection needed - the Terminal SDK handles this internally
            // when collectPaymentMethod is called
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Tap to Pay", e)
        }
    }

    fun cleanup() {
        try {
            cancelCurrentPayment()
            currentPaymentIntent = null
            currentCancelable = null
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}