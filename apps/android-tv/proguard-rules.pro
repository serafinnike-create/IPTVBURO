# R8 rules for the release build.
#
# AndroidX, Media3, Room, OkHttp and Hilt ship consumer rules that cover their own internals. The
# rules below cover what those cannot see: code this project reaches by reflection.

# Gson resolves model fields by name at runtime, so R8 must not rename or remove them. Without this
# the Xtream and Stalker responses silently deserialise into objects with null fields — a failure
# that appears only in a release build and never in debug, which makes it expensive to diagnose.
-keep class com.lucasserafin94.iptvburo.xtream.** { <fields>; }
-keep class com.lucasserafin94.iptvburo.stalker.** { <fields>; }
-keep class com.lucasserafin94.iptvburo.domain.model.** { <fields>; }

# Generic signatures are how Gson reconstructs List<T> and Map<K, V>.
-keepattributes Signature
-keepattributes *Annotation*

# Room maps columns through generated code that R8 can follow, but the entities are also read
# reflectively by the schema validator during a database upgrade.
-keep class com.lucasserafin94.iptvburo.data.local.entity.** { *; }

# OkHttp references these optional security providers only when they are present at runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers so a crash from a shipped build is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
