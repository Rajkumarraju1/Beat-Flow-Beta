package com.pralayakaveri.beatflow.presentation.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.presentation.home.PlaylistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music Insights") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        "Your Top Genres",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    InsightChart(state.topGenres)
                    Spacer(Modifier.height(32.dp))
                }

                item {
                    Text(
                        "Top Artists",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    InsightChart(state.topArtists)
                    Spacer(Modifier.height(32.dp))
                }

                if (state.forgottenSongs.isNotEmpty()) {
                    item {
                        DiscoverySection(
                            title = "Forgotten Songs",
                            subtitle = "Rediscover old favorites",
                            songs = state.forgottenSongs,
                            onPlaySong = onPlaySong
                        )
                    }
                }

                if (state.hiddenGems.isNotEmpty()) {
                    item {
                        DiscoverySection(
                            title = "Hidden Gems",
                            subtitle = "Songs you love but rarely play",
                            songs = state.hiddenGems,
                            onPlaySong = onPlaySong
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InsightChart(data: Map<String, Int>) {
    val maxCount = data.values.maxOrNull() ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.forEach { (label, count) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$count plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(count.toFloat() / maxCount)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun DiscoverySection(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onPlaySong: (Song) -> Unit
) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(songs) { _, song ->
                PlaylistCard(song = song, onClick = { onPlaySong(song) })
            }
        }
    }
}
