-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

-keep class coil.** { *; }
-dontwarn coil.**

-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
