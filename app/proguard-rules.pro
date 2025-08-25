# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}

# Keep JavaScript interface for WebView
-keepclassmembers class com.ohrshalom.kioskapp.MainActivity$AndroidJavaScriptInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Stripe Terminal SDK rules
-keep class com.stripe.stripeterminal.** { *; }
-keep interface com.stripe.stripeterminal.** { *; }

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep configuration data classes
-keep class com.ohrshalom.kioskapp.config.** { *; }
-keep class com.ohrshalom.kioskapp.payment.** { *; }

# Keep data classes used with Gson
-keep class com.ohrshalom.kioskapp.MainActivity$PaymentData { *; }
-keep class com.ohrshalom.kioskapp.MainActivity$ConfigData { *; }