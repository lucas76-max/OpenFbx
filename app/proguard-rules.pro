# kotlinx.serialization keeps its generated serializers; the app has no reflection-based code.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
