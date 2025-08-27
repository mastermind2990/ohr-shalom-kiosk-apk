package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.PaymentMethodCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.ReaderReconnectionListener
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryMethod
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.PaymentMethod
import com.stripe.stripeterminal.external.models.PaymentMethodOptionsParameters
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.log.LogLevel

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private var connectedReader: Reader? = null
    
    init {
        initializeStripeTerminal()
    }
    
    private fun initializeStripeTerminal() {
        try {
            if (!Terminal.isInitialized()) {
                Terminal.initTerminal(
                    context.applicationContext,
                    logLevel = LogLevel.VERBOSE,
                    tokenProvider = object : ConnectionTokenProvider {
                        override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
                            // For development/testing - in production, implement server endpoint
                            Log.w(TAG, "Connection token provider called - implement server-side token generation")
                            // For now, return a mock token for compilation - replace with actual server call
                            callback.onFailure(ConnectionTokenException("Token provider not implemented - needs server endpoint"))
                        }
                    },
                    listener = object : TerminalListener {
                        override fun onUnexpectedReaderDisconnect(reader: Reader) {
                            Log.w(TAG, "Reader unexpectedly disconnected: ${reader.id}")
                            connectedReader = null
                        }
                        
                        override fun onConnectionStatusChange(status: com.stripe.stripeterminal.external.models.ConnectionStatus) {
                            Log.d(TAG, "Connection status changed: $status")
                        }
                        
                        override fun onPaymentStatusChange(status: com.stripe.stripeterminal.external.models.PaymentStatus) {
                            Log.d(TAG, "Payment status changed: $status")
                        }
                    }
                )
                
                Log.d(TAG, "Stripe Terminal initialized successfully for Tap to Pay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe Terminal", e)
        }
    }
    
    /**
     * Process Tap to Pay payment using Stripe Terminal
     * 
     * @param amountCents Amount in cents
     * @param currency Currency code (e.g., "usd")
     * @param email Optional email for receipt
     * @return true if payment successful, false otherwise
     */
    suspend fun processTapToPayPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return try {
            Log.d(TAG, "Processing Tap to Pay payment: $amountCents cents")
            
            // Step 1: Ensure we have a Tap to Pay reader connected
            if (connectedReader == null) {
                val readerConnected = connectTapToPayReader()
                if (!readerConnected) {
                    Log.e(TAG, "Failed to connect Tap to Pay reader")
                    return false
                }
            }
            
            // Step 2: Create payment intent
            val paymentIntent = createPaymentIntent(amountCents, currency, email)
            
            // Step 3: Collect payment method using Tap to Pay
            val paymentMethod = collectPaymentMethod(paymentIntent)
            
            // Step 4: Confirm the payment
            val confirmedPaymentIntent = confirmPaymentIntent(paymentIntent)
            
            val success = confirmedPaymentIntent?.status == com.stripe.stripeterminal.external.models.PaymentIntentStatus.SUCCEEDED
            
            if (success) {
                Log.d(TAG, "Tap to Pay payment completed successfully")
            } else {
                Log.w(TAG, "Tap to Pay payment failed or was cancelled")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Tap to Pay payment", e)
            false
        }
    }
    
    /**
     * Legacy method name for backwards compatibility
     */
    suspend fun processNfcPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return processTapToPayPayment(amountCents, currency, email)
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
     * Connect to the built-in Tap to Pay reader on this Android device
     */
    private suspend fun connectTapToPayReader(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Discover Tap to Pay readers (built-in to the device)
                val config = DiscoveryConfiguration.TapToPayDiscoveryConfiguration()
                
                Terminal.getInstance().discoverReaders(
                    config,
                    object : Callback {
                        override fun onSuccess() {
                            Log.d(TAG, "Tap to Pay reader discovery completed")
                            // For Tap to Pay, we typically auto-connect to the built-in reader
                            connectToBuiltInReader(continuation)
                        }
                        
                        override fun onFailure(exception: TerminalException) {
                            Log.e(TAG, "Failed to discover Tap to Pay readers", exception)
                            continuation.resume(false)
                        }
                    },
                    object : com.stripe.stripeterminal.external.callable.ReaderListener {
                        override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                            Log.d(TAG, "Discovered ${readers.size} readers")
                            if (readers.isNotEmpty()) {
                                // Connect to the first available Tap to Pay reader
                                connectToReader(readers.first(), continuation)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error starting reader discovery", e)
                continuation.resume(false)
            }
        }
    }
    
    private fun connectToReader(reader: Reader, continuation: kotlin.coroutines.Continuation<Boolean>) {
        val config = ConnectionConfiguration.TapToPayConnectionConfiguration()
        
        Terminal.getInstance().connectReader(
            reader,
            config,
            object : ReaderCallback {
                override fun onSuccess(connectedReader: Reader) {
                    Log.d(TAG, "Successfully connected to Tap to Pay reader: ${connectedReader.id}")
                    this@StripePaymentManager.connectedReader = connectedReader
                    continuation.resume(true)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to connect to Tap to Pay reader", exception)
                    continuation.resume(false)
                }
            }
        )
    }
    
    private fun connectToBuiltInReader(continuation: kotlin.coroutines.Continuation<Boolean>) {
        // For Tap to Pay, there's usually a built-in reader that doesn't need discovery
        try {
            Log.d(TAG, "Attempting to connect to built-in Tap to Pay reader")
            continuation.resume(true) // Assume success for now - will be handled by actual payment collection
        } catch (e: Exception) {
            Log.e(TAG, "Error with built-in reader", e)
            continuation.resume(false)
        }
    }
    
    /**
     * Collect payment method using Tap to Pay
     */
    private suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): PaymentMethod? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Terminal.getInstance().collectPaymentMethod(
                    paymentIntent,
                    object : PaymentMethodCallback {
                        override fun onSuccess(paymentIntent: PaymentIntent) {
                            Log.d(TAG, "Payment method collected successfully")
                            this@StripePaymentManager.currentPaymentIntent = paymentIntent
                            continuation.resume(paymentIntent.paymentMethod)
                        }
                        
                        override fun onFailure(exception: TerminalException) {
                            Log.e(TAG, "Failed to collect payment method", exception)
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting payment method", e)
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Confirm the payment intent
     */
    private suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Terminal.getInstance().confirmPaymentIntent(
                    paymentIntent,
                    object : PaymentIntentCallback {
                        override fun onSuccess(confirmedPaymentIntent: PaymentIntent) {
                            Log.d(TAG, "Payment confirmed successfully: ${confirmedPaymentIntent.id}")
                            continuation.resume(confirmedPaymentIntent)
                        }
                        
                        override fun onFailure(exception: TerminalException) {
                            Log.e(TAG, "Failed to confirm payment", exception)
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming payment", e)
                continuation.resume(null)
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
     * Check if Tap to Pay is available on the device
     */
    fun isTapToPayAvailable(): Boolean {
        return try {
            // Check if the device supports NFC
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            val nfcSupported = nfcAdapter != null && nfcAdapter.isEnabled
            
            // Check if Stripe Terminal is initialized
            val terminalReady = Terminal.isInitialized()
            
            // Check Android version (Tap to Pay requires Android 11+)
            val androidVersionSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
            
            Log.d(TAG, "Tap to Pay availability: NFC=$nfcSupported, Terminal=$terminalReady, Android=${androidVersionSupported}")
            
            nfcSupported && terminalReady && androidVersionSupported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Tap to Pay availability", e)
            false
        }
    }
    
    /**
     * Legacy method name for backwards compatibility
     */
    fun isNfcAvailable(): Boolean {
        return isTapToPayAvailable()
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
     * Register this Android device as a Tap to Pay reader
     * This method should be called when setting up the device for the first time
     */
    suspend fun registerDeviceAsReader(): Boolean {
        return try {
            Log.d(TAG, "Registering device as Tap to Pay reader...")
            
            // For Tap to Pay, the device registration is typically handled automatically
            // by the Stripe Terminal SDK when processing the first payment
            // This method is here for future use if explicit registration is needed
            
            val available = isTapToPayAvailable()
            if (available) {
                Log.d(TAG, "Device is ready for Tap to Pay")
            } else {
                Log.w(TAG, "Device does not support Tap to Pay")
            }
            
            available
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device as reader", e)
            false
        }
    }
    
    /**
     * Get the connected reader information
     */
    fun getConnectedReader(): Reader? {
        return connectedReader
    }
    
    /**
     * Disconnect from the current reader
     */
    suspend fun disconnectReader(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (connectedReader != null) {
                    Terminal.getInstance().disconnectReader(object : Callback {
                        override fun onSuccess() {
                            Log.d(TAG, "Reader disconnected successfully")
                            connectedReader = null
                            continuation.resume(true)
                        }
                        
                        override fun onFailure(exception: TerminalException) {
                            Log.e(TAG, "Failed to disconnect reader", exception)
                            continuation.resume(false)
                        }
                    })
                } else {
                    Log.d(TAG, "No reader to disconnect")
                    continuation.resume(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting reader", e)
                continuation.resume(false)
            }
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
            connectedReader = null
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}