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
        
        Log.d(TAG, "Ohr Shalom Kiosk starting up...")
        
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
            // Load Hebrew calendar data asynchronously and update WebView
            lifecycleScope.launch {
                try {
                    val config = configManager.getConfig()
                    val url = if (config.geonameId != null && config.geonameId != 0) {
                        "https://www.hebcal.com/shabbat?cfg=json&m=50&geonameid=${config.geonameId}"
                    } else {
                        "https://www.hebcal.com/shabbat?cfg=json&m=50&latitude=${config.latitude}&longitude=${config.longitude}"
                    }
                    
                    Log.d(TAG, "Fetching Hebrew calendar from: $url")
                    val request = Request.Builder().url(url).build()
                    val response = withContext(Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }
                    
                    if (response.isSuccessful) {
                        val calendarData = response.body?.string() ?: ""
                        Log.d(TAG, "Hebrew calendar response: $calendarData")
                        
                        // Update WebView with calendar data (properly escaped JSON)
                        runOnUiThread {
                            // Use Gson to properly escape JSON data
                            val escapedJsonString = gson.toJson(calendarData)
                            val script = "if (window.kioskInstance && window.kioskInstance.displayHebrewCalendar) { window.kioskInstance.displayHebrewCalendar(JSON.parse($escapedJsonString)); }"
                            binding.webView.evaluateJavascript(script, null)
                        }
                    } else {
                        Log.e(TAG, "Hebrew calendar API failed: ${response.code} ${response.message}")
                        runOnUiThread {
                            val script = "if (window.kioskInstance && window.kioskInstance.setCalendarErrorStates) { window.kioskInstance.setCalendarErrorStates(); }"
                            binding.webView.evaluateJavascript(script, null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching Hebrew calendar", e)
                    runOnUiThread {
                        val script = "if (window.kioskInstance && window.kioskInstance.setCalendarErrorStates) { window.kioskInstance.setCalendarErrorStates(); }"
                        binding.webView.evaluateJavascript(script, null)
                    }
                }
            }
            
            return "{}" // Return empty object immediately, actual data will be updated via callback
        }
        
        @JavascriptInterface
        fun showAdminConfig() {
            runOnUiThread {
                // Show admin configuration options
                val config = configManager.getConfig()
                val message = """
                    Admin Configuration Access:
                    
                    Location: Davenport, FL (${config.geonameId})
                    Coordinates: ${config.latitude}, ${config.longitude}
                    Organization: ${config.organizationName}
                    
                    Kiosk Mode: ${if (isKioskModeEnabled) "ENABLED" else "DISABLED"}
                    
                    Prayer Times:
                    Shacharit: ${config.shacharit}
                    Mincha: ${config.mincha} 
                    Maariv: ${config.maariv}
                    
                    Tap outside to return to kiosk.
                """.trimIndent()
                
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                
                // Temporarily disable kiosk mode to allow configuration
                if (isKioskModeEnabled) {
                    disableKioskMode()
                    // Re-enable after 30 seconds
                    binding.webView.postDelayed({
                        enableKioskMode()
                    }, 30000)
                }
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
}