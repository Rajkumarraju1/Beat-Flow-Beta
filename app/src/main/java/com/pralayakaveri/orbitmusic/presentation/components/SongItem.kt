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
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val borderColor = if (isCurrent()) Color(0xFF42C6B9) else Color.Transparent
    var isAnimating by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { isAnimating = false },
        label = "favorite_scale"
    )
    val context = LocalContext.current

    val imageRequest = remember(displayUri) {
        ImageRequest.Builder(context)
            .data(displayUri)
            .size(120)
            .crossfade(true)
            .build()
    }

    val cleanTitle = remember(song.title) { song.title.cleanSongTitle() }
    val timeDuration = remember(song.duration) { formatTime(song.duration) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = if (isCurrent()) 8.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isCurrent()) Color(0xFF42C6B9) else Color.Black
            )
            .border(if (isCurrent()) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent()) Color(0xFF252530) else Color(0xFF1E1E24)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                )
                if (isCurrent() && isPlaying()) {
                    MusicVisualizerIcon(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent()) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent()) Color(0xFF42C6B9) else Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • $timeDuration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = {
                    isAnimating = true
                    onFavoriteClick()
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFF42C6B9) else Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.scale(scale)
                    )
                }

                IconButton(onClick = { onOptionsClick(song) }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray.copy(alpha = 0.7f)
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
