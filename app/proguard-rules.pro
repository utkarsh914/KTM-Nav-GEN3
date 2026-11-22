# R8 runs on release and benchmark builds: shrink and optimise, never obfuscate (readable crash logs in
# Settings > Logs; a sideloaded app gains little from renaming). Turn R8 off with minifyRelease=false in local.properties.
-dontobfuscate

# Keep file names and line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Type-safe navigation routes (@Serializable) and every other serializable class: the serializers are looked up
# through the companion at run time (kotlinx.serialization ships most of these rules; stated here so the routes
# and the backup/ride formats cannot be broken by a library update).
-keepclassmembers class com.navigator.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.navigator.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.navigator.app.**$$serializer { *; }
# Whole classes, not just their serializers: navigation matches routes by class and serializer.
-keep @kotlinx.serialization.Serializable class com.navigator.app.** { *; }

# On-device maneuver classifier: LiteRT / TensorFlow Lite call into Java from native code.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# WorkManager (a transitive dependency, started by androidx.startup) builds its Room database by reflection. Room 2.5's
# own rule keeps the class but, in R8 full mode, not its constructor: the app then dies at launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Navigation SDK and Maps: the AARs carry their own consumer rules; nothing is added here on purpose.
