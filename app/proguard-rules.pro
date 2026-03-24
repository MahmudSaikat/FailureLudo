# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep game engine data classes (for potential serialization later)
-keep class com.failureludo.engine.** { *; }

# Kotlin Serialization (for future online mode)
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** *; }
