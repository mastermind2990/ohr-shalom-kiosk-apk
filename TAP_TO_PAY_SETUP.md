# Tap to Pay Setup Guide - Ohr Shalom Kiosk

## Overview
This document outlines the complete setup process for enabling Tap to Pay on Android using Stripe Terminal SDK 4.6.0 for the Ohr Shalom Donation Kiosk.

## ✅ Current Implementation Status

### Version 1.9.1-beta-1 Changes
- **✅ Correct API Implementation**: Updated to use proper Stripe Terminal 4.6.0 Tap to Pay APIs
- **✅ Device Support Check**: Added `Terminal.supportsReadersOfType()` validation
- **✅ Process Handling**: Added `TapToPay.isInTapToPayProcess()` check
- **✅ Proper Discovery**: Using `TapToPayDiscoveryConfiguration(isSimulated = false)`
- **✅ Correct Connection**: Using `TapToPayConnectionConfiguration` with all required parameters
- **✅ Auto-reconnect**: Implemented `TapToPayReaderListener` for connection management
- **✅ Error Handling**: Added specific error messages for common issues

## 🔧 Technical Requirements

### Device Requirements (Must Meet ALL)
- ✅ **NFC Hardware**: Functioning NFC antenna and chipset
- ✅ **Security**: Device NOT rooted, bootloader locked and unchanged  
- ✅ **OS Version**: Android 11 (API 30) or higher
- ✅ **Google Services**: Google Mobile Services (GMS) certified with Play Store
- ✅ **Hardware Security**: Hardware-backed keystore
- ✅ **Cryptography**: Support for RSA and AES key generation from Android keystore
- ✅ **Network**: Stable internet connection
- ✅ **OS Integrity**: Unmodified manufacturer-provided OS

### Stripe Account Requirements
- ✅ **Terminal Enabled**: Stripe account with Terminal capability
- ✅ **Tap to Pay Access**: Account enabled for Tap to Pay (may require Stripe support)
- ✅ **Location Setup**: Terminal Location created in Dashboard
- ✅ **API Keys**: Valid secret and publishable keys
- ✅ **Connection Token Endpoint**: Server endpoint for generating connection tokens

## 🛠️ Implementation Steps

### 1. Account Setup
```bash
# Check if your account supports Tap to Pay
# If you get "ACCOUNT_NOT_ENABLED" error, contact Stripe support
```

### 2. Create Terminal Location
```bash
# Via Dashboard: Terminal > Locations > Create Location
# Or via API: POST /v1/terminal/locations
# SAVE THE LOCATION_ID - you'll need it for device registration
```

### 3. Server Setup (Already Implemented)
- ✅ Connection token endpoint: `http://161.35.140.12/api/stripe/connection_token`
- ✅ Returns valid `pst_` prefixed tokens
- ✅ Integrated with production Stripe API

### 4. Device Registration Process

#### Step 1: Check Device Compatibility
```kotlin
// Already implemented in StripePaymentManager
val supported = isTapToPaySupported()
val nfcEnabled = isNfcAvailable()
```

#### Step 2: Initialize Terminal
```kotlin
// Already implemented with correct 4.6.0 API
// Includes TapToPay.isInTapToPayProcess() check
```

#### Step 3: Discover Tap to Pay Reader
```kotlin
// Call updateConfiguration with your Stripe location ID
paymentManager.updateConfiguration(
    publishableKey = "pk_live_YOUR_KEY",
    tokenEndpoint = null, // Using hardcoded production endpoint
    locationId = "tml_YOUR_LOCATION_ID", // REPLACE WITH YOUR LOCATION ID
    isLiveMode = true
)
```

#### Step 4: Connect Device as Reader
```kotlin
// Automatically handled by discoverTapToPayReader()
// Device will appear as Tap to Pay reader in Stripe Dashboard
```

## 🚨 Common Issues & Solutions

### Issue: "ACCOUNT_NOT_ENABLED"
**Solution**: Contact Stripe support to enable Tap to Pay for your account
- Email: support@stripe.com
- Subject: "Enable Tap to Pay on Android for Terminal"
- Include: Account ID, business use case

### Issue: "UNSUPPORTED_FEATURE" 
**Solution**: Device doesn't meet requirements
- Verify device is GMS certified
- Check Android version (must be 11+)
- Ensure device is not rooted

### Issue: "LOCATION_NOT_FOUND"
**Solution**: Create Terminal location in Dashboard
1. Go to Dashboard > Terminal > Locations
2. Click "Create Location" 
3. Use the location ID in `updateConfiguration()`

### Issue: "ATTESTATION_FAILURE"
**Solution**: Device is not Google Mobile Services certified
- Use different device that's GMS certified
- Cannot be resolved on non-GMS devices

## 📋 Testing Checklist

### Pre-Testing Setup
- [ ] Stripe account has Terminal enabled
- [ ] Terminal Location created and location ID obtained
- [ ] Device meets all hardware/software requirements
- [ ] NFC is enabled in device settings
- [ ] App has NFC permissions granted

### Testing Steps
1. **Install APK**: Deploy version 1.9.1-beta-1 to tablet
2. **Check Logs**: Monitor `StripePaymentManager` logs for errors
3. **Configuration**: Call `updateConfiguration()` with your location ID
4. **Discovery**: Verify "Tap to Pay reader discovery completed successfully"
5. **Connection**: Look for "✅ Tap to Pay reader connected successfully!"
6. **Dashboard**: Check Stripe Dashboard > Terminal > Readers for connected device
7. **Payment Test**: Try processing a small test payment

### Expected Log Output (Success)
```
D/StripePaymentManager: Stripe Terminal initialized successfully
D/StripePaymentManager: Starting Tap to Pay reader discovery for location: tml_xxxxx
D/StripePaymentManager: Discovered 1 Tap to Pay readers
D/StripePaymentManager: Found Tap to Pay reader: tmr_xxxxx
D/StripePaymentManager: ✅ Tap to Pay reader connected successfully!
D/StripePaymentManager: 🎉 Android tablet is now registered as Tap to Pay reader
```

## 🔗 Key Configuration

### Required Location ID
You MUST obtain your Terminal Location ID from Stripe Dashboard and use it in:
```kotlin
paymentManager.updateConfiguration(
    publishableKey = "pk_live_YOUR_ACTUAL_KEY",
    tokenEndpoint = null,
    locationId = "tml_YOUR_ACTUAL_LOCATION_ID", // ⚠️ CRITICAL: Replace this
    isLiveMode = true
)
```

### Production vs Test Mode
- **Test Mode**: Uses simulated readers (`isSimulated = true`)
- **Production Mode**: Uses real hardware (`isSimulated = false`)
- Current implementation auto-detects based on app debuggable flag

## 🎯 Next Steps

1. **Get Location ID**: Create Terminal Location in Stripe Dashboard
2. **Update Configuration**: Replace placeholder location ID in code
3. **Test on Device**: Deploy to compatible Android tablet
4. **Verify in Dashboard**: Confirm device appears as connected reader
5. **Process Test Payment**: Validate end-to-end payment flow

## 📞 Support Resources

- **Stripe Terminal Docs**: https://docs.stripe.com/terminal/payments/setup-reader/tap-to-pay?platform=android
- **Device Requirements**: https://docs.stripe.com/terminal/payments/setup-reader/tap-to-pay?platform=android#device-requirements  
- **Stripe Support**: support@stripe.com
- **Terminal Announcements**: terminal-announce@lists.stripe.com

---

**Version**: 1.9.1-beta-1  
**Last Updated**: August 27, 2025  
**SDK Version**: Stripe Terminal Android 4.6.0