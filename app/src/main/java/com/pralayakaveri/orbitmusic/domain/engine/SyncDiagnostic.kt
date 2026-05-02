package com.pralayakaveri.orbitmusic.domain.engine

/**
 * Detailed diagnostic report for a single indexing/sync pass.
 */
data class SyncDiagnostic(
    val startTime: Long,
    val durationMs: Long,
    val totalProcessed: Int,
    val added: Int,
    val updated: Int,
    val removedFromIndex: Int,
    val orphansMarked: Int,
    val reparented: Int,
    val collisionsDetected: Int,
    val confidenceWarnings: Int,
    val mode: IndexingMode
) {
    override fun toString(): String {
        return """
            [Sync Report - $mode]
            Duration: ${durationMs}ms
            Processed: $totalProcessed
            Added: $added | Updated: $updated
            Orphans: $orphansMarked | Removed: $removedFromIndex
            Re-parented: $reparented
            Issues: Collisions($collisionsDetected), Warnings($confidenceWarnings)
        """.trimIndent()
    }
}
