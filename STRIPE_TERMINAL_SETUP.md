# Stripe Terminal Android Tablet Registration Guide

## 🎯 **The Problem**
Your Android tablet detected the NFC tap (audio feedback) but isn't registered as a Stripe Terminal reader. This means the payment wasn't processed through Stripe.

## 🔧 **Solution Steps**

### **Step 1: Update Android StripePaymentManager**

The current Android code uses simulation mode. We need to update it to use real Stripe Terminal integration.

Replace the contents of `app/src/main/java/com/ohrshalom/kioskapp/payment/StripePaymentManager.kt` with this updated version:

```kotlin
package com.ohrshalom.kioskapp.payment

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
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
                        override fun onUnexpectedReaderDisconnect(reader: Reader) {
                            Log.w(TAG, "Reader disconnected unexpectedly: ${reader.id}")
                            connectedReader = null
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
            Log.d(TAG, "Discovering local mobile readers...")
            
            val config = DiscoveryConfiguration.LocalMobileDiscoveryConfiguration.Builder()
                .setSimulated(false) // Set to false for real hardware
                .build()
            
            Terminal.getInstance().discoverReaders(config, object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    Log.d(TAG, "Discovered ${readers.size} readers")
                    
                    if (readers.isNotEmpty()) {
                        val reader = readers[0] // Use first available reader
                        connectToReader(reader)
                    } else {
                        Log.w(TAG, "No readers discovered - creating local mobile reader")
                        connectToLocalMobileReader()
                    }
                }
            }, object : Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Reader discovery completed")
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Reader discovery failed", exception)
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during reader discovery", e)
        }
    }
    
    private fun connectToReader(reader: Reader) {
        try {
            Log.d(TAG, "Connecting to reader: ${reader.id}")
            
            val locationId = this.locationId
            if (locationId.isNullOrBlank()) {
                Log.e(TAG, "Location ID not configured")
                return
            }
            
            val config = ConnectionConfiguration.LocalMobileConnectionConfiguration.Builder()
                .setLocationId(locationId)
                .build()
            
            Terminal.getInstance().connectLocalMobileReader(reader, config, object : ReaderCallback {
                override fun onSuccess(connectedReader: Reader) {
                    Log.d(TAG, "Successfully connected to reader: ${connectedReader.id}")
                    this@StripePaymentManager.connectedReader = connectedReader
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to connect to reader", exception)
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to reader", e)
        }
    }
    
    private fun connectToLocalMobileReader() {
        try {
            Log.d(TAG, "Connecting to local mobile reader (tablet)")
            
            val locationId = this.locationId
            if (locationId.isNullOrBlank()) {
                Log.e(TAG, "Location ID not configured for local mobile reader")
                return
            }
            
            val config = ConnectionConfiguration.LocalMobileConnectionConfiguration.Builder()
                .setLocationId(locationId)
                .build()
            
            Terminal.getInstance().connectLocalMobileReader(config, object : ReaderCallback {
                override fun onSuccess(reader: Reader) {
                    Log.d(TAG, "Successfully connected local mobile reader: ${reader.id}")
                    this@StripePaymentManager.connectedReader = reader
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to connect local mobile reader", exception)
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to local mobile reader", e)
        }
    }
    
    /**
     * Process real NFC payment using Stripe Terminal
     */
    suspend fun processNfcPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return try {
            Log.d(TAG, "Processing real NFC payment: $amountCents cents")
            
            // Check if reader is connected
            if (connectedReader == null) {
                Log.w(TAG, "No reader connected, attempting to connect...")
                discoverAndConnectReader()
                // Wait a moment for connection
                kotlinx.coroutines.delay(2000)
                
                if (connectedReader == null) {
                    Log.e(TAG, "Cannot process payment - no reader connected")
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
            val reader = connectedReader
            val hasEndpoint = !connectionTokenEndpoint.isNullOrBlank()
            val hasLocation = !locationId.isNullOrBlank()
            
            when {
                !isInitialized -> "Terminal not initialized"
                !hasEndpoint -> "Connection endpoint not configured"
                !hasLocation -> "Location ID not configured"
                reader == null -> "Reader not connected"
                else -> "Ready (Reader: ${reader.id})"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    fun isNfcAvailable(): Boolean {
        return try {
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            nfcAdapter != null && nfcAdapter.isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking NFC availability", e)
            false
        }
    }
    
    fun getPaymentStatus(): String {
        return when {
            currentPaymentIntent != null -> "processing"
            connectedReader != null -> "ready"
            Terminal.isInitialized() -> "initialized"
            else -> "not_initialized"
        }
    }
    
    fun getCurrentPaymentIntent(): PaymentIntent? = currentPaymentIntent
    
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
    
    fun cleanup() {
        try {
            cancelCurrentPayment()
            connectedReader = null
            currentPaymentIntent = null
            currentCancelable = null
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
```

### **Step 2: Update Android Dependencies**

Add this dependency to `app/build.gradle` (if not already present):

```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

### **Step 3: Update MainActivity Android Interface**

Add this method to the `AndroidJavaScriptInterface` class in `MainActivity.kt`:

```kotlin
@JavascriptInterface
fun getTerminalStatus(): String {
    return try {
        paymentManager.getTerminalStatus()
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

## 🧪 **Testing Steps**

### **Step 1: Update Android App Configuration**

1. **Build and install** the updated Android app
2. **Open admin interface** (tap logo 5 times, PIN: 12345)
3. **Configure Stripe settings:**
   - **Publishable Key:** Your live `pk_live_...` key
   - **Connection Token Endpoint:** `http://161.35.140.12/api/stripe/connection_token`
   - **Location ID:** Your Stripe Terminal location ID (`tml_...`)
   - **Environment:** Live Mode

### **Step 2: Test Terminal Registration**

1. **Click "Test Terminal"** in admin interface
2. **Should show:** "Ready (Reader: tml_...)" or connection status
3. **Check logs:** `adb logcat | grep StripePaymentManager`

### **Step 3: Test Real Payment**

1. **Select amount** (e.g., $5)
2. **Click "Tap to Pay"**
3. **Tap credit card** to tablet
4. **Check Stripe Dashboard** for actual transaction

## 🎯 **Expected Results**

After this update:
- ✅ **Android tablet will register** as a Stripe Terminal reader
- ✅ **Real payments will process** through Stripe
- ✅ **Transactions will appear** in your Stripe Dashboard
- ✅ **NFC taps will create** actual payment intents

## 🚨 **Important Notes**

1. **Location ID Required:** Make sure you have the correct `tml_...` location ID
2. **Live Mode:** Ensure your keys and location match (test vs live)
3. **NFC Permissions:** Android app needs NFC permissions enabled
4. **Network Access:** Tablet needs internet to reach your backend

Let me know if you need help getting the Location ID or implementing any of these updates!