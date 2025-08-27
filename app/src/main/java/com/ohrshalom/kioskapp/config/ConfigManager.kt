package com.ohrshalom.kioskapp.config

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties

/**
 * Configuration Manager for Ohr Shalom Kiosk
 * 
 * Manages the config.ini file that stores kiosk settings.
 * Default location is set to Davenport, FL as requested.
 */
class ConfigManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ConfigManager"
        private const val CONFIG_FILE_NAME = "config.ini"
        
        // Default configuration values (Davenport, FL)
        private const val DEFAULT_LATITUDE = 28.1611
        private const val DEFAULT_LONGITUDE = -81.6029
        private const val DEFAULT_GEONAME_ID = 0 // Disabled - use coordinates instead
        private const val DEFAULT_TIMEZONE = "America/New_York"
        private const val DEFAULT_ADMIN_PIN = "12345"
        private const val DEFAULT_ORGANIZATION_NAME = "Ohr Shalom"
        private const val DEFAULT_LOCATION_METHOD = "coordinates"
        private const val DEFAULT_SHACHARIT = "7:00 AM"
        private const val DEFAULT_MINCHA = "2:00 PM"
        private const val DEFAULT_MAARIV = "8:00 PM"
    }
    
    private val configFile = File(context.filesDir, CONFIG_FILE_NAME)
    
    data class Config(
        val latitude: Double = DEFAULT_LATITUDE,
        val longitude: Double = DEFAULT_LONGITUDE,
        val timeZone: String = DEFAULT_TIMEZONE,
        val adminPin: String = DEFAULT_ADMIN_PIN,
        val organizationName: String = DEFAULT_ORGANIZATION_NAME,
        val geonameId: Int? = DEFAULT_GEONAME_ID,
        val locationMethod: String = DEFAULT_LOCATION_METHOD,
        val shacharit: String = DEFAULT_SHACHARIT,
        val mincha: String = DEFAULT_MINCHA,
        val maariv: String = DEFAULT_MAARIV
    )
    
    init {
        // Create default config file if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfig()
        }
    }
    
    private fun createDefaultConfig() {
        try {
            val properties = Properties()
            
            // Location settings - Default to Davenport, FL (using coordinates)
            properties.setProperty("latitude", DEFAULT_LATITUDE.toString())
            properties.setProperty("longitude", DEFAULT_LONGITUDE.toString())
            // Removed geonameId as it was causing 404 errors - use coordinates instead
            properties.setProperty("locationMethod", DEFAULT_LOCATION_METHOD)
            
            // Time and location
            properties.setProperty("timeZone", DEFAULT_TIMEZONE)
            
            // Organization settings
            properties.setProperty("organizationName", DEFAULT_ORGANIZATION_NAME)
            
            // Security
            properties.setProperty("adminPin", DEFAULT_ADMIN_PIN)
            
            // Prayer times
            properties.setProperty("shacharit", DEFAULT_SHACHARIT)
            properties.setProperty("mincha", DEFAULT_MINCHA)
            properties.setProperty("maariv", DEFAULT_MAARIV)
            
            // Save to file
            FileOutputStream(configFile).use { outputStream ->
                properties.store(outputStream, "Ohr Shalom Kiosk Configuration - Davenport, FL (Coordinates)")
            }
            
            Log.d(TAG, "Created default config file with Davenport, FL settings")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create default config file", e)
        }
    }
    
    fun getConfig(): Config {
        return try {
            val properties = Properties()
            
            FileInputStream(configFile).use { inputStream ->
                properties.load(inputStream)
            }
            
            Config(
                latitude = properties.getProperty("latitude", DEFAULT_LATITUDE.toString()).toDoubleOrNull() ?: DEFAULT_LATITUDE,
                longitude = properties.getProperty("longitude", DEFAULT_LONGITUDE.toString()).toDoubleOrNull() ?: DEFAULT_LONGITUDE,
                timeZone = properties.getProperty("timeZone", DEFAULT_TIMEZONE),
                adminPin = properties.getProperty("adminPin", DEFAULT_ADMIN_PIN),
                organizationName = properties.getProperty("organizationName", DEFAULT_ORGANIZATION_NAME),
                geonameId = properties.getProperty("geonameId")?.toIntOrNull() ?: DEFAULT_GEONAME_ID,
                locationMethod = properties.getProperty("locationMethod", DEFAULT_LOCATION_METHOD),
                shacharit = properties.getProperty("shacharit", DEFAULT_SHACHARIT),
                mincha = properties.getProperty("mincha", DEFAULT_MINCHA),
                maariv = properties.getProperty("maariv", DEFAULT_MAARIV)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config, using defaults", e)
            Config() // Return default config
        }
    }
    
    fun saveConfig(config: Config): Boolean {
        return try {
            val properties = Properties()
            
            // Location settings
            properties.setProperty("latitude", config.latitude.toString())
            properties.setProperty("longitude", config.longitude.toString())
            config.geonameId?.let { properties.setProperty("geonameId", it.toString()) }
            properties.setProperty("locationMethod", config.locationMethod)
            
            // Time and location
            properties.setProperty("timeZone", config.timeZone)
            
            // Organization settings
            properties.setProperty("organizationName", config.organizationName)
            
            // Security
            properties.setProperty("adminPin", config.adminPin)
            
            // Prayer times
            properties.setProperty("shacharit", config.shacharit)
            properties.setProperty("mincha", config.mincha)
            properties.setProperty("maariv", config.maariv)
            
            // Save to file
            FileOutputStream(configFile).use { outputStream ->
                properties.store(outputStream, "Ohr Shalom Kiosk Configuration - Updated: ${System.currentTimeMillis()}")
            }
            
            Log.d(TAG, "Configuration saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
            false
        }
    }
    
    fun updateConfigProperty(key: String, value: String): Boolean {
        return try {
            val currentConfig = getConfig()
            val properties = Properties()
            
            // Load current properties
            FileInputStream(configFile).use { inputStream ->
                properties.load(inputStream)
            }
            
            // Update the specific property
            properties.setProperty(key, value)
            
            // Save back to file
            FileOutputStream(configFile).use { outputStream ->
                properties.store(outputStream, "Ohr Shalom Kiosk Configuration - Property Updated: $key")
            }
            
            Log.d(TAG, "Configuration property updated: $key = $value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update config property: $key", e)
            false
        }
    }
    
    fun resetToDefaults(): Boolean {
        return try {
            if (configFile.exists()) {
                configFile.delete()
            }
            createDefaultConfig()
            Log.d(TAG, "Configuration reset to defaults (Davenport, FL)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset configuration", e)
            false
        }
    }
    
    fun getConfigFilePath(): String {
        return configFile.absolutePath
    }
    
    fun configFileExists(): Boolean {
        return configFile.exists()
    }
    
    /**
     * Update Stripe configuration in the config file
     */
    fun updateStripeConfig(stripeConfig: com.ohrshalom.kioskapp.MainActivity.StripeConfig): Boolean {
        return try {
            val properties = Properties()
            
            // Load current properties if file exists
            if (configFile.exists()) {
                FileInputStream(configFile).use { inputStream ->
                    properties.load(inputStream)
                }
            }
            
            // Update Stripe properties
            properties.setProperty("stripe.publishableKey", stripeConfig.publishableKey)
            properties.setProperty("stripe.tokenEndpoint", stripeConfig.tokenEndpoint)
            properties.setProperty("stripe.locationId", stripeConfig.locationId)
            properties.setProperty("stripe.environment", stripeConfig.environment)
            
            // Save back to file
            FileOutputStream(configFile).use { outputStream ->
                properties.store(outputStream, "Ohr Shalom Kiosk Configuration - Stripe Updated: ${System.currentTimeMillis()}")
            }
            
            Log.d(TAG, "Stripe configuration saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Stripe configuration", e)
            false
        }
    }
}