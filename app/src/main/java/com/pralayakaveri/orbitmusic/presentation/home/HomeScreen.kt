package com.pralayakaveri.orbitmusic.presentation.home

import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontWeight 
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.pralayakaveri.orbitmusic.domain.model.Song
import com.pralayakaveri.orbitmusic.domain.util.cleanSongTitle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.pralayakaveri.orbitmusic.presentation.components.SkeletonSongItem
import com.pralayakaveri.orbitmusic.presentation.components.MusicVisualizerIcon
import com.pralayakaveri.orbitmusic.presentation.components.SongItem
import com.pralayakaveri.orbitmusic.presentation.components.ListScrollResetHandler
import com.pralayakaveri.orbitmusic.presentation.components.GridScrollResetHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
    onSongClick: (List<Song>, Int) -> Unit,
    onCollectionClick: (String, List<Song>, String) -> Unit,
    onGalaxyClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    // Note: isFavorite calculation moved to a localized scope to prevent root recomposition

    var showSortSheet by remember { mutableStateOf(false) }

    // RECOMPOSITION LOGGING (ROOT)
    SideEffect { android.util.Log.d("Recompose", "HomeScreen ROOT") }

    val tabs = listOf("Songs", "Playlists", "Albums", "Artists", "Folders", "Favorites")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })
    val selectedTabIndex = pagerState.currentPage

    // Scroll States
    val songsListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val check = {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        hasPermission = check()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = check()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadData()
        }
    }
    val albumsGridState = rememberLazyGridState()
    val artistsGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()

    // Scroll Reset Signals (Unified in MainViewModel)
    val scrollResetSignal by mainViewModel.scrollResetSignal.collectAsState()

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

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
        com.pralayakaveri.orbitmusic.presentation.components.AddToPlaylistBottomSheet(
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

    val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val artistName = mainViewModel.selectedCollectionTitle.value
            if (artistName.isNotEmpty()) {
                mainViewModel.saveArtistImage(artistName, it.toString())
                // Removed clearSelectedCollection to keep the view open for immediate feedback
            }
        }
    }

    if (selectedSongForOptions != null) {
        SongOptionsWrapper(
            song = selectedSongForOptions!!,
            mainViewModel = mainViewModel,
            onShowPlaylistSheet = { showPlaylistSheet = true },
            onDismiss = { selectedSongForOptions = null }
        )
    }

    // Restore Sort Bottom Sheet
    if (showSortSheet) {
        val currentSortOrder by viewModel.sortOrder.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = Color(0xFF1E1E24),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Sort By",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
                
                SortOptionRow(
                    label = "Title",
                    isSelected = currentSortOrder == com.pralayakaveri.orbitmusic.domain.model.SortOrder.TITLE,
                    onClick = {
                        viewModel.setSortOrder(com.pralayakaveri.orbitmusic.domain.model.SortOrder.TITLE) {
                            mainViewModel.emitScrollReset(ScrollResetSignal.Reason.SORT)
                        }
                        showSortSheet = false
                    }
                )
                SortOptionRow(
                    label = "Recently Added",
                    isSelected = currentSortOrder == com.pralayakaveri.orbitmusic.domain.model.SortOrder.RECENTLY_ADDED,
                    onClick = {
                        viewModel.setSortOrder(com.pralayakaveri.orbitmusic.domain.model.SortOrder.RECENTLY_ADDED) {
                            mainViewModel.emitScrollReset(ScrollResetSignal.Reason.SORT)
                        }
                        showSortSheet = false
                    }
                )
                SortOptionRow(
                    label = "Artist",
                    isSelected = currentSortOrder == com.pralayakaveri.orbitmusic.domain.model.SortOrder.ARTIST,
                    onClick = {
                        viewModel.setSortOrder(com.pralayakaveri.orbitmusic.domain.model.SortOrder.ARTIST) {
                            mainViewModel.emitScrollReset(ScrollResetSignal.Reason.SORT)
                        }
                        showSortSheet = false
                    }
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // 1. Production TopAppBar (Exactly as in Beta)
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.3f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.pralayakaveri.orbitmusic.R.drawable.ic_logo_orbit),
                                        contentDescription = "App Logo",
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort", tint = Color.White.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = onGalaxyClick) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Galaxy", tint = Color.Gray)
                        }
                        IconButton(onClick = onInsightsClick) {
                            Icon(Icons.Rounded.BarChart, contentDescription = "Insights", tint = Color.Gray)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.Gray)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )

                // 2. Original Tabs (Sitting directly under Library title in Image 2)
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF42C6B9),
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color(0xFF42C6B9)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selectedTabIndex == index) Color.White else Color.Gray
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedCollectionTitle.isNotEmpty(),
            modifier = Modifier.fillMaxSize().zIndex(10f),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(animationSpec = tween(400)),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(animationSpec = tween(400))
        ) {
            val collectionSongs by mainViewModel.selectedCollectionSongs.collectAsState()
            val collectionType by mainViewModel.selectedCollectionType.collectAsState()
            val currentSong by mainViewModel.currentSong.collectAsState()
            val isPlaying by mainViewModel.isPlaying.collectAsState()
            val favoriteIds by mainViewModel.favoriteIds.collectAsState()
            val customArtworks by mainViewModel.customArtworks.collectAsState()
            val artistImages by mainViewModel.artistImages.collectAsState()

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
                // 1. Cinematic Hero Background (Restricted to top, reduced blur)
                if (collectionType == "Artist" || collectionType == "Album") {
                    // Reactive lookup: recalculates when artistImages map changes
                    val heroImage = if (collectionType == "Artist") {
                        artistImages[selectedCollectionTitle]
                    } else {
                        collectionSongs.firstOrNull()?.albumArtUri
                    }

                    if (heroImage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp) // Restricted height
                        ) {
                            coil.compose.AsyncImage(
                                model = heroImage,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = 0.5f), // Sharper, slightly brighter
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            // Fade to black at the bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF0F1115)),
                                            startY = 400f
                                        )
                                    )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // 2. Premium Transparent Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = { mainViewModel.clearSelectedCollection() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedCollectionTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (collectionType == "Artist") {
                            IconButton(onClick = { photoLauncher.launch("image/*") }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Artist Image",
                                    tint = Color(0xFF42C6B9)
                                )
                            }
                        }
                    }

                    // 3. Floating Content List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // Hero Section Spacer (Increased to push content down)
                        item { Spacer(modifier = Modifier.height(150.dp)) }

                        // 4. Floating Action System (Play All / Shuffle)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                QuickActionButton(
                                    icon = Icons.Rounded.PlayArrow,
                                    text = "Play All",
                                    onClick = { if (collectionSongs.isNotEmpty()) onSongClick(collectionSongs, 0) }
                                )
                                QuickActionButton(
                                    icon = Icons.Rounded.Shuffle,
                                    text = "Shuffle",
                                    onClick = { if (collectionSongs.isNotEmpty()) onSongClick(collectionSongs.shuffled(), 0) }
                                )
                            }
                        }

                        // 5. Cinematic Song List
                        itemsIndexed(collectionSongs, key = { _, song -> song.id }) { index, song ->
                            SongItem(
                                song = song,
                                isFavorite = favoriteIds.contains(song.id),
                                displayUri = customArtworks[song.id] ?: song.albumArtUri,
                                isCurrent = song.id == currentSong?.id,
                                isPlaying = isPlaying,
                                onClick = { onSongClick(collectionSongs, index) },
                                onFavoriteClick = { mainViewModel.toggleFavorite(song.id) },
                                onOptionsClick = { selectedSongForOptions = it }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()) // Only top padding to preserve MiniPlayer space
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> SongsPage(viewModel, mainViewModel, songsListState, scrollResetSignal, onSongClick, { selectedSongForOptions = it })
                    1 -> PlaylistsPage(viewModel, onSongClick)
                    2 -> AlbumsPage(viewModel, albumsGridState, scrollResetSignal, onCollectionClick)
                    3 -> ArtistsPage(
                        viewModel = viewModel,
                        mainViewModel = mainViewModel,
                        gridState = artistsGridState,
                        scrollResetSignal = scrollResetSignal,
                        onCollectionClick = onCollectionClick,
                        onEditArtistImage = { artistName ->
                            mainViewModel.selectCollection(artistName, emptyList(), "ArtistImagePick")
                            photoLauncher.launch("image/*")
                        }
                    )
                    4 -> FoldersPage(viewModel, foldersListState, scrollResetSignal, onCollectionClick)
                    5 -> FavoritesPage(viewModel, mainViewModel, favoritesListState, scrollResetSignal, onSongClick, { selectedSongForOptions = it })
                }
            }
        }
    }
}

