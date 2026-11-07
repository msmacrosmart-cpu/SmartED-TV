# ---------------------------------------------------------------------------
# SmartED TV — regras de minificação
# ---------------------------------------------------------------------------

# Kotlin serialization: geradores de serializer
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.smarted.tv.**$$serializer { *; }
-keepclassmembers class com.smarted.tv.** {
    *** Companion;
}
-keepclasseswithmembers class com.smarted.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Modelos de dados do domínio (serializados/refletidos)
-keep class com.smarted.tv.core.model.** { *; }
-keep class com.smarted.tv.addons.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Room
-keep class androidx.room.RoomDatabase { *; }

# Markwon
-dontwarn io.noties.markwon.**
