# kotlinx.serialization keeps generated serializers reachable through companions.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.elshal.vitalyn.**$$serializer { *; }
-keepclassmembers class com.elshal.vitalyn.** { *** Companion; }
-keepclasseswithmembers class com.elshal.vitalyn.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Body mesh resource is loaded by name from the core jar.
-keep class com.elshal.vitalyn.core.BodyModel { *; }
