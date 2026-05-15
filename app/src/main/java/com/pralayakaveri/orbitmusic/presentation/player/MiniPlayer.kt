package com.pralayakaveri.orbitmusic.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.shadow
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    song: Song,
    customArtworkUri: String? = null,
    isPlaying: Boolean,
    currentPosition: Long,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    enabled: Boolean = true
) {
    var offsetX by remember { mutableStateOf(0f) }
    val progress = if (song.duration > 0) currentPosition.toFloat() / song.duration else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp) // Sleeker height
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150f) {
                            onPrevious()
                        } else if (offsetX < -150f) {
                            onNext()
                        }
                        offsetX = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            }
            .clickable(enabled = enabled) { onExpand() },
        color = Color(0xFF1E1E24).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mini Progress Line (Hairline Bottom)
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
            ) {
                drawLine(
                    color = Color(0xFF42C6B9), // Restored original teal for progress
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width * progress, 0f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art (Rounded)
                AsyncImage(
                    model = customArtworkUri ?: song.albumArtUri,
                    contentDescription = "Album Art",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Metadata (Centered vertically)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title.cleanSongTitle(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = Color.Gray,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Play/Pause Button (Circular with glow)
                IconButton(
                    onClick = onPlayPause,
                    enabled = enabled,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
