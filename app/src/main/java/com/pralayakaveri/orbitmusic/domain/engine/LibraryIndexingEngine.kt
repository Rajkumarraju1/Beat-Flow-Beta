package com.pralayakaveri.orbitmusic.domain.engine

import com.pralayakaveri.orbitmusic.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

enum class IndexingMode {
    LEGACY,
    SHADOW,
    ACTIVE
}

enum class TriggerReason {
    INITIAL_SCAN,
    PERIODIC_CORRECTIVE,
    OBSERVER_EVENT,
    FOLLOW_UP_COALESCED,
    MANUAL_TRIGGER,
    WATCHDOG_HEARTBEAT
}

enum class IndexingState {
    IDLE,
    SCANNING,
    RECONCILING,
    INDEXING,
    ERROR
}

/**
 * Pluggable module for the Indexing Engine to handle specialized indices
 * (e.g., Search FTS, Galaxy Spatial Clusters).
 */
interface LibraryIndexModule {
    val id: String
    suspend fun onReconciliationComplete(allSongs: List<Song>)
}

/**
 * The core engine responsible for maintaining the relationship between 
 * MediaStore and the local Room database.
 */
interface LibraryIndexingEngine {
    val indexingState: StateFlow<IndexingState>
    val mode: IndexingMode
    val lastDiagnostic: StateFlow<SyncDiagnostic?>
    
    /**
     * Triggers a reconciliation pass.
     * @param reason The cause of this sync pass (for diagnostics).
     * @param forceFullScan If true, bypasses the lightweight "Headless Scan".
     */
    suspend fun startSync(reason: TriggerReason = TriggerReason.MANUAL_TRIGGER, forceFullScan: Boolean = false)
    
    /**
     * Sets the aggregation window for observer events (ms).
     */
    fun setDebounceWindow(ms: Long)

    /**
     * Sets the indexing mode (LEGACY, SHADOW, ACTIVE).
     */
    fun setMode(mode: IndexingMode)
}
