# 🧪 **Beta Testing Branch - Stripe Terminal Integration**

## 📋 **Beta Version Information**

- **Branch**: `Beta`
- **Version**: `1.9.5-sdk-compatibility-fixed` 
- **Build Code**: 15
- **Status**: Ready for Beta Testing
- **Created**: Based on v1.9.5-production-ready

---

## 🎯 **What's Being Tested**

### **Stripe Terminal Features**
✅ **Auto-Configuration**: No manual setup required  
✅ **Production Location**: `tml_GKsXoQ8u9cFZJF`  
✅ **Dynamic Serial Handling**: Current serial `b83edde4-d97a-4f83-8f6f-fce89c1ed3cc`  
✅ **Smart Reconnection**: Reuses existing readers  
✅ **Heartbeat System**: 30-second keepalive  
✅ **Location Linking**: Reader properly linked to location  
✅ **Status Reporting**: Enhanced logging and monitoring  

### **Key Improvements Over 1.8.4**
- **Resolved**: Reader going offline issues
- **Resolved**: Reader not linked to correct location
- **Resolved**: Multiple reader registrations on reinstall
- **Added**: Automatic serial number adaptation
- **Added**: Connection state validation
- **Added**: Comprehensive error handling

---

## 🚀 **Beta Testing Checkout**

### **Command to Test This Version:**
```bash
git checkout Beta
./gradlew clean assembleDebug
```

### **APK Location:**
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔍 **Testing Checklist**

### **1. Version Verification**
- [ ] Admin screen shows: `1.9.5-sdk-compatibility-fixed`
- [ ] App builds without compilation errors
- [ ] No crashes on startup

### **2. Stripe Terminal Connection**
- [ ] App auto-initializes Stripe Terminal  
- [ ] Reader connects to location `tml_GKsXoQ8u9cFZJF`
- [ ] Reader stays online (check Stripe dashboard)
- [ ] Serial number matches: `b83edde4-d97a-4f83-8f6f-fce89c1ed3cc`

### **3. Reconnection Testing**
- [ ] Kill app, restart → reconnects to same reader
- [ ] Reader remains online after reconnection
- [ ] No duplicate readers created in dashboard

### **4. Fresh Install Testing**
- [ ] Uninstall app completely
- [ ] Reinstall APK → gets new or reuses serial number
- [ ] Connects successfully to correct location
- [ ] Only one reader visible in dashboard

---

## 📊 **Expected Log Messages**

### **Successful Connection:**
```
✅ Location validated: [Location Name] (tml_GKsXoQ8u9cFZJF)
✅ Local Mobile reader connected successfully!
🎉 Android tablet assigned NEW reader serial for location: tml_GKsXoQ8u9cFZJF
💓 Started reader heartbeat (every 30s)
```

### **Reconnection:**
```
🔍 Found existing reader registration: b83edde4-d97a-4f83-8f6f-fce89c1ed3cc
🔄 EXISTING READER RECONNECTED!
💓 Heartbeat: Reader b83edde4... is alive
```

---

## 🐛 **Issue Reporting**

### **What to Test and Report:**

1. **Connection Stability**: Does reader stay online?
2. **Location Linking**: Is reader linked to `tml_GKsXoQ8u9cFZJF`?  
3. **Serial Persistence**: Same serial after app restart?
4. **Fresh Install**: New install behavior with serial numbers?
5. **Admin Screen**: Correct version display?

### **Log Collection:**
Enable detailed logging and capture:
- Stripe Terminal initialization logs
- Reader discovery and connection logs  
- Heartbeat status messages
- Any error messages

---

## 🎯 **Success Criteria**

This Beta is successful if:
- ✅ **Stable Connection**: Reader stays online consistently
- ✅ **Correct Location**: Reader linked to `tml_GKsXoQ8u9cFZJF` 
- ✅ **Single Reader**: No duplicate registrations
- ✅ **Version Display**: Admin shows `1.9.5-sdk-compatibility-fixed`
- ✅ **Persistence**: Same reader reused across app restarts

---

## 📞 **Feedback**

Report test results with:
- Device info (tablet model, Android version)
- Log snippets (especially connection and heartbeat logs)
- Stripe dashboard screenshots (reader status)
- Any issues or unexpected behavior

**Ready for comprehensive Beta testing!** 🧪