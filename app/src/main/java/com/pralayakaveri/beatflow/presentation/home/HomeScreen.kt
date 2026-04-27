package com.pralayakaveri.beatflow.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.pralayakaveri.beatflow.domain.model.Song
import com.pralayakaveri.beatflow.domain.util.cleanSongTitle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.pralayakaveri.beatflow.presentation.components.SkeletonSongItem
import com.pralayakaveri.beatflow.presentation.components.MusicVisualizerIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: com.pralayakaveri.beatflow.presentation.main.MainViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
    onSongClick: (List<Song>, Int) -> Unit,
    onCollectionClick: (String, List<Song>, String) -> Unit,
    onGalaxyClick: () -> Unit,
    onInsightsClick: () -> Unit
) {
    val searchQuery by mainViewModel.searchQuery.collectAsState()
    val songs by viewModel.getFilteredSongs(mainViewModel.searchQuery).collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val folders by viewModel.folders.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    val workoutPlaylist by viewModel.workoutPlaylist.collectAsState()
    val chillPlaylist by viewModel.chillPlaylist.collectAsState()
    val focusPlaylist by viewModel.focusPlaylist.collectAsState()
    val drivingPlaylist by viewModel.drivingPlaylist.collectAsState()

    val currentSong by mainViewModel.currentSong.collectAsState()

    val tabs = listOf("Songs", "Playlists", "Albums", "Artists", "Folders", "Favorites")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })
    val selectedTabIndex = pagerState.currentPage

    var isHeaderVisible by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            true
        )
    }
    val nestedScrollConnection = androidx.compose.runtime.remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -15f) {
                    isHeaderVisible = false
                } else if (available.y > 15f) {
                    isHeaderVisible = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    val isFavorite by (selectedSongForOptions?.let { mainViewModel.isFavorite(it.id) }
        ?: MutableStateFlow(false)).collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val selectedCollectionTitle by mainViewModel.selectedCollectionTitle.collectAsState()

    BackHandler(enabled = selectedCollectionTitle.isNotEmpty()) {
        mainViewModel.clearSelectedCollection()
    }

    var showPlaylistSheet by remember { mutableStateOf(false) }

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            mainViewModel.invalidateSongCache()
            viewModel.loadData()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Song deleted securely",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    if (showPlaylistSheet && selectedSongForOptions != null) {
        val playlists by mainViewModel.playlists.collectAsState()
        com.pralayakaveri.beatflow.presentation.components.AddToPlaylistBottomSheet(
            song = selectedSongForOptions!!,
            playlists = playlists,
            onCreatePlaylist = { name ->
                mainViewModel.createPlaylist(name)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Created playlist '$name'")
                }
            },
            onAddToPlaylist = { playlist ->
                mainViewModel.addSongToPlaylist(playlist.id, selectedSongForOptions!!.id)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Added to ${playlist.name}")
                }
                showPlaylistSheet = false
                selectedSongForOptions = null
            },
            onDismiss = { showPlaylistSheet = false }
        )
    }

    if (selectedSongForOptions != null) {
        val customArtworks by mainViewModel.customArtworks.collectAsState()
        com.pralayakaveri.beatflow.presentation.components.SongOptionsMenu(
            song = selectedSongForOptions!!,
            customArtworkUri = customArtworks[selectedSongForOptions!!.id],
            isFavorite = isFavorite,
            onFavoriteClick = { mainViewModel.toggleFavorite(selectedSongForOptions!!) },
            onPlaylistClick = { showPlaylistSheet = true },
            onShareClick = {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, selectedSongForOptions!!.uri)
                    type = "audio/*"
                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        "Share Song"
                    )
                )
                selectedSongForOptions = null
            },
            onDeleteClick = {
                try {
                    val uri = selectedSongForOptions!!.uri
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    )
                    deleteLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(
                            pendingIntent
                        ).build()
                    )
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error launching delete request", e)
                }
                selectedSongForOptions = null
            },
            onDismiss = { selectedSongForOptions = null }
        )
    }

    AnimatedContent(
        targetState = selectedCollectionTitle,
        transitionSpec = {
            if (targetState.isNotEmpty()) {
                slideInHorizontally { width -> width }.togetherWith(slideOutHorizontally { width -> -width / 2 })
            } else {
                slideInHorizontally { width -> -width / 2 }.togetherWith(slideOutHorizontally { width -> width })
            }
        },
        label = "CollectionNavigation"
    ) { targetTitle ->
        if (targetTitle.isNotEmpty()) {
            CollectionDetailScreen(
                mainViewModel = mainViewModel,
                onBack = { mainViewModel.clearSelectedCollection() },
                onSongClick = onSongClick,
                onOptionsClick = { song ->
                    selectedSongForOptions = song
                }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.pralayakaveri.beatflow.R.drawable.ic_logo_orbit),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Library",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                )
                            }
                        },
                        actions = {
                            var expanded by androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(
                                    false
                                )
                            }
                            IconButton(onClick = onGalaxyClick) {
                                Icon(
                                    imageVector = Icons.Default.AutoGraph,
                                    contentDescription = "Galaxy Mode",
                                    tint = Color.Gray
                                )
                            }
                            IconButton(onClick = onInsightsClick) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Music Insights",
                                    tint = Color.Gray
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        )
                    )
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    com.pralayakaveri.beatflow.presentation.components.AppBackground()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .nestedScroll(nestedScrollConnection)
                    ) {

                        androidx.compose.material3.ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            edgePadding = 16.dp,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF42C6B9),
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = Color(0xFF42C6B9),
                                        height = 3.dp
                                    )
                                }
                            },
                            divider = {
                                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { 
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = title,
                                            color = if (selectedTabIndex == index) Color(0xFF42C6B9) else Color.Gray,
                                            fontWeight = if (selectedTabIndex == index) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isHeaderVisible,
                            enter = expandVertically(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300))
                        ) {
                            Column {
                                FloatingSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { mainViewModel.onSearchQueryChange(it) }
                                )

                                QuickActionRow(
                                    onPlayAllClick = {
                                        if (songs.isNotEmpty()) onSongClick(
                                            songs,
                                            0
                                        )
                                    },
                                    onShuffleClick = {
                                        if (songs.isNotEmpty()) onSongClick(
                                            songs.shuffled(),
                                            0
                                        )
                                    },
                                    onFavoritesClick = { selectedTabIndex = 5 },
                                    onRecentlyPlayedClick = { selectedTabIndex = 1 }
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1
                            ) { pageIndex ->
                                when (pageIndex) {
                                    0 -> {
                                        if (isLoading) {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(10) { SkeletonSongItem() }
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                itemsIndexed(
                                                    songs,
                                                    key = { _, song -> song.id }) { index, song ->
                                                    SongItem(
                                                        song = song,
                                                        isCurrent = song.id == currentSong?.id,
                                                        isPlaying = isPlaying,
                                                        mainViewModel = mainViewModel,
                                                        onClick = { onSongClick(songs, index) },
                                                        onOptionsClick = { selectedSongForOptions = it }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    1 -> {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 16.dp)
                                        ) {
                                            item {
                                                PlaylistSection(
                                                    title = "Recently Added",
                                                    songs = recentlyAdded,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                            item {
                                                PlaylistSection(
                                                    title = "Most Played",
                                                    songs = mostPlayed,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                            item {
                                                PlaylistSection(
                                                    title = "Recently Played",
                                                    songs = recentlyPlayed,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }

                                            item {
                                                PlaylistSection(
                                                    title = "Workout",
                                                    songs = workoutPlaylist,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                            item {
                                                PlaylistSection(
                                                    title = "Chill Out",
                                                    songs = chillPlaylist,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                            item {
                                                PlaylistSection(
                                                    title = "Deep Focus",
                                                    songs = focusPlaylist,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                            item {
                                                PlaylistSection(
                                                    title = "Driving",
                                                    songs = drivingPlaylist,
                                                    onSongClick = { s, i -> onSongClick(s, i) })
                                            }
                                        }
                                    }

                                    2 -> {
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(
                                                2
                                            ),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(8.dp)
                                        ) {
                                            items(
                                                items = albums,
                                                key = { album -> album.id }
                                            ) { album ->
                                                val albumSongs =
                                                    songs.filter { it.albumId == album.id }
                                                AlbumItem(
                                                    album = album,
                                                    onClick = {
                                                        onCollectionClick(
                                                            album.title,
                                                            albumSongs,
                                                            "Album"
                                                        )
                                                    })
                                            }
                                        }
                                    }

                                    3 -> {
                                        val artistImages by mainViewModel.artistImages.collectAsState()
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(
                                                2
                                            ),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(8.dp)
                                        ) {
                                            items(
                                                items = artists,
                                                key = { artist -> artist.id.toString() + artist.name }
                                            ) { artist ->
                                                val artistSongs =
                                                    songs.filter { it.artist == artist.name }
                                                ArtistItem(
                                                    artist = artist,
                                                    customImageUri = artistImages[artist.name],
                                                    onClick = {
                                                        onCollectionClick(
                                                            artist.name,
                                                            artistSongs,
                                                            "Artist"
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    4 -> {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            itemsIndexed(
                                                folders.keys.toList(),
                                                key = { _, folderPath -> folderPath }) { _, folderPath ->
                                                val folderName = folderPath.substringAfterLast('/')
                                                val folderSongs = folders[folderPath] ?: emptyList()
                                                FolderItem(
                                                    folderName = folderName,
                                                    folderPath = folderPath,
                                                    songCount = folderSongs.size,
                                                    onClick = {
                                                        onCollectionClick(
                                                            folderName,
                                                            folderSongs,
                                                            "Folder"
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    5 -> {
                                        if (favorites.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.pralayakaveri.beatflow.presentation.components.EmptyState(
                                                    message = "No favorites yet",
                                                    icon = Icons.Default.FavoriteBorder
                                                )
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                itemsIndexed(
                                                    favorites,
                                                    key = { _, song -> song.id }) { index, song ->
                                                    SongItem(
                                                        song = song,
                                                        isCurrent = song.id == currentSong?.id,
                                                        isPlaying = isPlaying,
                                                        mainViewModel = mainViewModel,
                                                        onClick = { onSongClick(favorites, index) },
                                                        onOptionsClick = {
                                                            selectedSongForOptions = it
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistSection(title: String, songs: List<Song>, onSongClick: (List<Song>, Int) -> Unit) {
    if (songs.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    PlaylistCard(song = song, onClick = { onSongClick(songs, index) })
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        val context = LocalContext.current
        val imageRequest = remember(song.albumArtUri) {
            ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .size(250)
                .crossfade(true)
                .build()
        }
        coil.compose.AsyncImage(
            model = imageRequest,
            contentDescription = "Album Art",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title.cleanSongTitle(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumItem(album: com.pralayakaveri.beatflow.domain.model.Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        val context = LocalContext.current
        val imageRequest = remember(album.albumArtUri) {
            ImageRequest.Builder(context)
                .data(album.albumArtUri)
                .size(400)
                .crossfade(true)
                .build()
        }
        coil.compose.AsyncImage(
            model = imageRequest,
            contentDescription = "Album Art",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title.cleanSongTitle(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistItem(
    artist: com.pralayakaveri.beatflow.domain.model.Artist,
    customImageUri: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (customImageUri != null) {
                coil.compose.AsyncImage(
                    model = customImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "${artist.songCount} songs • ${artist.albumCount} albums",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    mainViewModel: com.pralayakaveri.beatflow.presentation.main.MainViewModel,
    onClick: () -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val borderColor = if (isCurrent) Color(0xFF42C6B9) else Color.Transparent
    val isFavorite by mainViewModel.isFavorite(song.id).collectAsState(initial = false)
    var isAnimating by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { isAnimating = false }
    )
    val context = LocalContext.current
    val customArtworks by mainViewModel.customArtworks.collectAsState()
    val displayUri = customArtworks[song.id] ?: song.albumArtUri

    val imageRequest = remember(displayUri) {
        ImageRequest.Builder(context)
            .data(displayUri)
            .size(150)
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
                elevation = if (isCurrent) 8.dp else 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isCurrent) Color(0xFF42C6B9) else Color.Black
            )
            .border(if (isCurrent) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFF252530) else Color(0xFF1E1E24)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                coil.compose.AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album Art",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                )
                if (isCurrent && isPlaying) {
                    MusicVisualizerIcon(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                    ),
                    color = if (isCurrent) Color(0xFF42C6B9) else Color.White.copy(alpha = 0.9f),
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
                    mainViewModel.toggleFavorite(song)
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
                        imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


@Composable
fun FloatingSearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), CircleShape)
            .clip(CircleShape),
        placeholder = { Text("Search your music...", color = Color.Gray) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.Gray
            )
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1E1E24),
            unfocusedContainerColor = Color(0xFF1E1E24),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = Color(0xFF42C6B9),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

@Composable
fun QuickActionRow(
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onRecentlyPlayedClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            QuickActionButton(
                icon = Icons.Rounded.PlayArrow,
                text = "Play All",
                onClick = onPlayAllClick
            )
        }
        item {
            QuickActionButton(
                icon = Icons.Rounded.Shuffle,
                text = "Shuffle",
                onClick = onShuffleClick
            )
        }
        item {
            QuickActionButton(
                icon = Icons.Rounded.Favorite,
                text = "Favorites",
                onClick = onFavoritesClick
            )
        }
        item {
            QuickActionButton(
                icon = Icons.Rounded.History,
                text = "Recent",
                onClick = onRecentlyPlayedClick
            )
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF1E1E24))
            .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF42C6B9),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun FolderItem(folderName: String, folderPath: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2E3B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF42C6B9)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folderName.ifEmpty { "Root Directory" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${songCount} audio files • $folderPath",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}


