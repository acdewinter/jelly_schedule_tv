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

# Optional logging backends referenced by the Jellyfin SDK's kotlin-logging but not shipped.
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.slf4j.Marker
-dontwarn org.slf4j.MarkerFactory
-dontwarn org.slf4j.event.Level
-dontwarn org.slf4j.spi.CallerBoundaryAware
-dontwarn org.slf4j.spi.LocationAwareLogger
-dontwarn org.slf4j.spi.LoggingEventBuilder
