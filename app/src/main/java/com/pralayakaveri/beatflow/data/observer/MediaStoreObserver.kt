package com.pralayakaveri.beatflow.data.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Signal Layer.
 * A thin wrapper around ContentObserver that emits a generic "Dirty" signal.
 * It contains no sync logic and is optimized for storm resistance via its shared flow.
 */
@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Signal a change. Multiple rapid signals are coalesced by the SharedFlow capacity.
            _events.tryEmit(Unit)
        }
    }

    fun start() {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreObserver", "Failed to register observer", e)
        }
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
