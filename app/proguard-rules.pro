# General Android
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Room (though mostly replaced by Firebase, keep if still used elsewhere)
-keep class androidx.room.** { *; }

# Models / Entities - Important for Firestore serialization
-keep class com.example.data.firebase.models.** { *; }
-keep class com.example.data.local.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }
