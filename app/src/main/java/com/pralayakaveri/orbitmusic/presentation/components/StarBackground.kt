package com.pralayakaveri.orbitmusic.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

@Composable
fun StarBackground(
    modifier: Modifier = Modifier,
    velocity: Float = 0f // 0f to 1f (Travel intensity)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val stars = remember {
        List(250) { // Increased count for dense streaks
            val isFar = Random.nextFloat() > 0.3f
            Star(
                x = Random.nextFloat() * 2f - 1f, // Normalized -1 to 1
                y = Random.nextFloat() * 2f - 1f,
                z = Random.nextFloat() * 2f, // Depth
                size = if (isFar) (0.5f + Random.nextFloat() * 1f) else (1.5f + Random.nextFloat() * 2.5f),
                alpha = if (isFar) (0.2f + Random.nextFloat() * 0.3f) else (0.4f + Random.nextFloat() * 0.5f),
                color = when (Random.nextInt(3)) {
                    0 -> Color(0xFFADD8E6)
                    1 -> Color(0xFFFFDAB9)
                    else -> Color.White
                }
            )
        }
    }

    val drift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing)),
        label = "starDrift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasCenterX = size.width / 2f
        val canvasCenterY = size.height / 2f

        stars.forEach { star ->
            // 3D Projection-ish logic for radial expansion
            val speedFactor = 1f + velocity * 15f
            val px = star.x * (1f + drift * 0.1f * speedFactor)
            val py = star.y * (1f + drift * 0.1f * speedFactor)
            
            // Map normalized to screen
            val startX = canvasCenterX + star.x * (size.width / 2f)
            val startY = canvasCenterY + star.y * (size.height / 2f)
            
            // During velocity, stars stretch away from center
            if (velocity > 0.05f) {
                val length = 20f * velocity * (1f + star.z)
                val angle = kotlin.math.atan2(star.y, star.x)
                val endX = startX + kotlin.math.cos(angle) * length
                val endY = startY + kotlin.math.sin(angle) * length
                
                drawLine(
                    color = star.color.copy(alpha = star.alpha * (1f - velocity * 0.5f)),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = star.size * (1f + velocity),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            } else {
                drawCircle(
                    color = star.color.copy(alpha = star.alpha),
                    radius = star.size,
                    center = Offset(startX, startY)
                )
            }
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val alpha: Float,
    val color: Color
)
