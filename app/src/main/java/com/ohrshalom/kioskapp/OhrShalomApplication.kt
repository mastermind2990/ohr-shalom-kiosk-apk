package com.ohrshalom.kioskapp

import android.app.Application
import android.util.Log
import com.stripe.stripeterminal.TerminalApplicationDelegate

/**
 * Custom Application class for Ohr Shalom Kiosk
 * 
 * Initializes Stripe Terminal delegate to fix integration warnings
 */
class OhrShalomApplication : Application() {
    
    companion object {
        private const val TAG = "OhrShalomApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize TerminalApplicationDelegate to fix Stripe Terminal warning
            Log.d(TAG, "Initializing TerminalApplicationDelegate...")
            TerminalApplicationDelegate.onCreate()
            Log.d(TAG, "TerminalApplicationDelegate initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TerminalApplicationDelegate", e)
        }
    }
}