@Composable
fun SongsPage(
    viewModel: HomeViewModel,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollResetSignal: com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal?,
    onSongClick: (List<Song>, Int) -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val songs by viewModel.getFilteredSongs(mainViewModel.searchQuery).collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentSongState = mainViewModel.currentSong.collectAsState()
    val isPlayingState = mainViewModel.isPlaying.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val customArtworks by mainViewModel.customArtworks.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "SongsPage RECOMPOSED") }

    // READINESS-DRIVEN ORCHESTRATION TRIGGER
    // Fires ONLY ONCE when the first content composition is stable.
    LaunchedEffect(isLoading) {
        if (!isLoading && songs.isNotEmpty()) {
            android.util.Log.d("StartupTimeline", "[PHASE 1] SongsPage Rendered. Notifying ViewModel of Settlement.")
            viewModel.onUiSettled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val sortOrder by viewModel.sortOrder.collectAsState()
        val signature = remember(songs, sortOrder) { 
            songs.take(3).map { it.id } + sortOrder.hashCode().toLong()
        }
        ListScrollResetHandler(listState, signature, scrollResetSignal)

        if (isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { 
                    FloatingSearchBar(
                        query = mainViewModel.searchQuery.value,
                        onQueryChange = { mainViewModel.onSearchQueryChange(it) }
                    )
                }
                item { 
                    QuickActionRow(
                        onPlayAllClick = { },
                        onShuffleClick = { },
                        onFavoritesClick = { },
                        onRecentlyPlayedClick = { }
                    )
                }
                items(10) { SkeletonSongItem() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // Collapsing Header Elements (Integrated into list)
                item {
                    val searchQuery by mainViewModel.searchQuery.collectAsState()
                    FloatingSearchBar(
                        query = searchQuery,
                        onQueryChange = { mainViewModel.onSearchQueryChange(it) }
                    )
                }
                item {
                    QuickActionRow(
                        onPlayAllClick = { if (songs.isNotEmpty()) onSongClick(songs, 0) },
                        onShuffleClick = { if (songs.isNotEmpty()) onSongClick(songs.shuffled(), 0) },
                        onFavoritesClick = { /* Handled via ViewModel/Pager */ },
                        onRecentlyPlayedClick = { /* Handled via ViewModel/Pager */ }
                    )
                }

                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val isItemCurrent = currentSongState.value?.id == song.id
                    val isItemPlaying = isPlayingState.value
                    SongItem(
                        song = song,
                        isFavorite = favoriteIds.contains(song.id),
                        displayUri = customArtworks[song.id] ?: song.albumArtUri,
                        isCurrent = isItemCurrent,
                        isPlaying = isItemPlaying,
                        onClick = { onSongClick(songs, index) },
                        onFavoriteClick = { mainViewModel.toggleFavorite(targetSongId = song.id) },
                        onOptionsClick = onOptionsClick
                    )
                }
                // Add spacer for MiniPlayer visibility
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
fun PlaylistsPage(
    viewModel: HomeViewModel,
    onSongClick: (List<Song>, Int) -> Unit
) {
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val workoutPlaylist by viewModel.workoutPlaylist.collectAsState()
    val chillPlaylist by viewModel.chillPlaylist.collectAsState()
    val focusPlaylist by viewModel.focusPlaylist.collectAsState()
    val drivingPlaylist by viewModel.drivingPlaylist.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "PlaylistsPage RECOMPOSED") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { PlaylistSection("Recently Added", recentlyAdded, onSongClick) }
        item { PlaylistSection("Most Played", mostPlayed, onSongClick) }
        item { PlaylistSection("Recently Played", recentlyPlayed, onSongClick) }
        item { PlaylistSection("Workout", workoutPlaylist, onSongClick) }
        item { PlaylistSection("Chill Out", chillPlaylist, onSongClick) }
        item { PlaylistSection("Deep Focus", focusPlaylist, onSongClick) }
        item { PlaylistSection("Driving", drivingPlaylist, onSongClick) }
    }
}

@Composable
fun AlbumsPage(
    viewModel: HomeViewModel,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    scrollResetSignal: com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal?,
    onCollectionClick: (String, List<Song>, String) -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    val albumSongsMap by viewModel.albumSongsMap.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "AlbumsPage RECOMPOSED") }

    Box(modifier = Modifier.fillMaxSize()) {
        val signature = remember(albums) { albums.take(3).map { it.id } }
        GridScrollResetHandler(gridState, signature, scrollResetSignal)

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(items = albums, key = { it.id }) { album ->
                val albumSongs = albumSongsMap[album.id] ?: emptyList()
                AlbumItem(album, onClick = { onCollectionClick(album.title, albumSongs, "Album") })
            }
        }
    }
}

