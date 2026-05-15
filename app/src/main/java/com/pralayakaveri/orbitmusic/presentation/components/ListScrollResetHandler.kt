package com.pralayakaveri.orbitmusic.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal

/**
 * Production-Grade Resilient Reset Handler.
 * 
 * This handler implements a multi-stage synchronization model to ensure deterministic 
 * scroll resets during heavy UI-thread load (confirmed by Logcat Davey frame stalls).
 */
@Composable
fun ListScrollResetHandler(
    listState: LazyListState,
    dataSignature: List<Long>,
    signal: ScrollResetSignal?,
    threshold: Int = 15
) {
    HandleReset(
        firstVisibleItemIndex = { listState.firstVisibleItemIndex },
        firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
        totalItemsCount = { listState.layoutInfo.totalItemsCount },
        visibleItemsIds = { listState.layoutInfo.visibleItemsInfo.map { it.key as? Long ?: -1L } },
        isViewportMeasured = { listState.layoutInfo.visibleItemsInfo.isNotEmpty() },
        scrollToItem = { listState.scrollToItem(0) },
        dataSignature = dataSignature,
        signal = signal
    )
}

@Composable
fun GridScrollResetHandler(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    dataSignature: List<Long>,
    signal: ScrollResetSignal?,
    threshold: Int = 15
) {
    HandleReset(
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        visibleItemsIds = { gridState.layoutInfo.visibleItemsInfo.map { it.key as? Long ?: -1L } },
        isViewportMeasured = { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() },
        scrollToItem = { gridState.scrollToItem(0) },
        dataSignature = dataSignature,
        signal = signal
    )
}

@Composable
private fun HandleReset(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
    totalItemsCount: () -> Int,
    visibleItemsIds: () -> List<Long>,
    isViewportMeasured: () -> Boolean,
    scrollToItem: suspend () -> Unit,
    dataSignature: List<Long>,
    signal: ScrollResetSignal?
) {
    var lastHandledSignalId by remember { mutableStateOf(-1L) }
    var isFirstComposition by remember { mutableStateOf(true) }
    val view = LocalView.current

    // STAGE 1: SIGNAL Gating (Auto-Cancellation via LaunchedEffect)
    LaunchedEffect(signal?.id, dataSignature) {
        val s = signal ?: return@LaunchedEffect
        val expectedFirstId = dataSignature.firstOrNull() ?: return@LaunchedEffect
        
        // 1. Process Restoration Guard
        if (isFirstComposition) {
            isFirstComposition = false
            lastHandledSignalId = s.id
            return@LaunchedEffect
        }

        // 2. One-Shot Idempotency Guard
        if (s.id == lastHandledSignalId) return@LaunchedEffect
        
        // STAGE 2: Viewport Identity Confirmation (The "Davey!" Fix)
        // We wait up to 500ms for the expected item to actually mount in the viewport.
        val syncSuccess = withTimeoutOrNull(500L) {
            snapshotFlow { visibleItemsIds() }
                .filter { ids -> ids.contains(expectedFirstId) }
                .first()
        } != null

        // STAGE 3: Fallback & Context Verification
        // If timeout occurred, we ONLY force reset if the layout is non-empty.
        val shouldExecute = if (syncSuccess) {
            true
        } else {
            // Fallback: Verify we have actual data and a measured viewport
            totalItemsCount() > 0 && isViewportMeasured()
        }

        if (shouldExecute) {
            // STAGE 4: One-Shot Mark
            lastHandledSignalId = s.id
            
            // STAGE 5: Execution
            val index = firstVisibleItemIndex()
            val offset = firstVisibleItemScrollOffset()

            if (index > 0 || offset > 0) {
                scrollToItem()
                view.announceForAccessibility("List updated. Showing latest items.")
            }
        }
    }
}
