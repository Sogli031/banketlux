# ProGuard Rules for Google Drive API
# Apps: ciklus, gymtracker, rad, pare, dug, riba, zaduzenja, kalorije
# Copy these rules to app/proguard-rules.pro when using Google Drive backup with isMinifyEnabled = true

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Google Play Services Auth
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class * extends com.google.api.client.json.GenericJson { *; }
-dontwarn com.google.api.**

# Google HTTP Client
-keep class com.google.http.** { *; }
-dontwarn com.google.http.**

# Gson (used by Google API)
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Apache HTTP Client (transitive dependency)
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
