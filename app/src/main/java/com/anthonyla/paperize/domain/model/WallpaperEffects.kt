package com.anthonyla.paperize.domain.model

import com.anthonyla.paperize.core.constants.Constants
import kotlinx.serialization.Serializable

/** Also stored as JSON for per-album effects, so renamed properties need a migration. */
@Serializable
data class WallpaperEffects(
    val enableBlur: Boolean = false,
    val blurPercentage: Int = Constants.DEFAULT_BLUR_PERCENTAGE,
    val enableDarken: Boolean = false,
    val darkenPercentage: Int = Constants.DEFAULT_DARKEN_PERCENTAGE,
    val enableVignette: Boolean = false,
    val vignettePercentage: Int = Constants.DEFAULT_VIGNETTE_PERCENTAGE,
    val enableGrayscale: Boolean = false,
    val grayscalePercentage: Int = Constants.DEFAULT_GRAYSCALE_PERCENTAGE,

    // Interactive effects (live wallpaper mode only)
    val enableDoubleTap: Boolean = false,
    val enableChangeOnScreenOff: Boolean = false,
    val enableParallax: Boolean = false,
    val parallaxIntensity: Int = Constants.DEFAULT_PARALLAX_INTENSITY,
    /** Slowly pan across images that Fill or None cuts off (live only). */
    val enableAutoPan: Boolean = false,
    /** Seconds for one pass from one edge of the image to the other. */
    val autoPanSweepSeconds: Int = Constants.DEFAULT_AUTO_PAN_SWEEP_SECONDS
) {
    fun validate(): WallpaperEffects = copy(
        darkenPercentage = darkenPercentage.coerceIn(0, 100),
        blurPercentage = blurPercentage.coerceIn(0, 100),
        vignettePercentage = vignettePercentage.coerceIn(0, 100),
        grayscalePercentage = grayscalePercentage.coerceIn(0, 100),
        parallaxIntensity = parallaxIntensity.coerceIn(0, 100),
        autoPanSweepSeconds = autoPanSweepSeconds.coerceIn(
            Constants.AUTO_PAN_SWEEP_STEPS_SECONDS.min(),
            Constants.AUTO_PAN_SWEEP_STEPS_SECONDS.max()
        )
    )
}
