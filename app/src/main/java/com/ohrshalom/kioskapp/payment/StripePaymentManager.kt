package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*

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
    
    init {
        initializeStripeTerminal()
    }
    
    private fun initializeStripeTerminal() {
        try {
            if (!Terminal.isInitialized()) {
                Terminal.initTerminal(
                    context.applicationContext,
                    tokenProvider = { callback ->
                        // For development/testing - in production, implement server endpoint
                        Log.w(TAG, "Connection token provider called - implement server-side token generation")
                        callback.onFailure(ConnectionTokenException("Token provider not implemented - needs server endpoint"))
                    },
                    listener = object : TerminalListener {
                        override fun onUnexpectedReaderDisconnect(reader: Reader) {
                            Log.w(TAG, "Reader unexpectedly disconnected: ${reader.serialNumber}")
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