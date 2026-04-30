# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# --- Hilt Work ---
-keep class androidx.hilt.work.** { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# --- App Logic Hardening (CRITICAL FIX) ---

# Use broad wildcards to handle package name variations (beatflow vs orbitmusic)
-keep class com.pralayakaveri.**.MetadataWorker { *; }
-keep class com.pralayakaveri.**.MusicController { *; }
-keepclassmembers class com.pralayakaveri.**.MusicController { *; }
-keep class com.pralayakaveri.**.MediaModule { *; }
-keepclassmembers class com.pralayakaveri.**.MediaModule { *; }
-keep class com.pralayakaveri.**.MainViewModel { *; }
-keepclassmembers class com.pralayakaveri.**.MainViewModel { *; }

# Keep all models and local data classes
-keep class com.pralayakaveri.**.domain.model.** { *; }
-keep class com.pralayakaveri.**.data.local.** { *; }

# Prevent stripping of the worker constructor
-keepclassmembers class com.pralayakaveri.**.MetadataWorker {
    public <init>(...);
}

# General
-dontwarn androidx.media3.**
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn dagger.hilt.android.internal.managers.ViewComponentManager