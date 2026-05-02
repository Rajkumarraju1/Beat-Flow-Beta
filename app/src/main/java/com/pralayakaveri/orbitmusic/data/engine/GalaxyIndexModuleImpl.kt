package com.pralayakaveri.orbitmusic.data.engine

import com.pralayakaveri.orbitmusic.data.local.*
import com.pralayakaveri.orbitmusic.domain.engine.LibraryIndexModule
import com.pralayakaveri.orbitmusic.domain.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class GalaxyIndexModuleImpl @Inject constructor(
    private val galaxyIndexDao: GalaxyIndexDao,
    private val galaxyAnchorDao: GalaxyAnchorDao,
    private val librarySongDao: LibrarySongDao
) : LibraryIndexModule {

    override val id: String = "GalaxyIndexModule"

    private val moduleScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        moduleScope.launch {
            // Debounced update to avoid spatial re-calculation during heavy IO bursts
            _trigger.debounce(2000L).collect {
                updateSpatialIndex()
            }
        }
    }

    override suspend fun onReconciliationComplete(allSongs: List<Song>) {
        _trigger.tryEmit(Unit)
    }

    private suspend fun updateSpatialIndex() = withContext(Dispatchers.IO) {
        android.util.Log.d("GalaxyIndexModule", "Recalculating spatial topology...")
        
        try {
            val songs = librarySongDao.getAllSongsSingle()
            val existingAnchors = galaxyAnchorDao.getAllAnchors().associateBy { it.artistId }
            val newNodes = mutableListOf<GalaxyIndexEntity>()
            val addedAnchors = mutableListOf<GalaxyAnchorEntity>()

            // Group songs into Artist Solar Systems
            val artistSystems = songs.groupBy { it.artistId }
            
            artistSystems.forEach { (artistId, artistSongs) ->
                // 1. Stable Topology: Get or Create Artist Anchor
                val anchor = existingAnchors[artistId] ?: run {
                    val firstSong = artistSongs.first()
                    val newAnchor = generateStableAnchor(artistId, firstSong.artist)
                    addedAnchors.add(newAnchor)
                    galaxyAnchorDao.insertAnchor(newAnchor) // Save immediately to prevent race conditions
                    newAnchor
                }

                // 2. Spiral Placement: Songs orbit the Artist Anchor
                artistSongs.forEachIndexed { index, song ->
                    // Archimedean Spiral: r = a + b * theta
                    val theta = index * 0.7f // Tightness of the spiral
                    val r = 40.0f + (index * 12.0f) // Growth rate
                    
                    val x = anchor.x + (r * cos(theta))
                    val y = anchor.y + (r * sin(theta))
                    
                    newNodes.add(GalaxyIndexEntity(
                        songId = song.id,
                        artistId = artistId,
                        x = x,
                        y = y,
                        clusterId = anchor.clusterId,
                        radius = 1.0f
                    ))
                }
            }

            // Atomic Spatial Refresh
            galaxyIndexDao.clearIndex()
            galaxyIndexDao.insertOrUpdateBulk(newNodes)
            
            android.util.Log.i("GalaxyIndexModule", "Spatial index updated: ${newNodes.size} stars across ${artistSystems.size} systems.")
        } catch (e: Exception) {
            android.util.Log.e("GalaxyIndexModule", "Spatial indexing failed", e)
        }
    }

    /**
     * Generates a stable, hash-based anchor for a new artist.
     * This ensures the artist "Sun" stays in the same region of the galaxy forever.
     */
    private fun generateStableAnchor(artistId: Long, artistName: String): GalaxyAnchorEntity {
        val hash = artistName.hashCode()
        val random = java.util.Random(hash.toLong())
        
        // Circular distribution with a hollow center
        val angle = random.nextDouble() * 2.0 * PI
        val distance = 600.0 + random.nextDouble() * 4000.0 
        
        return GalaxyAnchorEntity(
            artistId = artistId,
            x = (cos(angle) * distance).toFloat(),
            y = (sin(angle) * distance).toFloat(),
            clusterId = "GENRE_NEBULA_${(hash % 8).absoluteValue}" // Simulated genre clusters
        )
    }
}
