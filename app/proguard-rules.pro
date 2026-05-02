# --- Hilt / Dagger Hardening ---
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

-keep class * {
    @dagger.assisted.AssistedInject <init>(...);
}

-keep class * {
    @dagger.multibindings.IntoMap *;
}

# --- WorkManager Hardening ---
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }

-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- App Package Hardening ---
-keep class com.pralayakaveri.orbitmusic.** { *; }
-keepclassmembers class com.pralayakaveri.orbitmusic.** { *; }

# --- Room Hardening ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# --- Hilt Worker & Assisted Inject Hardening (STRICT) ---
-keep class com.pralayakaveri.orbitmusic.**.HiltWrapper_* { *; }
-keep class com.pralayakaveri.orbitmusic.**.*_AssistedFactory { *; }

# Preserve logs in release builds for verification
-keepclassmembers class android.util.Log {
    public static *** i(...);
    public static *** d(...);
    public static *** e(...);
}

-dontwarn androidx.media3.**
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn dagger.hilt.android.internal.managers.ViewComponentManager
-dontwarn androidx.hilt.work.**

# --- JAudioTagger (Avoids Missing Class Errors for Desktop APIs) ---
-dontwarn javax.swing.**
-dontwarn java.awt.**
-dontwarn org.jaudiotagger.test.**