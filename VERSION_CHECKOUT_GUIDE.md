# Ohr Shalom Kiosk - Version Checkout Guide

## 📱 **Available Versions**

### **🎯 RECOMMENDED: v1.9.5-production-ready**
**Latest stable version with full Stripe Terminal integration**

### **Quick Checkout Commands**

#### **Option 1: Checkout Version Branch (Recommended)**
```bash
git checkout v1.9.5-production-ready
```

#### **Option 2: Checkout Version Tag**  
```bash
git checkout tags/v1.9.5-production-ready
```

#### **Option 3: Fresh Clone with Specific Version**
```bash
git clone https://github.com/mastermind2990/ohr-shalom-kiosk-apk.git
cd ohr-shalom-kiosk-apk
git checkout v1.9.5-production-ready
```

---

## 🏷️ **Version History**

### **v1.9.5-production-ready** ✅ **← USE THIS VERSION**
- **Features**: Complete Stripe Terminal integration with dynamic serial handling
- **Location**: `tml_GKsXoQ8u9cFZJF` (production location)  
- **Status**: ✅ Production-ready, compilation tested
- **Serial**: Automatically adapts to whatever Stripe assigns
- **Connection**: Stable with heartbeat and reconnection

### **v1.8.4-terminal-compliant** ❌ **← OLD VERSION**
- **Status**: ❌ Outdated, has reader connection issues
- **Issues**: Reader goes offline, not linked to location properly

---

## 🔧 **Build Instructions**

After checking out **v1.9.5-production-ready**:

```bash
# In Android Studio or command line:
./gradlew assembleDebug

# The APK will be in:
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📊 **Version Verification**

After building, the admin screen should show:
- **Version**: `1.9.5-sdk-compatibility-fixed`  
- **Build**: Ready for production testing

If you see `1.8.4-terminal-compliant`, you're on the wrong version!

---

## 🎯 **What This Version Includes**

✅ **Auto-Configuration**: No manual Stripe setup needed  
✅ **Production Location**: Automatically connects to `tml_GKsXoQ8u9cFZJF`  
✅ **Dynamic Serial Handling**: Adapts to new APK installations  
✅ **Smart Reconnection**: Reuses existing readers when possible  
✅ **Heartbeat System**: Keeps readers online (30s interval)  
✅ **Location Validation**: Ensures reader properly linked  
✅ **Enhanced Logging**: Clear status messages for debugging  

---

## 🚨 **Troubleshooting**

**Issue**: Admin shows old version (1.8.4)  
**Solution**: Ensure you checked out `v1.9.5-production-ready` branch

**Issue**: Compilation errors  
**Solution**: Clean project and rebuild:
```bash
./gradlew clean assembleDebug
```

**Issue**: Reader not connecting  
**Solution**: Check logs for detailed connection status and heartbeat messages