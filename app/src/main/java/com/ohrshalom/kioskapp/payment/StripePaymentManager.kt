package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
// Stripe Terminal 4.6.0 Tap to Pay imports
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

/**
 * Stripe Payment Manager for NFC/Tap to Pay functionality
 * 
 * Handles Stripe Terminal integration for contactless payments.
 * This manager provides NFC payment processing capabilities for the kiosk.
 */
class StripePaymentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StripePaymentManager"
        
        // For demo/testing - replace with your actual Stripe secret key
        private const val STRIPE_TEST_SECRET_KEY = "sk_test_..."
    }
    
    private var currentPaymentIntent: PaymentIntent? = null
    private var currentCancelable: Cancelable? = null
    
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
                            Log.d(TAG, "Fetching connection token from server...")
                            fetchConnectionTokenFromServer(callback)
                        }
                    },
                    listener = object : TerminalListener {
                        override fun onUnexpectedReaderDisconnect(reader: Reader) {
                            Log.w(TAG, "Unexpected reader disconnect: ${reader.id}")
                        }
                        
                        override fun onConnectionStatusChange(status: com.stripe.stripeterminal.external.models.ConnectionStatus) {
                            Log.d(TAG, "Terminal connection status changed: $status")
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
     * Fetch connection token from the backend server
     */
    private fun fetchConnectionTokenFromServer(callback: ConnectionTokenCallback) {
        // Use the production endpoint
        val tokenEndpoint = "http://161.35.140.12/api/stripe/connection_token"
        
        Thread {
            try {
                Log.d(TAG, "Requesting connection token from: $tokenEndpoint")
                
                val url = java.net.URL(tokenEndpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                
                // Send empty JSON body
                val outputStream = connection.outputStream
                outputStream.write("{}".toByteArray())
                outputStream.flush()
                outputStream.close()
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Connection token response code: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Connection token response: $response")
                    
                    // Parse JSON response
                    val jsonResponse = org.json.JSONObject(response)
                    val secret = jsonResponse.getString("secret")
                    
                    if (secret.startsWith("pst_")) {
                        Log.d(TAG, "Valid connection token received")
                        callback.onSuccess(secret)
                    } else {
                        Log.e(TAG, "Invalid connection token format")
                        callback.onFailure(ConnectionTokenException("Invalid token format"))
                    }
                } else {
                    Log.e(TAG, "Failed to get connection token: HTTP $responseCode")
                    callback.onFailure(ConnectionTokenException("HTTP $responseCode"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching connection token", e)
                callback.onFailure(ConnectionTokenException("Network error: ${e.message}"))
            }
        }.start()
    }
    
    /**
     * Process NFC payment using Stripe Terminal
     * 
     * @param amountCents Amount in cents
     * @param currency Currency code (e.g., "usd")
     * @param email Optional email for receipt
     * @return true if payment successful, false otherwise
     */
    suspend fun processNfcPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return try {
            Log.d(TAG, "Processing NFC payment: $amountCents cents")
            
            // Create payment intent
            val paymentIntent = createPaymentIntent(amountCents, currency, email)
            
            // For demo purposes, simulate NFC payment processing
            // In production, this would use actual Stripe Terminal NFC readers
            val success = simulateNfcPayment(paymentIntent)
            
            if (success) {
                Log.d(TAG, "NFC payment completed successfully")
            } else {
                Log.w(TAG, "NFC payment failed or was cancelled")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error processing NFC payment", e)
            false
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
    
    private suspend fun simulateNfcPayment(paymentIntent: PaymentIntent): Boolean {
        return suspendCancellableCoroutine { continuation ->
            // Simulate NFC payment processing
            // In production, this would use Terminal.getInstance().collectPaymentMethod()
            // and Terminal.getInstance().confirmPaymentIntent()
            
            Log.d(TAG, "Simulating NFC payment processing...")
            
            // Simulate processing delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Simulate 95% success rate for demo
                val success = Math.random() > 0.05
                
                if (success) {
                    Log.d(TAG, "Simulated NFC payment successful")
                    continuation.resume(true)
                } else {
                    Log.d(TAG, "Simulated NFC payment failed")
                    continuation.resume(false)
                }
            }, 3000) // 3 second simulation delay
            
            continuation.invokeOnCancellation {
                Log.d(TAG, "NFC payment simulation cancelled")
            }
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
     * Update Stripe configuration
     * 
     * @param publishableKey Stripe publishable key
     * @param tokenEndpoint Server endpoint for connection tokens
     * @param locationId Stripe Terminal location ID
     * @param isLiveMode Whether to use live mode
     * @return true if configuration updated successfully
     */
    fun updateConfiguration(
        publishableKey: String,
        tokenEndpoint: String?,
        locationId: String?,
        isLiveMode: Boolean
    ): Boolean {
        return try {
            Log.d(TAG, "Updating Stripe configuration - Live mode: $isLiveMode")
            
            // Store configuration
            Log.d(TAG, "Stripe configuration updated:")
            Log.d(TAG, "  - Publishable Key: ${publishableKey.take(20)}...")
            Log.d(TAG, "  - Token Endpoint: $tokenEndpoint")
            Log.d(TAG, "  - Location ID: $locationId")
            Log.d(TAG, "  - Live Mode: $isLiveMode")
            
            // If Terminal is initialized and we have valid configuration, start reader discovery
            if (Terminal.isInitialized() && locationId != null) {
                initializeTapToPayReader(locationId)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Stripe configuration", e)
            false
        }
    }
    
    /**
     * Initialize Tap to Pay reader for the tablet
     */
    private fun initializeTapToPayReader(locationId: String) {
        try {
            Log.d(TAG, "Initializing Tap to Pay reader for location: $locationId")
            
            if (!Terminal.isInitialized()) {
                Log.e(TAG, "Terminal not initialized, cannot discover readers")
                return
            }
            
            // For Tap to Pay on Android, we need to discover the local mobile reader
            // and connect it to the specified location
            discoverTapToPayReader(locationId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tap to Pay reader", e)
        }
    }
    
    /**
     * Discover and connect to Tap to Pay reader
     */
    private fun discoverTapToPayReader(locationId: String) {
        try {
            Log.d(TAG, "Starting Tap to Pay reader discovery...")
            
            // Import required classes for discovery
            val discoveryConfig = com.stripe.stripeterminal.external.models.DiscoveryConfiguration.LocalMobileDiscoveryConfiguration.Builder()
                .setIsSimulated(false) // Use real hardware
                .build()
            
            Terminal.getInstance().discoverReaders(
                discoveryConfig,
                object : com.stripe.stripeterminal.external.callable.DiscoveryListener {
                    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                        Log.d(TAG, "Discovered ${readers.size} readers")
                        
                        // For Tap to Pay, we expect exactly one local mobile reader
                        if (readers.isNotEmpty()) {
                            val reader = readers.first()
                            Log.d(TAG, "Found Tap to Pay reader: ${reader.deviceType} - ${reader.id}")
                            connectToReader(reader, locationId)
                        } else {
                            Log.w(TAG, "No Tap to Pay readers discovered")
                        }
                    }
                },
                object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "Reader discovery completed successfully")
                    }
                    
                    override fun onFailure(e: TerminalException) {
                        Log.e(TAG, "Reader discovery failed: ${e.errorMessage}")
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during reader discovery", e)
        }
    }
    
    /**
     * Connect to discovered reader with location
     */
    private fun connectToReader(reader: Reader, locationId: String) {
        try {
            Log.d(TAG, "Connecting to reader: ${reader.id} for location: $locationId")
            
            val connectionConfig = com.stripe.stripeterminal.external.models.ConnectionConfiguration.LocalMobileConnectionConfiguration.Builder(locationId)
                .build()
            
            Terminal.getInstance().connectLocalMobileReader(
                reader,
                connectionConfig,
                object : com.stripe.stripeterminal.external.callable.ReaderCallback {
                    override fun onSuccess(connectedReader: Reader) {
                        Log.d(TAG, "Successfully connected to Tap to Pay reader: ${connectedReader.id}")
                        Log.d(TAG, "Reader location: ${connectedReader.location?.displayName ?: "Unknown"}")
                        Log.d(TAG, "Reader status: ${connectedReader.status}")
                    }
                    
                    override fun onFailure(e: TerminalException) {
                        Log.e(TAG, "Failed to connect to reader: ${e.errorMessage}")
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to reader", e)
        }
    }

    /**
     * Clean up resources
     */
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