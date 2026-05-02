package com.pralayakaveri.orbitmusic.presentation.util

data class ScrollResetSignal(
    val id: Long = System.currentTimeMillis(),
    val reason: Reason
) {
    enum class Reason {
        SORT,
        SEARCH,
        TAB_SWITCH // Potential future use
    }
}