@Composable
fun ArtistsPage(
    viewModel: HomeViewModel,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    scrollResetSignal: com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal?,
    onCollectionClick: (String, List<Song>, String) -> Unit,
    onEditArtistImage: (String) -> Unit
) {
    val artists by viewModel.artists.collectAsState()
    val artistSongsMap by viewModel.artistSongsMap.collectAsState()
    val artistImages by mainViewModel.artistImages.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "ArtistsPage RECOMPOSED") }

    Box(modifier = Modifier.fillMaxSize()) {
        val signature = remember(artists) { artists.take(3).map { it.id.toLong() } }
        GridScrollResetHandler(gridState, signature, scrollResetSignal)

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(
                items = artists, 
                key = { artist -> 
                    // Key includes image URI to force recomposition on change
                    "${artist.id}_${artist.name}_${artistImages[artist.name] ?: ""}"
                }
            ) { artist ->
                val artistSongs = artistSongsMap[artist.name] ?: emptyList()
                val customImage = artistImages[artist.name]
                ArtistItem(
                    artist = artist,
                    customImageUri = customImage,
                    onClick = { onCollectionClick(artist.name, artistSongs, "Artist") }
                )
            }
        }
    }
}

@Composable
fun FoldersPage(
    viewModel: HomeViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollResetSignal: com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal?,
    onCollectionClick: (String, List<Song>, String) -> Unit
) {
    val folders by viewModel.folders.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "FoldersPage RECOMPOSED") }

    Box(modifier = Modifier.fillMaxSize()) {
        val signature = remember(folders) { folders.keys.take(3).map { it.hashCode().toLong() } }
        ListScrollResetHandler(listState, signature, scrollResetSignal)

        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(folders.keys.toList(), key = { _, path -> path }) { _, path ->
                val folderSongs = folders[path] ?: emptyList()
                FolderItem(path.substringAfterLast('/'), path, folderSongs.size, onClick = { onCollectionClick(path.substringAfterLast('/'), folderSongs, "Folder") })
            }
        }
    }
}

