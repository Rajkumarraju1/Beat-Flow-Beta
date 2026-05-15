package com.pralayakaveri.orbitmusic.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle

@Composable
fun SongItem(
    song: Song,
    isFavorite: Boolean,
    displayUri: Any?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val context = LocalContext.current
    
    // Scale animation for favorite button
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "FavoriteScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isCurrent) {
                    Modifier.border(
                        1.2.dp, 
                        Color(0xFF42C6B9).copy(alpha = 0.9f), 
                        RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
            .clickable { onClick() },
        color = Color(0xFF1E1E24).copy(alpha = 0.8f) // Unified Midnight Void background for all cards
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art / Visualizer Container
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E24))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val imageRequest = remember(displayUri) {
                    ImageRequest.Builder(context)
                        .data(displayUri)
                        .size(250)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        MusicVisualizerIcon(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF42C6B9)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title.cleanSongTitle(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Color(0xFF42C6B9) else Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " • ${formatTime(song.duration ?: 0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray.copy(alpha = 0.6f)
                    )
                }
            }

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.scale(favoriteScale)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFF42C6B9) else Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { onOptionsClick(song) }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
