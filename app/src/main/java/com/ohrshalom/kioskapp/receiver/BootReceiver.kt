package com.ohrshalom.kioskapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ohrshalom.kioskapp.MainActivity

/**
 * Boot receiver to automatically start the kiosk activity on device boot
 * This ensures the tablet always returns to kiosk mode after restart
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Boot completed - starting kiosk activity")
                
                val kioskIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                           Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                           Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                try {
                    context.startActivity(kioskIntent)
                    Log.d(TAG, "Kiosk activity started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start kiosk activity on boot", e)
                }
            }
        }
    }
}