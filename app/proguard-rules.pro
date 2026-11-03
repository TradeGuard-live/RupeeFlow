# ==============================================================================
# RupeeFlow — Production ProGuard & R8 Optimization Rules ✅
# ==============================================================================

# Core Kotlin & Annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**

# Android Architecture & Room Database
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.naman.rupeeflow.data.local.entity.** { *; }
-keep class com.naman.rupeeflow.data.local.converter.** { *; }
-keep class com.naman.rupeeflow.data.local.dao.** { *; }

# Domain Models & Enums (Preserve data class properties for reflection / serialization)
-keep class com.naman.rupeeflow.domain.model.** { *; }
-keepclassmembers enum com.naman.rupeeflow.domain.model.** { *; }

# SafeMath & Currency Utilities
-keep class com.naman.rupeeflow.domain.util.SafeMath { *; }
-keep class com.naman.rupeeflow.domain.util.CurrencyUtils { *; }

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AndroidX Lifecycle & ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# AndroidX Biometrics & Security
-keep class androidx.biometric.** { *; }

# JSON Serialization / Org.json
-keepclassmembers class org.json.** { *; }

# Remove debug Logcat statements in release builds for privacy hardening
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
