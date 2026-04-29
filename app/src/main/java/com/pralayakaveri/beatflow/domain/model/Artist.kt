package com.pralayakaveri.beatflow.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
