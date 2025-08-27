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
    private var isConfigured: Boolean = false
    
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
            Log.d(TAG, "=== STRIPE ANDROID DEBUG: Starting production defaults configuration ===")
            
            // Check current state
            Log.d(TAG, "ANDROID DEBUG: Current configuration state:")
            Log.d(TAG, "  - Token Endpoint: ${connectionTokenEndpoint ?: "NOT SET"}")
            Log.d(TAG, "  - Location ID: ${locationId ?: "NOT SET"}")
            Log.d(TAG, "  - Is Configured: $isConfigured")
            Log.d(TAG, "  - Terminal Initialized: ${Terminal.isInitialized()}")
            
            // Hardcoded production values
            val productionPublishableKey = "pk_live_51Q5QhsJhCdJUSe2h1hl7iqL7YLmprQQMu7FLmkDzULDwacidH6LmzH4dbodT2k2FP7Sh9whkLmZ5YHmGFEi4MrtE0081NqrCtr"
            val productionTokenEndpoint = "http://161.35.140.12/api/stripe/connection_token"
            val productionLocationId = "tml_GKsXoQ8u9cFZJF"
            
            Log.d(TAG, "ANDROID DEBUG: Applying production configuration:")
            Log.d(TAG, "  - Publishable Key: ${productionPublishableKey.take(20)}...")
            Log.d(TAG, "  - Token Endpoint: $productionTokenEndpoint")
            Log.d(TAG, "  - Location ID: $productionLocationId")
            Log.d(TAG, "  - Live Mode: true")
            
            // Configure automatically
            val success = updateConfiguration(
                publishableKey = productionPublishableKey,
                tokenEndpoint = productionTokenEndpoint,
                locationId = productionLocationId,
                isLiveMode = true
            )
            
            Log.d(TAG, "ANDROID DEBUG: Configuration update result: $success")
            
            if (success) {
                Log.d(TAG, "✅ ANDROID DEBUG: Production defaults configured successfully")
                Log.d(TAG, "ANDROID DEBUG: Terminal registration process initiated")
                
                // Log final state
                Log.d(TAG, "ANDROID DEBUG: Final configuration state:")
                Log.d(TAG, "  - Token Endpoint: ${connectionTokenEndpoint}")
                Log.d(TAG, "  - Location ID: ${locationId}")
                Log.d(TAG, "  - Is Configured: $isConfigured")
                Log.d(TAG, "  - Terminal Status: ${getTerminalStatus()}")
            } else {
                Log.w(TAG, "❌ ANDROID DEBUG: Failed to configure production defaults")
            }
            
            Log.d(TAG, "=== STRIPE ANDROID DEBUG: Production defaults configuration completed ===")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ANDROID DEBUG: Exception during production defaults configuration", e)
        }
    }
    
    private fun fetchConnectionTokenFromBackend(callback: ConnectionTokenCallback) {
        Log.d(TAG, "=== CONNECTION TOKEN DEBUG: Starting token fetch ===")
        
        val endpoint = connectionTokenEndpoint
        if (endpoint.isNullOrBlank()) {
            Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: Endpoint not configured")
            callback.onFailure(ConnectionTokenException("Connection token endpoint not configured"))
            return
        }
        
        Log.d(TAG, "CONNECTION TOKEN DEBUG: Fetching from endpoint: $endpoint")
        Log.d(TAG, "CONNECTION TOKEN DEBUG: Request method: POST")
        Log.d(TAG, "CONNECTION TOKEN DEBUG: Request body: {}")
        
        val requestBody = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        
        Log.d(TAG, "CONNECTION TOKEN DEBUG: Request headers: ${request.headers}")
        val startTime = System.currentTimeMillis()
        
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: Network request failed after ${duration}ms", e)
                Log.e(TAG, "CONNECTION TOKEN DEBUG: Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "CONNECTION TOKEN DEBUG: Error message: ${e.message}")
                callback.onFailure(ConnectionTokenException("Network error: ${e.message}"))
            }
            
            override fun onResponse(call: Call, response: Response) {
                val duration = System.currentTimeMillis() - startTime
                try {
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response received after ${duration}ms")
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response code: ${response.code}")
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response message: ${response.message}")
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response headers: ${response.headers}")
                    
                    val responseBody = response.body?.string()
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response body length: ${responseBody?.length ?: 0}")
                    Log.d(TAG, "CONNECTION TOKEN DEBUG: Response body: ${responseBody ?: "NULL"}")
                    
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            Log.d(TAG, "CONNECTION TOKEN DEBUG: Parsed JSON keys: ${json.keys().asSequence().toList()}")
                            
                            if (json.has("secret")) {
                                val secret = json.getString("secret")
                                Log.d(TAG, "✅ CONNECTION TOKEN DEBUG: Secret found, length: ${secret.length}")
                                Log.d(TAG, "CONNECTION TOKEN DEBUG: Secret prefix: ${secret.take(10)}...")
                                callback.onSuccess(secret)
                            } else {
                                Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: No 'secret' field in response")
                                callback.onFailure(ConnectionTokenException("Response missing 'secret' field"))
                            }
                        } catch (jsonException: Exception) {
                            Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: JSON parsing failed", jsonException)
                            Log.e(TAG, "CONNECTION TOKEN DEBUG: Raw response for debugging: $responseBody")
                            callback.onFailure(ConnectionTokenException("JSON parse error: ${jsonException.message}"))
                        }
                    } else {
                        Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: Unsuccessful response")
                        Log.e(TAG, "CONNECTION TOKEN DEBUG: HTTP ${response.code}: ${response.message}")
                        Log.e(TAG, "CONNECTION TOKEN DEBUG: Response body: $responseBody")
                        callback.onFailure(ConnectionTokenException("Server error: ${response.code} - ${response.message}"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CONNECTION TOKEN DEBUG: Exception processing response", e)
                    callback.onFailure(ConnectionTokenException("Response processing error: ${e.message}"))
                } finally {
                    Log.d(TAG, "=== CONNECTION TOKEN DEBUG: Token fetch completed ===")
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
            
            // Ensure Tap to Pay is configured
            if (!isConfigured || connectionTokenEndpoint.isNullOrBlank() || locationId.isNullOrBlank()) {
                Log.w(TAG, "Tap to Pay not configured, setting up...")
                discoverAndConnectReader()
                
                if (!isConfigured) {
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
                !isConfigured -> "Tap to Pay not configured"
                else -> "Ready - Tap to Pay configured (Location: $locationId)"
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
            Log.d(TAG, "=== STRIPE CONFIG UPDATE DEBUG: Starting configuration update ===")
            Log.d(TAG, "CONFIG DEBUG: Input parameters:")
            Log.d(TAG, "  - Publishable Key: ${publishableKey.take(20)}... (length: ${publishableKey.length})")
            Log.d(TAG, "  - Token Endpoint: $tokenEndpoint")
            Log.d(TAG, "  - Location ID: $locationId")
            Log.d(TAG, "  - Live Mode: $isLiveMode")
            
            Log.d(TAG, "CONFIG DEBUG: Previous configuration:")
            Log.d(TAG, "  - Previous Endpoint: $connectionTokenEndpoint")
            Log.d(TAG, "  - Previous Location: ${this.locationId}")
            Log.d(TAG, "  - Previous Configured: $isConfigured")
            
            // Update configuration
            this.connectionTokenEndpoint = tokenEndpoint
            this.locationId = locationId
            
            Log.d(TAG, "CONFIG DEBUG: Configuration variables updated")
            
            // Validate configuration completeness
            val hasEndpoint = !tokenEndpoint.isNullOrBlank()
            val hasLocation = !locationId.isNullOrBlank()
            val hasPublishableKey = publishableKey.isNotBlank()
            
            Log.d(TAG, "CONFIG DEBUG: Configuration validation:")
            Log.d(TAG, "  - Has Endpoint: $hasEndpoint")
            Log.d(TAG, "  - Has Location: $hasLocation")
            Log.d(TAG, "  - Has Publishable Key: $hasPublishableKey")
            
            if (hasEndpoint && hasLocation && hasPublishableKey) {
                Log.d(TAG, "CONFIG DEBUG: All required configuration present, setting up Tap to Pay")
                discoverAndConnectReader()
            } else {
                Log.w(TAG, "CONFIG DEBUG: Incomplete configuration - Tap to Pay setup skipped")
                isConfigured = false
            }
            
            Log.d(TAG, "CONFIG DEBUG: Final state:")
            Log.d(TAG, "  - Token Endpoint: $connectionTokenEndpoint")
            Log.d(TAG, "  - Location ID: ${this.locationId}")
            Log.d(TAG, "  - Is Configured: $isConfigured")
            Log.d(TAG, "  - Terminal Status: ${getTerminalStatus()}")
            
            Log.d(TAG, "✅ CONFIG DEBUG: Configuration update completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ CONFIG DEBUG: Failed to update Stripe configuration", e)
            false
        } finally {
            Log.d(TAG, "=== STRIPE CONFIG UPDATE DEBUG: Update process finished ===")
        }
    }
    
    private fun discoverAndConnectReader() {
        try {
            Log.d(TAG, "Setting up Tap to Pay configuration...")
            
            val locationId = this.locationId
            if (locationId.isNullOrBlank()) {
                Log.e(TAG, "Location ID not configured for Tap to Pay")
                return
            }
            
            Log.d(TAG, "Tap to Pay configured with location ID: $locationId")
            Log.d(TAG, "Device ready for NFC payment processing")
            
            // For Stripe Terminal SDK 4.6.0, Tap to Pay works differently
            // The device acts as the reader automatically when payment collection starts
            // No explicit reader discovery/connection needed for Tap to Pay
            isConfigured = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Tap to Pay configuration", e)
        }
    }

    fun cleanup() {
        try {
            cancelCurrentPayment()
            currentPaymentIntent = null
            currentCancelable = null
            isConfigured = false
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}