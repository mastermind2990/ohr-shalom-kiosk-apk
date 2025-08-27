package com.ohrshalom.kioskapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.ohrshalom.kioskapp.config.ConfigManager
import com.ohrshalom.kioskapp.databinding.ActivityMainBinding
import com.ohrshalom.kioskapp.payment.StripePaymentManager
import com.stripe.stripeterminal.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Main Activity - Unified Kiosk Application
 * 
 * This activity serves as the primary interface for the Ohr Shalom donation kiosk.
 * It combines all functionality into a single Android application:
 * 
 * 1. Kiosk Mode - Prevents users from exiting the application
 * 2. Wake Lock - Keeps tablet awake during operation
 * 3. NFC Payments - Handles Stripe Terminal Tap to Pay
 * 4. WebView Integration - Displays donation interface
 * 5. Configuration Management - Manages config.ini file
 * 6. Location Default - Set to Davenport, FL (28.1611, -81.6029)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: ConfigManager
    private lateinit var paymentManager: StripePaymentManager
    private lateinit var sharedPreferences: SharedPreferences
    
    private var nfcAdapter: NfcAdapter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isKioskModeEnabled = false
    
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    
    companion object {
        private const val TAG = "OhrShalomKiosk"
        private const val VERSION = "1.3-admin-interface"
        private const val PREFS_NAME = "kiosk_preferences"
        private const val KEY_KIOSK_MODE = "kiosk_mode_enabled"
        
        // Davenport, FL coordinates (as requested)
        const val DEFAULT_LATITUDE = 28.1611
        const val DEFAULT_LONGITUDE = -81.6029
        const val DEFAULT_GEONAME_ID = 4154279 // Davenport, FL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d(TAG, "=== Ohr Shalom Kiosk v$VERSION starting up ===")
        Log.d(TAG, "Build info - compileSdk: 34, targetSdk: 34, minSdk: 26")
        
        // Initialize components
        initializeComponents()
        
        // Setup WebView with embedded interface
        setupWebView()
        
        // Setup kiosk mode by default
        setupKioskMode()
        
        // Initialize wake lock
        setupWakeLock()
        
        // Setup NFC
        setupNfc()
        
        // Load the embedded kiosk interface
        loadKioskInterface()
        
        // Handle back button to prevent exiting kiosk
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent back button from exiting kiosk mode
                Log.d(TAG, "Back button pressed - blocked in kiosk mode")
                // Show message to user
                binding.webView.post {
                    val script = "if (window.kioskInstance) { window.kioskInstance.showMessage('Kiosk mode active - contact admin to exit', 'warning', 3000); }"
                    binding.webView.evaluateJavascript(script, null)
                }
            }
        })
        
        Log.d(TAG, "Ohr Shalom Kiosk initialized successfully")
    }
    
    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configManager = ConfigManager(this)
        paymentManager = StripePaymentManager(this)
        
        // Enable kiosk mode by default for donation kiosk
        isKioskModeEnabled = sharedPreferences.getBoolean(KEY_KIOSK_MODE, true) // Default to true
        Log.d(TAG, "Kiosk mode enabled: $isKioskModeEnabled")
    }
    
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                
                // Security settings for kiosk mode
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            
            // Add JavaScript interface for Android-WebView communication
            addJavascriptInterface(AndroidJavaScriptInterface(), "AndroidInterface")
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "WebView loading: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "WebView loaded: $url")
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // Block external navigation in kiosk mode
                    val url = request?.url?.toString() ?: ""
                    return if (url.startsWith("file://")) {
                        false // Allow local files
                    } else {
                        Log.d(TAG, "Blocked external navigation to: $url")
                        true // Block external URLs
                    }
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "WebView Console: ${consoleMessage?.message()}")
                    return true
                }
            }
        }
    }
    
    private fun setupKioskMode() {
        if (isKioskModeEnabled) {
            enableKioskMode()
        }
    }
    
    private fun enableKioskMode() {
        try {
            // Hide navigation and status bars
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            val controller = window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            // Set full screen flags with more aggressive settings
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
            
            // Additional window flags for stronger kiosk mode
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            
            isKioskModeEnabled = true
            sharedPreferences.edit().putBoolean(KEY_KIOSK_MODE, true).apply()
            
            Log.d(TAG, "Kiosk mode enabled with aggressive protection")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable kiosk mode", e)
        }
    }
    
    private fun disableKioskMode() {
        try {
            // Restore navigation and status bars
            WindowCompat.setDecorFitsSystemWindows(window, true)
            
            val controller = window.insetsController
            controller?.let {
                it.show(WindowInsets.Type.systemBars())
            }
            
            // Clear full screen flags
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            
            isKioskModeEnabled = false
            sharedPreferences.edit().putBoolean(KEY_KIOSK_MODE, false).apply()
            
            Log.d(TAG, "Kiosk mode disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable kiosk mode", e)
        }
    }
    
    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "OhrShalomKiosk:WakeLock"
        )
    }
    
    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC not supported on this device")
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_SHORT).show()
        } else if (nfcAdapter?.isEnabled == false) {
            Log.w(TAG, "NFC is disabled")
            Toast.makeText(this, "Please enable NFC in settings", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "NFC is enabled and ready")
        }
    }
    
    private fun loadKioskInterface() {
        // Load the embedded HTML interface
        val url = "file:///android_asset/www/index.html"
        binding.webView.loadUrl(url)
        Log.d(TAG, "Loading kiosk interface from: $url")
    }
    
    override fun onResume() {
        super.onResume()
        
        // Acquire wake lock to keep screen on
        wakeLock?.takeIf { !it.isHeld }?.acquire(10*60*1000L /*10 minutes*/)
        
        // Re-enable kiosk mode if it was enabled
        if (isKioskModeEnabled) {
            enableKioskMode()
        }
        
        Log.d(TAG, "Activity resumed - kiosk mode: $isKioskModeEnabled")
    }
    
    override fun onPause() {
        super.onPause()
        
        // Release wake lock to save battery when paused
        wakeLock?.takeIf { it.isHeld }?.release()
        
        // Kiosk mode protection - prevent app from being backgrounded
        if (isKioskModeEnabled) {
            Log.d(TAG, "App paused in kiosk mode - bringing back to foreground")
            // Immediately bring back to foreground
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
        
        Log.d(TAG, "Activity paused")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        wakeLock?.takeIf { it.isHeld }?.release()
        
        Log.d(TAG, "Activity destroyed")
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block hardware keys in kiosk mode
        if (isKioskModeEnabled) {
            return when (keyCode) {
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_RECENT_APPS,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_MENU -> {
                    Log.d(TAG, "Hardware key blocked: $keyCode")
                    true // Consume the event
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * JavaScript Interface for WebView-Android communication
     * Provides methods for the web interface to interact with native Android functionality
     */
    inner class AndroidJavaScriptInterface {
        
        @JavascriptInterface
        fun processNfcPayment(paymentDataJson: String): String {
            return try {
                Log.d(TAG, "Processing NFC payment: $paymentDataJson")
                
                // Parse payment data
                val paymentData = gson.fromJson(paymentDataJson, PaymentData::class.java)
                
                lifecycleScope.launch {
                    try {
                        val success = paymentManager.processNfcPayment(
                            paymentData.amount,
                            paymentData.currency,
                            paymentData.email
                        )
                        
                        // Notify WebView of payment result
                        runOnUiThread {
                            val script = "window.kioskInstance.paymentCompleted($success, ${paymentData.amount}, '${if (success) "Payment successful!" else "Payment failed"}')"
                            binding.webView.evaluateJavascript(script, null)
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "NFC payment error", e)
                        runOnUiThread {
                            val script = "window.kioskInstance.paymentCompleted(false, ${paymentData.amount}, 'Payment error: ${e.message}')"
                            binding.webView.evaluateJavascript(script, null)
                        }
                    }
                }
                
                "processing" // Return immediate response
            } catch (e: Exception) {
                Log.e(TAG, "Error processing NFC payment", e)
                "error"
            }
        }
        
        @JavascriptInterface
        fun getConfig(): String {
            return try {
                val config = configManager.getConfig()
                gson.toJson(config)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting config", e)
                "{}"
            }
        }
        
        @JavascriptInterface
        fun saveConfig(configJson: String): Boolean {
            return try {
                val config = gson.fromJson(configJson, ConfigManager.Config::class.java)
                configManager.saveConfig(config)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving config", e)
                false
            }
        }
        
        @JavascriptInterface
        fun getHebrewCalendar(): String {
            Log.d(TAG, "=== HEBCAL DEBUG: getHebrewCalendar() called ===")
            
            // Load Hebrew calendar data asynchronously and update WebView
            lifecycleScope.launch {
                try {
                    val config = configManager.getConfig()
                    Log.d(TAG, "HEBCAL DEBUG: Config loaded - lat: ${config.latitude}, lng: ${config.longitude}, geonameId: ${config.geonameId}")
                    
                    // Always use coordinates as they are more reliable than geonameId
                    val url = "https://www.hebcal.com/shabbat?cfg=json&m=50&latitude=${config.latitude}&longitude=${config.longitude}"
                    
                    Log.d(TAG, "HEBCAL DEBUG: Fetching Hebrew calendar from: $url")
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "OhrShalomKiosk/1.1")
                        .build()
                    
                    Log.d(TAG, "HEBCAL DEBUG: Making network request...")
                    val response = withContext(Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }
                    
                    Log.d(TAG, "HEBCAL DEBUG: Network response received - Code: ${response.code}, Success: ${response.isSuccessful}")
                    
                    if (response.isSuccessful) {
                        val calendarData = response.body?.string() ?: ""
                        Log.d(TAG, "HEBCAL DEBUG: Response body length: ${calendarData.length}")
                        Log.d(TAG, "HEBCAL DEBUG: Response preview: ${calendarData.take(200)}...")
                        Log.d(TAG, "HEBCAL DEBUG: Full response: $calendarData")
                        
                        // Parse the JSON to validate structure
                        try {
                            val parsedJson = gson.fromJson(calendarData, Map::class.java)
                            val items = parsedJson["items"] as? List<*>
                            Log.d(TAG, "HEBCAL DEBUG: Parsed JSON successfully - Items count: ${items?.size ?: 0}")
                            
                            items?.forEachIndexed { index, item ->
                                val itemMap = item as? Map<*, *>
                                Log.d(TAG, "HEBCAL DEBUG: Item $index - Category: ${itemMap?.get("category")}, Title: ${itemMap?.get("title")}")
                            }
                        } catch (jsonError: Exception) {
                            Log.e(TAG, "HEBCAL DEBUG: JSON parsing error", jsonError)
                        }
                        
                        // Update WebView with calendar data
                        runOnUiThread {
                            Log.d(TAG, "HEBCAL DEBUG: Updating WebView with Hebrew calendar data")
                            
                            // Check if WebView and kioskInstance exist
                            val checkScript = "window.kioskInstance ? 'kioskInstance exists' : 'kioskInstance missing'"
                            binding.webView.evaluateJavascript(checkScript) { result ->
                                Log.d(TAG, "HEBCAL DEBUG: WebView kioskInstance check result: $result")
                            }
                            
                            // Pass the raw JSON string directly to JavaScript
                            val escapedJson = calendarData.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                            val script = "if (window.kioskInstance && window.kioskInstance.displayHebrewCalendar) { try { window.kioskInstance.displayHebrewCalendar(JSON.parse(\"$escapedJson\")); console.log('HEBCAL DEBUG: displayHebrewCalendar called successfully'); } catch(e) { console.error('HEBCAL DEBUG: Error calling displayHebrewCalendar:', e); } } else { console.error('HEBCAL DEBUG: kioskInstance or displayHebrewCalendar method not found'); }"
                            
                            Log.d(TAG, "HEBCAL DEBUG: Executing WebView script")
                            binding.webView.evaluateJavascript(script) { result ->
                                Log.d(TAG, "HEBCAL DEBUG: WebView script execution result: $result")
                            }
                        }
                    } else {
                        Log.e(TAG, "HEBCAL DEBUG: API failed - Code: ${response.code}, Message: ${response.message}")
                        Log.e(TAG, "HEBCAL DEBUG: Response headers: ${response.headers}")
                        response.body?.let { body ->
                            val errorBody = body.string()
                            Log.e(TAG, "HEBCAL DEBUG: Error response body: $errorBody")
                        }
                        
                        runOnUiThread {
                            val script = "if (window.kioskInstance && window.kioskInstance.setCalendarErrorStates) { window.kioskInstance.setCalendarErrorStates(); console.log('HEBCAL DEBUG: setCalendarErrorStates called'); } else { console.error('HEBCAL DEBUG: setCalendarErrorStates method not found'); }"
                            binding.webView.evaluateJavascript(script, null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "HEBCAL DEBUG: Exception in getHebrewCalendar", e)
                    Log.e(TAG, "HEBCAL DEBUG: Exception type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "HEBCAL DEBUG: Exception message: ${e.message}")
                    Log.e(TAG, "HEBCAL DEBUG: Exception stack trace: ${e.stackTrace.joinToString("\n")}")
                    
                    runOnUiThread {
                        val script = "if (window.kioskInstance && window.kioskInstance.setCalendarErrorStates) { window.kioskInstance.setCalendarErrorStates(); console.log('HEBCAL DEBUG: Error state set due to exception'); } else { console.error('HEBCAL DEBUG: Cannot set error state - method not found'); }"
                        binding.webView.evaluateJavascript(script, null)
                    }
                }
            }
            
            Log.d(TAG, "HEBCAL DEBUG: getHebrewCalendar() returning empty object")
            return "{}" // Return empty object immediately, actual data will be updated via callback
        }
        
        @JavascriptInterface
        fun showAdminConfig() {
            runOnUiThread {
                Log.d(TAG, "ADMIN DEBUG: showAdminConfig called - JavaScript will handle the UI")
                
                // Temporarily disable kiosk mode to allow configuration
                if (isKioskModeEnabled) {
                    Log.d(TAG, "ADMIN DEBUG: Temporarily disabling kiosk mode for admin access")
                    disableKioskMode()
                    
                    // Re-enable after 2 minutes to give admin time to configure
                    binding.webView.postDelayed({
                        Log.d(TAG, "ADMIN DEBUG: Re-enabling kiosk mode after admin timeout")
                        enableKioskMode()
                    }, 120000) // 2 minutes
                }
                
                // Log current configuration for debugging
                val config = configManager.getConfig()
                Log.d(TAG, "ADMIN DEBUG: Current config - Org: ${config.organizationName}, " +
                        "Location: ${config.latitude},${config.longitude}, " +
                        "Timezone: ${config.timeZone}, " +
                        "Prayer times: ${config.shacharit}/${config.mincha}/${config.maariv}")
            }
        }
        
        @JavascriptInterface
        fun enterKioskMode() {
            runOnUiThread {
                enableKioskMode()
                Toast.makeText(this@MainActivity, "Kiosk mode enabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun exitKioskMode() {
            runOnUiThread {
                disableKioskMode()
                Toast.makeText(this@MainActivity, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun getNfcStatus(): String {
            return when {
                nfcAdapter == null -> "not_supported"
                nfcAdapter?.isEnabled == false -> "disabled"
                else -> "enabled"
            }
        }
        
        @JavascriptInterface
        fun openNfcSettings() {
            runOnUiThread {
                try {
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open NFC settings", e)
                }
            }
        }
        
        @JavascriptInterface
        fun log(message: String) {
            Log.d("WebView", message)
        }
        
        @JavascriptInterface
        fun getKioskStatus(): String {
            return try {
                val status = mapOf(
                    "version" to VERSION,
                    "kioskMode" to isKioskModeEnabled,
                    "wakeLockHeld" to (wakeLock?.isHeld == true),
                    "nfcStatus" to getNfcStatus(),
                    "configFile" to configManager.configFileExists(),
                    "timestamp" to System.currentTimeMillis()
                )
                gson.toJson(status)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting kiosk status", e)
                "{\"error\":\"${e.message}\"}"
            }
        }
        
        @JavascriptInterface
        fun updateStripeConfig(stripeConfigJson: String): String {
            return try {
                Log.d(TAG, "Updating Stripe configuration: $stripeConfigJson")
                
                // Parse Stripe configuration
                val stripeConfig = gson.fromJson(stripeConfigJson, StripeConfig::class.java)
                
                // Validate required fields
                if (stripeConfig.publishableKey.isBlank()) {
                    return "error: Publishable key is required"
                }
                
                // Validate key format based on environment
                if (stripeConfig.environment == "test" && !stripeConfig.publishableKey.startsWith("pk_test_")) {
                    return "error: Test environment requires pk_test_ key"
                }
                
                if (stripeConfig.environment == "live" && !stripeConfig.publishableKey.startsWith("pk_live_")) {
                    return "error: Live environment requires pk_live_ key"
                }
                
                // Update payment manager with new configuration
                val success = paymentManager.updateConfiguration(
                    stripeConfig.publishableKey,
                    stripeConfig.tokenEndpoint,
                    stripeConfig.locationId,
                    stripeConfig.environment == "live"
                )
                
                if (success) {
                    // Save to configuration file
                    configManager.updateStripeConfig(stripeConfig)
                    Log.d(TAG, "Stripe configuration updated successfully")
                    "success"
                } else {
                    "error: Failed to update payment manager"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating Stripe configuration", e)
                "error: ${e.message}"
            }
        }
        
        @JavascriptInterface
        fun getTerminalStatus(): String {
            return try {
                paymentManager.getTerminalStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting terminal status", e)
                "Error: ${e.message}"
            }
        }
    }

    
    override fun onStop() {
        super.onStop()
        if (isKioskModeEnabled) {
            Log.d(TAG, "App stopped in kiosk mode - restarting")
            // Restart the activity to prevent being backgrounded
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isKioskModeEnabled) {
            Log.d(TAG, "User trying to leave app in kiosk mode - preventing")
            // Immediately bring back to foreground
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Ensure we stay in kiosk mode when launched via intent
        if (isKioskModeEnabled) {
            Log.d(TAG, "New intent received - maintaining kiosk mode")
            enableKioskMode()
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-enable immersive mode when focus is regained in kiosk mode
        if (hasFocus && isKioskModeEnabled) {
            Log.d(TAG, "Window focus regained - re-enabling kiosk mode")
            enableKioskMode()
        }
    }
    
    data class PaymentData(
        val amount: Int, // Amount in cents
        val currency: String,
        val email: String?
    )
    
    data class StripeConfig(
        val publishableKey: String,
        val tokenEndpoint: String,
        val locationId: String,
        val environment: String // "test" or "live"
    )
}