# Morfoboard ProGuard Rules

# Keep IME service (referenced in AndroidManifest)
-keep class com.morfoboard.app.ime.MorfoboardIME { *; }

# Keep activities (referenced in AndroidManifest)
-keep class com.morfoboard.app.MainActivity { *; }
-keep class com.morfoboard.app.settings.SettingsActivity { *; }

# Keep AI models for Gson serialization
-keep class com.morfoboard.app.ai.AIProcessRequest { *; }
-keep class com.morfoboard.app.ai.AIProcessResponse { *; }
-keep class com.morfoboard.app.ai.NineRouterResponse { *; }
-keep class com.morfoboard.app.ai.Choice { *; }
-keep class com.morfoboard.app.ai.Message { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Google Sign-In
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
