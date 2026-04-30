package com.pralayakaveri.beatflow.data.engine

import android.content.Context
import androidx.room.withTransaction
import com.pralayakaveri.beatflow.data.local.*
import com.pralayakaveri.beatflow.data.observer.MediaStoreObserver
import com.pralayakaveri.beatflow.data.provider.MediaStoreProvider
import com.pralayakaveri.beatflow.domain.engine.*
import com.pralayakaveri.beatflow.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryIndexingEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val mediaStoreProvider: MediaStoreProvider,
    private val mediaStoreObserver: MediaStoreObserver,
    private val indexDao: LibraryIndexDao,
    private val librarySongDao: LibrarySongDao,
    private val metadataDao: SongMetadataDao,
    private val favoritesDao: FavoritesDao,
    private val playCountDao: PlayCountDao,
    private val modules: Set<@JvmSuppressWildcards LibraryIndexModule>
) : LibraryIndexingEngine {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _indexingState = MutableStateFlow(IndexingState.IDLE)
    override val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()

    private val _lastDiagnostic = MutableStateFlow<SyncDiagnostic?>(null)
    override val lastDiagnostic: StateFlow<SyncDiagnostic?> = _lastDiagnostic.asStateFlow()

    private var _mode = IndexingMode.ACTIVE 
    override val mode: IndexingMode get() = _mode

    private var _debounceWindowMs = 3000L
    private var isFollowUpPending = false
    private val followUpTrigger = MutableSharedFlow<TriggerReason>(extraBufferCapacity = 1)

    init {
        // Start Signal Observation
        mediaStoreObserver.start()
        
        // Pipeline: Aggregate, Debounce, and Trigger
        engineScope.launch {
            mediaStoreObserver.events
                .debounce { _debounceWindowMs }
                .collect {
                    handleObserverSignal()
                }
        }

        // Follow-up Processor
        engineScope.launch {
            followUpTrigger.collect { reason ->
                startSync(reason)
            }
        }

        // Initial/Corrective Pass
        engineScope.launch {
            startSync(TriggerReason.INITIAL_SCAN)
        }
    }

    override fun setMode(mode: IndexingMode) {
        _mode = mode
    }

    override fun setDebounceWindow(ms: Long) {
        _debounceWindowMs = ms
    }

    private fun handleObserverSignal() {
        if (_indexingState.value == IndexingState.IDLE) {
            engineScope.launch {
                startSync(TriggerReason.OBSERVER_EVENT)
            }
        } else {
            isFollowUpPending = true
            android.util.Log.d("LibraryIndexingEngine", "[PHASE 2] Coalescing observer burst into single follow-up pass.")
        }
    }

    override suspend fun startSync(reason: TriggerReason, forceFullScan: Boolean) = withContext(Dispatchers.IO) {
        if (_mode == IndexingMode.LEGACY) return@withContext
        if (_indexingState.value != IndexingState.IDLE && reason != TriggerReason.FOLLOW_UP_COALESCED) {
             // Already running. If this is a new signal, coalesce it.
             isFollowUpPending = true
             return@withContext
        }

        val startTime = System.currentTimeMillis()
        var added = 0
        var updated = 0
        var removed = 0
        var orphans = 0
        var reparented = 0
        var collisions = 0
        var warnings = 0

        try {
            _indexingState.value = IndexingState.SCANNING
            val mediaStoreHeadless = mediaStoreProvider.getHeadlessSongs()
            
            // PREFLIGHT CHECK: Avoid clearing library if MediaStore is suspiciously empty
            if (mediaStoreHeadless.isEmpty() && forceFullScan) {
                android.util.Log.w("LibraryIndexingEngine", "Preflight ABORT: MediaStore is empty. Not clearing library.")
                _indexingState.value = IndexingState.IDLE
                return@withContext
            }

            _indexingState.value = IndexingState.RECONCILING
            
            try {
                database.withTransaction {
                    if (forceFullScan) {
                        android.util.Log.i("LibraryIndexingEngine", "ForceFullScan triggered: Performing atomic library wipe.")
                        librarySongDao.deleteAll()
                        indexDao.deleteAll()
                    }

                    val existingIndex = if (forceFullScan) emptyMap() else indexDao.getAllIndexEntries().associateBy { it.songId }
                    val existingMirrorIds = if (forceFullScan) emptySet() else librarySongDao.getAllSongsSingle().map { it.id }.toSet()
                    val mediaStoreIds = mediaStoreHeadless.map { it.id }.toSet()
                
                val idsToDeepScan = mutableListOf<Long>()
                
                mediaStoreHeadless.forEach { msHeadless ->
                    val existing = existingIndex[msHeadless.id]
                    val inMirror = existingMirrorIds.contains(msHeadless.id)
                    val primaryHash = generatePrimaryHash(msHeadless.path, msHeadless.size)
                    
                    if (existing == null) {
                        android.util.Log.v("LibraryIndexingEngine", "New song discovered: ${msHeadless.path} (ID: ${msHeadless.id})")
                        val recoveryCandidate = indexDao.findByRecoveryHash(primaryHash)
                        if (recoveryCandidate != null) {
                            if (_mode == IndexingMode.ACTIVE) {
                                recoverOrphan(recoveryCandidate.songId, msHeadless.id, recoveryCandidate, primaryHash)
                                reparented++
                            } else {
                                android.util.Log.d("LibraryIndexingEngine", "[SHADOW_OBSERVER] Would have re-parented: ${recoveryCandidate.songId} -> ${msHeadless.id}")
                                reparented++
                            }
                        } else {
                            idsToDeepScan.add(msHeadless.id)
                        }
                    } else {
                        val needsUpdate = forceFullScan || 
                                         !inMirror || 
                                         (msHeadless.dateModified * 1000 > existing.lastSyncedAt)

                        if (needsUpdate) {
                            android.util.Log.v("LibraryIndexingEngine", "Updating song: ${msHeadless.path} (ID: ${msHeadless.id}, reason: fullScan=$forceFullScan, inMirror=$inMirror)")
                            indexDao.insertOrUpdate(existing.copy(
                                isOrphan = false,
                                recoveryHash = primaryHash,
                                firstSeenMissingAt = null,
                                lastSyncedAt = System.currentTimeMillis()
                            ))
                            idsToDeepScan.add(msHeadless.id)
                            updated++
                        } else if (existing.isOrphan) {
                            android.util.Log.v("LibraryIndexingEngine", "Marking song as non-orphan: ${msHeadless.id}")
                            indexDao.updateOrphanStatus(msHeadless.id, false, null)
                        }
                    }
                }
                
                if (idsToDeepScan.isNotEmpty()) {
                    val deepSongs = mediaStoreProvider.getSongsByIds(idsToDeepScan)
                    deepSongs.forEach { song ->
                        val primaryHash = generatePrimaryHash(song.dataPath, File(song.dataPath).length())
                        val secondaryHash = generateSecondaryHash(song.dataPath, song.duration)
                        
                        if (existingIndex[song.id] == null) {
                            val secondaryCandidate = indexDao.findBySecondaryHash(secondaryHash)
                            if (secondaryCandidate != null) {
                                if (_mode == IndexingMode.ACTIVE) {
                                    recoverOrphan(secondaryCandidate.songId, song.id, secondaryCandidate, primaryHash)
                                    reparented++
                                } else {
                                    android.util.Log.d("LibraryIndexingEngine", "[SHADOW_OBSERVER] Confidence Match: ${secondaryCandidate.songId} -> ${song.id}")
                                    reparented++
                                }
                            } else {
                                insertNewSong(song, primaryHash, secondaryHash)
                                added++
                            }
                        } else {
                            updateSongMirror(song)
                        }
                    }
                }

                val orphansInDb = existingIndex.filter { it.key !in mediaStoreIds && !it.value.isOrphan }
                if (orphansInDb.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    orphansInDb.forEach { (id, index) ->
                        indexDao.insertOrUpdate(index.copy(
                            isOrphan = true,
                            firstSeenMissingAt = index.firstSeenMissingAt ?: now
                        ))
                        orphans++
                    }
                    if (_mode == IndexingMode.ACTIVE) {
                        librarySongDao.deleteSongs(orphansInDb.keys.toList())
                        removed = orphansInDb.size
                    }
                }

                // FAIL-FAST GUARD: If MediaStore has songs, but we inserted NOTHING during a force scan,
                // something is fundamentally broken (DB write error, permissions, etc.)
                if (mediaStoreHeadless.isNotEmpty() && forceFullScan && added == 0) {
                    val errorMsg = "CRITICAL: Force rescan found ${mediaStoreHeadless.size} songs but ADDED 0 to DB. Aborting transaction to prevent data loss."
                    android.util.Log.e("LibraryIndexingEngine", errorMsg)
                    throw IllegalStateException(errorMsg)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("INDEX_METRIC", "rescan_failed processed=${mediaStoreHeadless.size} added=$added reason=${e.message}")
            android.util.Log.e("LibraryIndexingEngine", "Transaction failed (Library preserved)", e)
            _indexingState.value = IndexingState.ERROR
            return@withContext
        }

            _indexingState.value = IndexingState.IDLE
            
            // Notify pluggable indexing modules (FTS, Galaxy, etc.)
            val songs = librarySongDao.getAllSongsWithIndexSingle().map { it.toDomain() }
            modules.forEach { it.onReconciliationComplete(songs) }
            
            val diagnostic = SyncDiagnostic(
                startTime = startTime,
                durationMs = System.currentTimeMillis() - startTime,
                totalProcessed = mediaStoreHeadless.size,
                added = added,
                updated = updated,
                removedFromIndex = removed,
                orphansMarked = orphans,
                reparented = reparented,
                collisionsDetected = collisions,
                confidenceWarnings = warnings,
                mode = _mode
            )
            _lastDiagnostic.value = diagnostic
            android.util.Log.i("LibraryIndexingEngine", "[PHASE 2] Sync Complete (Reason: $reason) - $diagnostic")

        } catch (e: Exception) {
            android.util.Log.e("LibraryIndexingEngine", "Sync pass failed", e)
            _indexingState.value = IndexingState.ERROR
        } finally {
            // Follow-up policy
            if (isFollowUpPending) {
                isFollowUpPending = false
                followUpTrigger.tryEmit(TriggerReason.FOLLOW_UP_COALESCED)
            }
        }
    }

    private suspend fun insertNewSong(song: Song, primary: String, secondary: String) {
        android.util.Log.d("LibraryIndexingEngine", "Inserting NEW song: ${song.title} (ID: ${song.id})")
        indexDao.insertOrUpdate(
            LibraryIndexEntity(
                songId = song.id,
                isOrphan = false,
                recoveryHash = primary,
                secondaryHash = secondary,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
        updateSongMirror(song)
    }
 
    private suspend fun updateSongMirror(song: Song) {
        android.util.Log.v("LibraryIndexingEngine", "Mirroring song to DB: ${song.title} (ID: ${song.id})")
        librarySongDao.insertSongs(listOf(
            LibrarySongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                artistId = song.artistId,
                album = song.album,
                albumId = song.albumId,
                duration = song.duration,
                dataPath = song.dataPath,
                trackNumber = song.trackNumber,
                uriString = song.uri.toString(),
                albumArtUriString = song.albumArtUri?.toString()
            )
        ))
    }

    private suspend fun recoverOrphan(oldId: Long, newId: Long, orphan: LibraryIndexEntity, primaryHash: String) {
        if (favoritesDao.isFavoriteSingle(oldId)) {
            favoritesDao.addFavorite(FavoriteSongEntity(newId, System.currentTimeMillis()))
            favoritesDao.removeFavorite(FavoriteSongEntity(oldId, 0))
        }
        playCountDao.getPlayCountById(oldId)?.let { pc ->
            playCountDao.insertOrUpdate(pc.copy(songId = newId))
        }
        metadataDao.getMetadataById(oldId)?.let { meta ->
            metadataDao.insertOrUpdate(meta.copy(songId = newId))
            metadataDao.deleteMetadata(oldId)
        }
        indexDao.deleteEntry(oldId)
        indexDao.insertOrUpdate(orphan.copy(
            songId = newId,
            isOrphan = false,
            recoveryHash = primaryHash,
            firstSeenMissingAt = null,
            lastSyncedAt = System.currentTimeMillis()
        ))
    }

    private fun generatePrimaryHash(path: String, size: Long): String {
        return "${path}_$size"
    }

    private fun generateSecondaryHash(path: String, duration: Long): String {
        val filename = File(path).name
        return "${filename}_$duration"
    }
}