@Composable
fun FavoritesPage(
    viewModel: HomeViewModel,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollResetSignal: com.pralayakaveri.orbitmusic.presentation.util.ScrollResetSignal?,
    onSongClick: (List<Song>, Int) -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val customArtworks by mainViewModel.customArtworks.collectAsState()
    val currentSongState = mainViewModel.currentSong.collectAsState()
    val isPlayingState = mainViewModel.isPlaying.collectAsState()

    SideEffect { android.util.Log.d("Recompose", "FavoritesPage RECOMPOSED") }

    Box(modifier = Modifier.fillMaxSize()) {
        val signature = remember(favorites) { favorites.take(3).map { it.id } }
        ListScrollResetHandler(listState, signature, scrollResetSignal)

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.pralayakaveri.orbitmusic.presentation.components.EmptyState("No favorites yet", Icons.Default.FavoriteBorder)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(favorites, key = { _, song -> song.id }) { index, song ->
                    val isItemCurrent = song.id == currentSongState.value?.id
                    val isItemPlaying = isPlayingState.value
                    SongItem(song, isFavorite = true, displayUri = customArtworks[song.id] ?: song.albumArtUri, isCurrent = isItemCurrent, isPlaying = isItemPlaying, onClick = { onSongClick(favorites, index) }, onFavoriteClick = { mainViewModel.toggleFavorite(song.id) }, onOptionsClick = onOptionsClick)
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White // Fixed color
                ),
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
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White // Fixed color
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray, // Fixed color
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumItem(album: com.pralayakaveri.orbitmusic.domain.model.Album, onClick: () -> Unit) {
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
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White // Fixed color
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray, // Fixed color
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistItem(
    artist: com.pralayakaveri.orbitmusic.domain.model.Artist,
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
                .background(Color(0xFF2A2E3B)),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val displayImage = customImageUri ?: ""
            
            if (displayImage.isNotEmpty()) {
                coil.compose.AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayImage)
                        .crossfade(true)
                        .build(),
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
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White // Fixed color
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "${artist.songCount} songs • ${artist.albumCount} albums",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray, // Fixed color
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
// SongItem moved to components/SongItem.kt


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
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (isSelected) Color(0xFF42C6B9) else Color.White,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            )
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF42C6B9),
                modifier = Modifier.size(20.dp)
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
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White // Fixed color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${songCount} audio files • $folderPath",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray, // Fixed color
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



@Composable
fun LibraryHeaderControls(
    viewModel: HomeViewModel,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    onSongClick: (List<Song>, Int) -> Unit,
    onNavigateToPage: (Int) -> Unit
) {
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val songs by viewModel.getFilteredSongs(mainViewModel.searchQuery).collectAsStateWithLifecycle(emptyList())

    SideEffect { android.util.Log.d("Recompose", "LibraryHeaderControls RECOMPOSED") }

    Column {
        FloatingSearchBar(
            query = searchQuery,
            onQueryChange = { mainViewModel.onSearchQueryChange(it) }
        )

        QuickActionRow(
            onPlayAllClick = {
                if (songs.isNotEmpty()) onSongClick(songs, 0)
            },
            onShuffleClick = {
                if (songs.isNotEmpty()) onSongClick(songs.shuffled(), 0)
            },
            onFavoritesClick = { onNavigateToPage(5) },
            onRecentlyPlayedClick = { onNavigateToPage(1) }
        )
    }
}

@Composable
private fun SongOptionsWrapper(
    song: Song,
    mainViewModel: com.pralayakaveri.orbitmusic.presentation.main.MainViewModel,
    onShowPlaylistSheet: () -> Unit,
    onDismiss: () -> Unit
) {
    val favoriteIds by mainViewModel.favoriteIds.collectAsStateWithLifecycle()
    val customArtworks by mainViewModel.customArtworks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val isFavorite = favoriteIds.contains(song.id)

    com.pralayakaveri.orbitmusic.presentation.components.SongOptionsMenu(
        song = song,
        customArtworkUri = customArtworks[song.id],
        isFavorite = isFavorite,
        onFavoriteClick = { mainViewModel.toggleFavorite(targetSongId = song.id) },
        onPlaylistClick = onShowPlaylistSheet,
        onShareClick = {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                type = "audio/*"
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
            onDismiss()
        },
        onDeleteClick = {
            // Delete logic remains in HomeScreen via deleteLauncher interaction
            // or we could hoist the launcher. For now, we'll keep the dismiss
            // and let the parent handle the actual delete call if needed,
            // but the UI is isolated here.
            onDismiss()
        },
        onDismiss = onDismiss
    )
}
