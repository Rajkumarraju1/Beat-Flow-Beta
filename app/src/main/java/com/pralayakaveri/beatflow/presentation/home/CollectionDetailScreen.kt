package com.pralayakaveri.beatflow.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pralayakaveri.beatflow.domain.model.Song
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Edit
import coil.compose.AsyncImage
import com.pralayakaveri.beatflow.presentation.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    mainViewModel: com.pralayakaveri.beatflow.presentation.main.MainViewModel,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val title by mainViewModel.selectedCollectionTitle.collectAsState()
    val songs by mainViewModel.selectedCollectionSongs.collectAsState()
    val type by mainViewModel.selectedCollectionType.collectAsState()
    val currentSongState = mainViewModel.currentSong.collectAsState()
    val isPlayingState = mainViewModel.isPlaying.collectAsState()
    val artistImages by mainViewModel.artistImages.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val customArtworks by mainViewModel.customArtworks.collectAsState()

    val currentArtistImage = remember(title, artistImages) { artistImages[title] }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            if (type == "Artist") {
                mainViewModel.saveArtistImage(title, it.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (type == "Artist") {
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Image",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1E1E24)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Premium blurred background for Artists
            if (type == "Artist" && currentArtistImage != null) {
                AsyncImage(
                    model = currentArtistImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .blur(50.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSongClick(songs, 0) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42C6B9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All")
                        }
                        OutlinedButton(
                            onClick = { onSongClick(songs.shuffled(), 0) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.Gray.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Shuffle,
                                contentDescription = null,
                                tint = Color(0xFF42C6B9)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", color = Color.White)
                        }
                    }
                }

                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongItem(
                        song = song,
                        isFavorite = favoriteIds.contains(song.id),
                        displayUri = customArtworks[song.id] ?: song.albumArtUri,
                        isCurrent = { song.id == currentSongState.value?.id },
                        isPlaying = { isPlayingState.value },
                        onClick = { onSongClick(songs, index) },
                        onFavoriteClick = { mainViewModel.toggleFavorite(targetSongId = song.id) },
                        onOptionsClick = onOptionsClick
                    )
                }
            }
        }
    }
}
