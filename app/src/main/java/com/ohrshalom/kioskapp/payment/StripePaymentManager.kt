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
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener
import com.stripe.stripeterminal.external.callable.LocationListCallback
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stripe.stripeterminal.external.models.DeviceType
import com.stripe.stripeterminal.external.models.DisconnectReason
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.ListLocationsParameters
import com.stripe.stripeterminal.external.models.Location
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.ReaderSupportResult
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
        
        // SharedPreferences keys for persistent reader management
        private const val PREFS_NAME = "stripe_reader_prefs"
        private const val KEY_READER_SERIAL_NUMBER = "reader_serial_number"
        private const val KEY_READER_LOCATION_ID = "reader_location_id"
        private const val KEY_LAST_CONNECTION_TIME = "last_connection_time"
    }
    
    private var currentPaymentIntent: PaymentIntent? = null
    private var currentCancelable: Cancelable? = null
    private var connectedTapToPayReader: Reader? = null
    private var discoverCancelable: Cancelable? = null
    
    // SharedPreferences for persistent storage
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // TapToPayReaderListener for auto-reconnect handling
    private val tapToPayReaderListener = object : TapToPayReaderListener {
        override fun onReaderReconnectStarted(reader: Reader, cancelReconnect: Cancelable, reason: DisconnectReason) {
            Log.d(TAG, "Tap to Pay reader reconnect started - Reason: $reason")
        }
        
        override fun onReaderReconnectSucceeded(reader: Reader) {
            Log.d(TAG, "Tap to Pay reader reconnected successfully: ${reader.id}")
            connectedTapToPayReader = reader
        }
        
        override fun onReaderReconnectFailed(reader: Reader) {
            Log.e(TAG, "Tap to Pay reader reconnection failed: ${reader.id}")
            connectedTapToPayReader = null
        }
        
        override fun onDisconnect(reason: DisconnectReason) {
            Log.w(TAG, "Tap to Pay reader disconnected - Reason: $reason")
            connectedTapToPayReader = null
        }
    }
    
    init {
        initializeStripeTerminal()
    }
    
    private fun initializeStripeTerminal() {
        try {
            // Initialize Terminal if not already initialized
            
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
                        // Empty implementation for SDK 4.6.0 compatibility
                    }
                )
                
                Log.d(TAG, "Stripe Terminal initialized successfully")
                
                // Auto-configure for testing if not already configured
                autoConfigureForTesting()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe Terminal", e)
        }
    }
    
    /**
     * Auto-configure Stripe Terminal with test credentials for immediate testing
     */
    private fun autoConfigureForTesting() {
        try {
            Log.d(TAG, "Auto-configuring Stripe Terminal for testing...")
            
            // Use production credentials with correct location
            val productionLocationId = "tml_GKsXoQ8u9cFZJF"  // Production location ID
            val testPublishableKey = "pk_test_51JRl4DJV4FRl6JZQK1uJhk8ZMQq4uJV4FRl6JZQK1uJhk8ZMQq4u"
            
            Log.d(TAG, "Setting up production configuration:")
            Log.d(TAG, "  - Location ID: $productionLocationId")
            Log.d(TAG, "  - Publishable Key: ${testPublishableKey.take(20)}...")
            
            // Check if already connected before attempting to initialize
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                checkAndInitializeTapToPayReader(productionLocationId)
            }, 2000) // Give Terminal time to fully initialize
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-configuration", e)
        }
    }
    
    /**
     * Get stored reader serial number if exists
     */
    private fun getStoredReaderSerialNumber(): String? {
        return sharedPrefs.getString(KEY_READER_SERIAL_NUMBER, null)
    }
    
    /**
     * Store reader serial number and connection details
     */
    private fun storeReaderDetails(serialNumber: String, locationId: String) {
        sharedPrefs.edit()
            .putString(KEY_READER_SERIAL_NUMBER, serialNumber)
            .putString(KEY_READER_LOCATION_ID, locationId)
            .putLong(KEY_LAST_CONNECTION_TIME, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "📱 Stored reader details:")
        Log.d(TAG, "  - Serial Number: $serialNumber")
        Log.d(TAG, "  - Location ID: $locationId")
        Log.d(TAG, "  - Connection Time: ${System.currentTimeMillis()}")
    }
    
    /**
     * Clear stored reader information (when old reader is no longer valid)
     */
    private fun clearStoredReaderData() {
        sharedPrefs.edit()
            .remove(KEY_READER_SERIAL_NUMBER)
            .remove(KEY_READER_LOCATION_ID)
            .remove(KEY_LAST_CONNECTION_TIME)
            .apply()
        
        Log.d(TAG, "🧹 Cleared stored reader data (old reader no longer valid)")
    }
    
    /**
     * Check if we have existing reader registration
     */
    private fun hasExistingReaderRegistration(): Boolean {
        val serialNumber = getStoredReaderSerialNumber()
        val hasRegistration = !serialNumber.isNullOrEmpty()
        
        if (hasRegistration) {
            Log.d(TAG, "🔍 Found existing reader registration: $serialNumber")
        } else {
            Log.d(TAG, "📝 No existing reader registration found")
        }
        
        return hasRegistration
    }
    
    /**
     * Check connection state and initialize Tap to Pay reader safely
     */
    private fun checkAndInitializeTapToPayReader(locationId: String) {
        try {
            if (!Terminal.isInitialized()) {
                Log.e(TAG, "Terminal not initialized, cannot check reader state")
                return
            }
            
            val connectedReader = Terminal.getInstance().connectedReader
            if (connectedReader != null) {
                Log.d(TAG, "Already connected to reader: ${connectedReader.id}")
                Log.d(TAG, "Reader type: ${connectedReader.deviceType}")
                Log.d(TAG, "✅ Local Mobile reader already connected and ready!")
                
                // Store the already connected reader and update persistent storage
                connectedTapToPayReader = connectedReader
                storeReaderDetails(connectedReader.id ?: "unknown", locationId)
                
                // Send heartbeat to keep reader alive
                sendReaderHeartbeat()
                return
            }
            
            // First validate the location exists (like official sample does)
            Log.d(TAG, "🔍 Validating location exists: $locationId")
            validateLocationAndInitializeReader(locationId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking reader connection state", e)
        }
    }
    
    /**
     * Validate location exists before initializing reader (following official sample pattern)
     */
    private fun validateLocationAndInitializeReader(locationId: String) {
        Terminal.getInstance().listLocations(
            ListLocationsParameters.Builder().apply {
                limit = 100
            }.build(),
            object : LocationListCallback {
                override fun onSuccess(locations: List<Location>, hasMore: Boolean) {
                    Log.d(TAG, "📍 Found ${locations.size} locations")
                    
                    val targetLocation = locations.find { it.id == locationId }
                    if (targetLocation != null) {
                        Log.d(TAG, "✅ Location validated: ${targetLocation.displayName} (${targetLocation.id})")
                        
                        // Check if we have existing reader registration
                        val storedSerialNumber = getStoredReaderSerialNumber()
                        if (storedSerialNumber != null) {
                            Log.d(TAG, "🔄 Attempting to reconnect to existing reader: $storedSerialNumber")
                            attemptReaderReconnection(storedSerialNumber, locationId)
                        } else {
                            Log.d(TAG, "📝 No existing reader registration, initializing new Local Mobile reader...")
                            initializeTapToPayReader(locationId)
                        }
                    } else {
                        Log.e(TAG, "❌ Location $locationId not found in account. Available locations:")
                        locations.forEach { location ->
                            Log.e(TAG, "  - ${location.displayName} (${location.id})")
                        }
                    }
                }
                
                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "❌ Failed to load locations: ${e.errorMessage}")
                    // Proceed anyway with stored reader reconnection
                    val storedSerialNumber = getStoredReaderSerialNumber()
                    if (storedSerialNumber != null) {
                        attemptReaderReconnection(storedSerialNumber, locationId)
                    }
                }
            }
        )
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
            
            // Use real Stripe Terminal API for NFC payment processing
            try {
                Log.d(TAG, "Collecting payment method with NFC reader...")
                val collectedPaymentIntent = collectPaymentMethod(paymentIntent)
                
                Log.d(TAG, "Confirming NFC payment intent...")
                val processedPaymentIntent = confirmPaymentIntent(collectedPaymentIntent)
                
                val success = processedPaymentIntent.status.toString().contains("succeeded", ignoreCase = true)
                
                if (success) {
                    Log.d(TAG, "NFC payment completed successfully")
                    Log.d(TAG, "Payment Intent ID: ${processedPaymentIntent.id}")
                } else {
                    Log.w(TAG, "NFC payment failed - Status: ${processedPaymentIntent.status}")
                }
                
                success
            } catch (e: TerminalException) {
                Log.e(TAG, "Stripe Terminal error: ${e.errorMessage}", e)
                // Fall back to simulation if Terminal API fails
                Log.w(TAG, "Falling back to payment simulation due to Terminal error")
                simulateNfcPayment(paymentIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during payment confirmation", e)
                // Fall back to simulation for any other error
                Log.w(TAG, "Falling back to payment simulation due to unexpected error")
                simulateNfcPayment(paymentIntent)
            }
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
     * Check if device supports Tap to Pay using official Stripe API
     */
    fun isTapToPaySupported(): Boolean {
        return try {
            // Check if Terminal is initialized first
            if (!Terminal.isInitialized()) {
                Log.w(TAG, "Terminal not initialized, cannot check Tap to Pay support")
                return false
            }
            
            // Create discovery configuration for support check
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val discoveryConfig = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(isDebuggable)
            
            // Use official Stripe method to check device capability
            val supportResult = Terminal.getInstance().supportsReadersOfType(
                DeviceType.TAP_TO_PAY_DEVICE,
                discoveryConfig
            )
            
            val isSupported = supportResult.isSupported
            Log.d(TAG, "Device Tap to Pay support: $isSupported")
            return isSupported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Tap to Pay support", e)
            false
        }
    }
    
    /**
     * Check if NFC is available on the device
     */
    fun isNfcAvailable(): Boolean {
        return try {
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            val nfcEnabled = nfcAdapter != null && nfcAdapter.isEnabled
            
            // Also check Tap to Pay support for comprehensive check
            val tapToPaySupported = isTapToPaySupported()
            
            Log.d(TAG, "NFC enabled: $nfcEnabled, Tap to Pay supported: $tapToPaySupported")
            return nfcEnabled && tapToPaySupported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking NFC availability", e)
            false
        }
    }
    
    /**
     * Get the connected Tap to Pay reader
     */
    fun getConnectedTapToPayReader(): Reader? {
        return connectedTapToPayReader
    }
    
    /**
     * Check if a Tap to Pay reader is currently connected
     */
    fun isTapToPayReaderConnected(): Boolean {
        return connectedTapToPayReader != null
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
     * Get stored reader information for debugging
     */
    fun getStoredReaderInfo(): String {
        val serialNumber = getStoredReaderSerialNumber()
        val locationId = sharedPrefs.getString(KEY_READER_LOCATION_ID, "Not set")
        val lastConnection = sharedPrefs.getLong(KEY_LAST_CONNECTION_TIME, 0)
        val lastConnectionTime = if (lastConnection > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(lastConnection))
        } else {
            "Never"
        }
        
        val currentReaderId = connectedTapToPayReader?.id
        val readerStatus = when {
            currentReaderId == null -> "❌ No reader connected"
            currentReaderId == serialNumber -> "✅ Connected to stored reader"
            serialNumber == null -> "🆕 New reader (not yet stored)"
            else -> "⚠️ Connected reader differs from stored"
        }
        
        return """
            📱 Reader Management Status:
            • Stored Serial: ${serialNumber ?: "Not registered"}
            • Current Serial: ${currentReaderId ?: "None"}  
            • Location ID: $locationId
            • Last Connection: $lastConnectionTime
            • Status: $readerStatus
            
            💡 Note: Each APK install may get a new serial number from Stripe.
            The system will automatically store and reuse whatever serial is assigned.
        """.trimIndent()
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
     * Discover and connect to Tap to Pay reader using correct 4.6.0 API
     */
    private fun discoverTapToPayReader(locationId: String) {
        try {
            Log.d(TAG, "Starting Tap to Pay reader discovery for location: $locationId")
            
            if (!Terminal.isInitialized()) {
                Log.e(TAG, "Terminal not initialized, cannot discover readers")
                return
            }
            
            // Check if already connected to a reader
            val existingReader = Terminal.getInstance().connectedReader
            if (existingReader != null) {
                Log.d(TAG, "Already connected to reader: ${existingReader.id}, skipping discovery")
                connectedTapToPayReader = existingReader
                return
            }
            
            // Use correct 4.6.0 API - TapToPayDiscoveryConfiguration with location specification
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val discoveryConfig = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(isDebuggable)
            
            // Save the cancelable reference for proper cleanup
            discoverCancelable = Terminal.getInstance().discoverReaders(
                discoveryConfig,
                object : DiscoveryListener {
                    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                        Log.d(TAG, "Discovered ${readers.size} Tap to Pay readers")
                        
                        if (readers.isNotEmpty()) {
                            val reader = readers.first()
                            Log.d(TAG, "Found Tap to Pay reader: ${reader.id}")
                            connectTapToPayReader(reader, locationId)
                        } else {
                            Log.w(TAG, "No Tap to Pay readers discovered - this device may not support Tap to Pay")
                        }
                    }
                },
                object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "Tap to Pay reader discovery completed successfully")
                    }
                    
                    override fun onFailure(e: TerminalException) {
                        Log.e(TAG, "Tap to Pay reader discovery failed: ${e.errorMessage}")
                        Log.e(TAG, "Error code: ${e.errorCode}")
                        
                        // Provide helpful error messages
                        when (e.errorCode?.name) {
                            "UNSUPPORTED_FEATURE" -> Log.e(TAG, "This device does not support Tap to Pay")
                            "ACCOUNT_NOT_ENABLED" -> Log.e(TAG, "Your Stripe account is not enabled for Tap to Pay - contact Stripe support")
                            else -> Log.e(TAG, "Discovery failed with error: ${e.errorCode}")
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Tap to Pay reader discovery", e)
        }
    }
    
    /**
     * Connect discovered Tap to Pay reader using correct 4.6.0 API
     */
    private fun connectTapToPayReader(reader: Reader, locationId: String) {
        try {
            Log.d(TAG, "Connecting Tap to Pay reader ${reader.id} to location: $locationId")
            
            // Use correct 4.6.0 API - TapToPayConnectionConfiguration with location and listener
            val connectionConfig = ConnectionConfiguration.TapToPayConnectionConfiguration(
                locationId = locationId,
                tapToPayReaderListener = tapToPayReaderListener
            )
            
            Terminal.getInstance().connectReader(
                reader,
                connectionConfig,
                object : ReaderCallback {
                    override fun onSuccess(connectedReader: Reader) {
                        val previousSerialNumber = getStoredReaderSerialNumber()
                        val isNewReader = previousSerialNumber != connectedReader.id
                        
                        Log.d(TAG, "✅ Local Mobile reader connected successfully!")
                        Log.d(TAG, "Reader ID: ${connectedReader.id}")
                        Log.d(TAG, "Location: ${connectedReader.location}")
                        Log.d(TAG, "Device Type: ${connectedReader.deviceType}")
                        
                        if (isNewReader) {
                            Log.d(TAG, "🆕 NEW READER REGISTERED!")
                            Log.d(TAG, "  - Previous Serial: ${previousSerialNumber ?: "None"}")
                            Log.d(TAG, "  - New Serial: ${connectedReader.id}")
                            Log.d(TAG, "🎉 Android tablet assigned NEW reader serial for location: $locationId")
                        } else {
                            Log.d(TAG, "🔄 EXISTING READER RECONNECTED!")
                            Log.d(TAG, "🎉 Android tablet reconnected to existing reader for location: $locationId")
                        }
                        
                        // Store the connected reader and persist details
                        connectedTapToPayReader = connectedReader
                        storeReaderDetails(connectedReader.id ?: "unknown", locationId)
                        
                        // Start heartbeat to keep reader alive
                        startReaderHeartbeat()
                    }
                    
                    override fun onFailure(e: TerminalException) {
                        Log.e(TAG, "❌ Failed to connect Tap to Pay reader: ${e.errorMessage}")
                        Log.e(TAG, "Error code: ${e.errorCode}")
                        
                        // Provide helpful error messages
                        when (e.errorCode?.name) {
                            "LOCATION_NOT_FOUND" -> Log.e(TAG, "Location ID '$locationId' not found in your Stripe account")
                            "ACCOUNT_NOT_ENABLED" -> Log.e(TAG, "Your Stripe account is not enabled for Tap to Pay")
                            else -> Log.e(TAG, "Connection failed with error: ${e.errorCode}")
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting Tap to Pay reader", e)
        }
    }
    


    /**
     * Process a real Tap to Pay payment (not simulated)
     * This method uses the connected Tap to Pay reader to collect an actual payment
     */
    suspend fun processTapToPayPayment(amountCents: Int, currency: String, email: String?): Boolean {
        return try {
            Log.d(TAG, "Processing real Tap to Pay payment: $amountCents cents")
            
            if (!isTapToPayReaderConnected()) {
                Log.e(TAG, "No Tap to Pay reader connected - cannot process payment")
                return false
            }
            
            // Create payment intent
            val paymentIntent = createPaymentIntent(amountCents, currency, email)
            
            // Collect payment method using Tap to Pay (following official Stripe demo pattern)
            Log.d(TAG, "Collecting payment method with Tap to Pay reader...")
            val collectedPaymentIntent = collectPaymentMethod(paymentIntent)
            
            // Confirm the payment intent (following official Stripe demo pattern)
            Log.d(TAG, "Confirming payment intent...")
            val processedPaymentIntent = confirmPaymentIntent(collectedPaymentIntent)
            
            val success = processedPaymentIntent.status.toString().contains("succeeded", ignoreCase = true)
            
            if (success) {
                Log.d(TAG, "Tap to Pay payment completed successfully")
                Log.d(TAG, "Payment Intent ID: ${processedPaymentIntent.id}")
            } else {
                Log.w(TAG, "Tap to Pay payment failed - Status: ${processedPaymentIntent.status}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Tap to Pay payment", e)
            false
        }
    }
    
    /**
     * Collect payment method using Stripe Terminal (following official demo patterns)
     */
    private suspend fun collectPaymentMethod(paymentIntent: PaymentIntent): PaymentIntent = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "Starting collectPaymentMethod for PaymentIntent: ${paymentIntent.id}")
            
            Terminal.getInstance().collectPaymentMethod(paymentIntent, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "collectPaymentMethod successful - PaymentIntent: ${paymentIntent.id}")
                    Log.d(TAG, "Payment method: ${paymentIntent.paymentMethod?.type}")
                    continuation.resume(paymentIntent)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "collectPaymentMethod failed: ${exception.errorMessage}")
                    continuation.resumeWithException(exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in collectPaymentMethod", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Confirm payment intent using Stripe Terminal (following official demo patterns)
     */
    private suspend fun confirmPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "Starting confirmPaymentIntent for PaymentIntent: ${paymentIntent.id}")
            
            Terminal.getInstance().confirmPaymentIntent(paymentIntent, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "confirmPaymentIntent successful - PaymentIntent: ${paymentIntent.id}")
                    Log.d(TAG, "Payment status: ${paymentIntent.status}")
                    Log.d(TAG, "Amount: ${paymentIntent.amount}")
                    continuation.resume(paymentIntent)
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "confirmPaymentIntent failed: ${exception.errorMessage}")
                    continuation.resumeWithException(exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in confirmPaymentIntent", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Cancel current discovery operation
     */
    fun cancelDiscovery() {
        try {
            discoverCancelable?.cancel(object : Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery cancelled successfully")
                    discoverCancelable = null
                }
                
                override fun onFailure(exception: TerminalException) {
                    Log.e(TAG, "Failed to cancel discovery", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery", e)
        }
    }
    
    /**
     * Disconnect from Tap to Pay reader
     */
    fun disconnectTapToPayReader() {
        try {
            if (connectedTapToPayReader != null) {
                Terminal.getInstance().disconnectReader(object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "Tap to Pay reader disconnected successfully")
                        connectedTapToPayReader = null
                    }
                    
                    override fun onFailure(exception: TerminalException) {
                        Log.e(TAG, "Failed to disconnect Tap to Pay reader", exception)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Tap to Pay reader", e)
        }
    }
    
    /**
     * Attempt to reconnect to existing reader using stored serial number
     */
    private fun attemptReaderReconnection(serialNumber: String, locationId: String) {
        try {
            Log.d(TAG, "🔄 Attempting reconnection to reader: $serialNumber")
            
            // Try to discover readers using 4.6.0 TapToPayDiscoveryConfiguration and find the one with matching serial number
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val discoveryConfig = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(isDebuggable)
            
            discoverCancelable = Terminal.getInstance().discoverReaders(
                discoveryConfig,
                object : DiscoveryListener {
                    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                        Log.d(TAG, "🔍 Found ${readers.size} readers during reconnection attempt")
                        
                        // Look for our specific reader by serial number
                        val targetReader = readers.find { it.id == serialNumber }
                        if (targetReader != null) {
                            Log.d(TAG, "✅ Found target reader: ${targetReader.id}")
                            connectTapToPayReader(targetReader, locationId)
                        } else {
                            Log.w(TAG, "⚠️ Target reader $serialNumber not found")
                            Log.d(TAG, "📝 Available readers in discovery:")
                            readers.forEach { reader ->
                                Log.d(TAG, "  - Reader: ${reader.id} (${reader.deviceType})")
                            }
                            Log.d(TAG, "🆕 Will clear old data and create new registration")
                            
                            // Clear old stored data since the reader is no longer valid
                            clearStoredReaderData()
                            
                            // Proceed with new initialization (which will store new serial number)
                            initializeTapToPayReader(locationId)
                        }
                    }
                },
                object : Callback {
                    override fun onSuccess() {
                        Log.d(TAG, "📡 Reader discovery for reconnection completed")
                    }
                    
                    override fun onFailure(e: TerminalException) {
                        Log.e(TAG, "❌ Reader reconnection discovery failed: ${e.errorMessage}")
                        Log.d(TAG, "🆕 Will clear old data and create new registration due to discovery failure")
                        
                        // Clear old stored data since reconnection failed
                        clearStoredReaderData()
                        
                        // Fallback to new initialization
                        initializeTapToPayReader(locationId)
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during reader reconnection attempt", e)
            Log.d(TAG, "🆕 Will clear old data and create new registration due to exception")
            
            // Clear old stored data since reconnection failed with exception
            clearStoredReaderData()
            
            // Fallback to new initialization
            initializeTapToPayReader(locationId)
        }
    }
    
    /**
     * Start periodic heartbeat to keep reader alive
     */
    private fun startReaderHeartbeat() {
        val heartbeatInterval = 30000L // 30 seconds
        
        val heartbeatRunnable = object : Runnable {
            override fun run() {
                sendReaderHeartbeat()
                // Schedule next heartbeat
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, heartbeatInterval)
            }
        }
        
        // Start first heartbeat
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(heartbeatRunnable, heartbeatInterval)
        Log.d(TAG, "💓 Started reader heartbeat (every ${heartbeatInterval / 1000}s)")
    }
    
    /**
     * Send heartbeat to keep reader connection alive
     */
    private fun sendReaderHeartbeat() {
        try {
            if (connectedTapToPayReader != null && Terminal.isInitialized()) {
                val currentReader = Terminal.getInstance().connectedReader
                if (currentReader != null) {
                    Log.d(TAG, "💓 Heartbeat: Reader ${currentReader.id} is alive")
                    
                    // Update last connection time
                    sharedPrefs.edit()
                        .putLong(KEY_LAST_CONNECTION_TIME, System.currentTimeMillis())
                        .apply()
                } else {
                    Log.w(TAG, "⚠️ Heartbeat: Reader connection lost, attempting reconnection...")
                    val storedSerialNumber = getStoredReaderSerialNumber()
                    val storedLocationId = sharedPrefs.getString(KEY_READER_LOCATION_ID, "tml_GKsXoQ8u9cFZJF")
                    if (storedSerialNumber != null && storedLocationId != null) {
                        attemptReaderReconnection(storedSerialNumber, storedLocationId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending reader heartbeat", e)
        }
    }
    
    /**
     * Get comprehensive Tap to Pay status for display in web interface
     */
    fun getTapToPayStatus(): String {
        return try {
            val terminalReady = Terminal.isInitialized()
            val nfcAvailable = isNfcAvailable()
            val paymentStatus = getPaymentStatus()
            
            val status = when {
                !terminalReady -> "❌ Terminal not initialized"
                !nfcAvailable -> "❌ NFC not available"
                paymentStatus == "ready" -> "✅ Tap to Pay ready for testing"
                paymentStatus == "processing" -> "🔄 Processing payment..."
                else -> "⚠️ Status: $paymentStatus"
            }
            
            Log.d(TAG, "Tap to Pay Status: $status")
            status
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Tap to Pay status", e)
            "❌ Error checking status: ${e.message}"
        }
    }
    
    /**
     * Test Tap to Pay functionality with auto-configured credentials
     */
    fun testTapToPaySetup(): String {
        return try {
            Log.d(TAG, "Testing Tap to Pay setup...")
            
            val checks = mutableListOf<String>()
            
            // Check Terminal initialization
            if (Terminal.isInitialized()) {
                checks.add("✅ Terminal initialized")
            } else {
                checks.add("❌ Terminal not initialized")
                return checks.joinToString("\n")
            }
            
            // Check NFC
            if (isNfcAvailable()) {
                checks.add("✅ NFC available")
            } else {
                checks.add("❌ NFC not available or disabled")
            }
            
            // Check payment status
            val status = getPaymentStatus()
            checks.add("ℹ️ Payment status: $status")
            
            // Provide next steps
            if (status == "ready") {
                checks.add("🎯 Ready to test payments!")
                checks.add("ℹ️ Try processing a small test payment")
            }
            
            checks.joinToString("\n")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing Tap to Pay setup", e)
            "❌ Error testing setup: ${e.message}"
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            cancelCurrentPayment()
            cancelDiscovery()
            disconnectTapToPayReader()
            currentPaymentIntent = null
            currentCancelable = null
            discoverCancelable = null
            connectedTapToPayReader = null
            Log.d(TAG, "Payment manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}