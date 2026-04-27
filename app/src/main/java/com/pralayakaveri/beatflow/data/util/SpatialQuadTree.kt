package com.pralayakaveri.beatflow.data.util

import com.pralayakaveri.beatflow.data.local.GalaxyIndexEntity

/**
 * High-performance QuadTree for Galaxy Spatial Culling.
 * Optimized for rapid viewport queries in the immersive 3D view.
 */
class SpatialQuadTree(
    private val bounds: RectF,
    private val capacity: Int = 32
) {
    private val nodes = mutableListOf<GalaxyIndexEntity>()
    private var divided = false
    
    private lateinit var northwest: SpatialQuadTree
    private lateinit var northeast: SpatialQuadTree
    private lateinit var southwest: SpatialQuadTree
    private lateinit var southeast: SpatialQuadTree

    fun insert(node: GalaxyIndexEntity): Boolean {
        if (!bounds.contains(node.x, node.y)) return false

        if (nodes.size < capacity && !divided) {
            nodes.add(node)
            return true
        }

        if (!divided) subdivide()

        return northwest.insert(node) || 
               northeast.insert(node) || 
               southwest.insert(node) || 
               southeast.insert(node)
    }

    private fun subdivide() {
        val x = bounds.centerX
        val y = bounds.centerY
        val w = bounds.width / 2f
        val h = bounds.height / 2f

        northwest = SpatialQuadTree(RectF(x - w, y - h, x, y), capacity)
        northeast = SpatialQuadTree(RectF(x, y - h, x + w, y), capacity)
        southwest = SpatialQuadTree(RectF(x - w, y, x, y + h), capacity)
        southeast = SpatialQuadTree(RectF(x, y, x + w, y + h), capacity)

        divided = true

        // Re-distribute existing nodes
        nodes.forEach { insert(it) }
        nodes.clear()
    }

    fun query(range: RectF, result: MutableList<GalaxyIndexEntity>) {
        if (!bounds.intersects(range)) return

        if (divided) {
            northwest.query(range, result)
            northeast.query(range, result)
            southwest.query(range, result)
            southeast.query(range, result)
        } else {
            nodes.forEach {
                if (range.contains(it.x, it.y)) {
                    result.add(it)
                }
            }
        }
    }

    data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
        val width get() = right - left
        val height get() = bottom - top

        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
        fun intersects(other: RectF) = !(other.left > right || other.right < left || other.top > bottom || other.bottom < top)
    }
}
