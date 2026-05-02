package com.pralayakaveri.orbitmusic.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal

@Composable
fun ListScrollResetHandler(
    listState: LazyListState,
    itemCount: Int,
    signal: ScrollResetSignal?,
    threshold: Int = 15
) {
    HandleReset(
        firstVisibleItemIndex = { listState.firstVisibleItemIndex },
        firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
        totalItemsCount = { listState.layoutInfo.totalItemsCount },
        expectedCount = itemCount,
        scrollToItem = { listState.scrollToItem(0) },
        animateScrollToItem = { listState.animateScrollToItem(0) },
        signal = signal,
        threshold = threshold
    )
}

@Composable
fun GridScrollResetHandler(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    itemCount: Int,
    signal: ScrollResetSignal?,
    threshold: Int = 15
) {
    HandleReset(
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        expectedCount = itemCount,
        scrollToItem = { gridState.scrollToItem(0) },
        animateScrollToItem = { gridState.animateScrollToItem(0) },
        signal = signal,
        threshold = threshold
    )
}

@Composable
private fun HandleReset(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
    totalItemsCount: () -> Int,
    expectedCount: Int,
    scrollToItem: suspend () -> Unit,
    animateScrollToItem: suspend () -> Unit,
    signal: ScrollResetSignal?,
    threshold: Int
) {
    var lastHandledId by remember { mutableStateOf(-1L) }
    var isFirstComposition by remember { mutableStateOf(true) }
    val view = LocalView.current

    LaunchedEffect(signal?.id) {
        val s = signal ?: return@LaunchedEffect
        
        if (isFirstComposition) {
            isFirstComposition = false
            lastHandledId = s.id
            return@LaunchedEffect
        }

        if (s.id == lastHandledId) return@LaunchedEffect
        lastHandledId = s.id
        
        // Phase 1: Wait for Dataset Sync (up to 500ms)
        // Critical for Search/Filter where item count changes
        val isSynced = withTimeoutOrNull(500L) {
            snapshotFlow { totalItemsCount() }.first { it == expectedCount }
        } != null
        
        if (!isSynced) return@LaunchedEffect
        if (expectedCount == 0) return@LaunchedEffect
        
        // Phase 2: Frame Sync (Crucial for Sort where count is identical)
        // Ensures layout offsets are recalculated before we read them
        withFrameNanos { }

        // Phase 3: Stabilization Delay (only for heavy lists)
        if (expectedCount > 200) {
            delay(50)
        }

        // Phase 4: Execution
        val index = firstVisibleItemIndex()
        val offset = firstVisibleItemScrollOffset()

        if (index == 0 && offset == 0) return@LaunchedEffect

        if (index > threshold) {
            scrollToItem()
        } else {
            animateScrollToItem()
        }

        // Accessibility announcement
        view.announceForAccessibility("List updated. Showing latest items.")
    }
}
