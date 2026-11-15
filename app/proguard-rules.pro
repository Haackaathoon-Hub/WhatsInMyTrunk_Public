# Add project specific ProGuard rules here.

# AAOS CarPropertyManager accessed via reflection in PleosSignalProvider
-dontwarn android.car.**
-keep class android.car.** { *; }

# Pleos Connect SDKs (Vehicle SDK 2.3.2, NaviHelper SDK 2.0.3, SpeechToText SDK 3.3.1)
-dontwarn ai.pleos.**
-keep class ai.pleos.** { *; }

# Offline Korean STT engine (Vosk + JNA native bindings)
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep class org.vosk.** { *; }

# Preserve reflection / Intent payload classes & enums
-keepclassmembers enum com.pleos.vehiclecontext.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
