package com.pralayakaveri.beatflow.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion Profile defines the intensity and style of animations.
 * Supports Accessibility's "Reduced Motion" requirements.
 */
data class MotionProfile(
    val name: String,
    val enableWarp: Boolean,
    val cameraEasingScale: Float, // 1.0 = normal, 0.2 = stiff
    val enableParallax: Boolean,
    val lodTransitionDuration: Int, // ms
    val warpDurationMultiplier: Float // 1.0 = normal, 0.0 = instant
) {
    companion object {
        val Full = MotionProfile(
            name = "Full",
            enableWarp = true,
            cameraEasingScale = 1.0f,
            enableParallax = true,
            lodTransitionDuration = 600,
            warpDurationMultiplier = 1.0f
        )

        val Reduced = MotionProfile(
            name = "Reduced",
            enableWarp = false,
            cameraEasingScale = 0.2f,
            enableParallax = false,
            lodTransitionDuration = 200,
            warpDurationMultiplier = 0.0f // Effectively cross-fade
        )
    }
}

val LocalMotionProfile = staticCompositionLocalOf { MotionProfile.Full }
