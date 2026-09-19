# kotlinx.serialization: keep serializers of our models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.jellyschedule.tv.**$$serializer { *; }
-keepclassmembers class dev.jellyschedule.tv.** { *** Companion; }
-keepclasseswithmembers class dev.jellyschedule.tv.** { kotlinx.serialization.KSerializer serializer(...); }

# Jellyfin SDK models are serialized with kotlinx.serialization as well.
-keep,includedescriptorclasses class org.jellyfin.sdk.model.**$$serializer { *; }
-keepclassmembers class org.jellyfin.sdk.model.** { *** Companion; }
-keepclasseswithmembers class org.jellyfin.sdk.model.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Media3 ship their own consumer rules; nothing to add.

# SLF4J is loaded reflectively (ServiceLoader) by kotlin-logging; keep the API and the Android provider.
-keep class org.slf4j.** { *; }
-keep class uk.uuid.slf4j.android.** { *; }
-dontwarn org.slf4j.**
