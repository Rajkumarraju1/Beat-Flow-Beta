# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

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
-keep @androidx.room.RawQuery class * { *; }

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# --- Data Models (Reflection / Serialization) ---
# Protect Domain models and Room Entities used in Galaxy/Search
-keep class com.pralayakaveri.beatflow.domain.model.** { *; }
-keep class com.pralayakaveri.beatflow.data.local.** { *; }
-keep class com.pralayakaveri.beatflow.presentation.galaxy.GalaxyNode { *; }
-keep enum com.pralayakaveri.beatflow.presentation.galaxy.NodeType { *; }

# --- Kotlin Serialization / Coroutines ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext$ScheduledAt {
    long nanos;
}

# --- Coil ---
-keep class coil.** { *; }

# --- Kotlin Reflect ---
-keep class kotlin.reflect.jvm.internal.** { *; }

# --- General Hardening ---
-dontwarn androidx.media3.**
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**

# --- Explicit App Logic Keep Rules ---
-keep class com.pralayakaveri.beatflow.data.worker.MetadataWorker { *; }
-keep class com.pralayakaveri.beatflow.service.MusicController { *; }
-keep class com.pralayakaveri.beatflow.presentation.main.MainViewModel { *; }