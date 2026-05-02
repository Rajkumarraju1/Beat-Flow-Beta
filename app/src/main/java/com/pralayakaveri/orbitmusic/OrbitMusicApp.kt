package com.pralayakaveri.orbitmusic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OrbitMusicApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("orbit_prefs", android.content.Context.MODE_PRIVATE)
        // We use a new key because the OS backup restored the old "first_purge_done" as true!
        if (!prefs.getBoolean("workmanager_beatflow_purged_v2", false)) {
            try {
                // Forcefully delete physical WorkManager DB files before initialization
                // to prevent it from ever trying to instantiate stale 'beatflow' workers.
                val db1 = getDatabasePath("androidx.work.workdb")
                val db2 = getDatabasePath("androidx.work.workdb-shm")
                val db3 = getDatabasePath("androidx.work.workdb-wal")
                if (db1.exists()) db1.delete()
                if (db2.exists()) db2.delete()
                if (db3.exists()) db3.delete()

                try {
                    androidx.work.WorkManager.getInstance(this).cancelAllWork()
                } catch (e: Exception) {
                    android.util.Log.w("OrbitMusicApp", "WorkManager not ready yet, skipping cancelAllWork")
                }
                prefs.edit().putBoolean("workmanager_beatflow_purged_v2", true).apply()
                android.util.Log.i("OrbitMusicApp", "One-time WorkManager DB physical purge v2 successful.")
            } catch (e: Exception) {
                android.util.Log.e("OrbitMusicApp", "WorkManager physical purge failed", e)
            }
        }
        android.util.Log.i("OrbitMusicApp", "OrbitMusicApp onCreate started")
    }
}
