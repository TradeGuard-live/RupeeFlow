# Proguard rules for RupeeFlow
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
