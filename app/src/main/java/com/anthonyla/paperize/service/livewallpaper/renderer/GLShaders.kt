package com.anthonyla.paperize.service.livewallpaper.renderer

import com.anthonyla.paperize.core.util.blurRadiusToSigma
import kotlin.math.hypot

object GLShaders {

    const val VERTEX_SHADER = """
        uniform mat4 u_mvpMatrix;
        attribute vec4 a_position;
        attribute vec2 a_texCoord;
        varying vec2 v_texCoord;
        varying vec2 v_screenCoord;

        void main() {
            gl_Position = u_mvpMatrix * a_position;
            v_texCoord = a_texCoord;
            // Where on the screen this is (-1..1 on both axes), whichever way the picture is drawn:
            // straight from its texture tiles, or from the blurred full-screen copy. Screen-wide
            // effects use it so they look the same either way.
            v_screenCoord = gl_Position.xy / gl_Position.w;
        }
    """

    /**
     * Fragment shader for directional Gaussian blur pass.
     * Uses 17-tap kernel (half-integer steps) to eliminate banding/ghosting at high blur radii.
     * Weights are pre-computed for sigma ~= 1.815 sampled at {0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4} steps.
     * Branchless: when blurRadius is 0, all offsets are 0 so all samples collapse to center.
     */
    const val BLUR_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform vec2 u_direction;
        uniform float u_blurRadius;
        varying vec2 v_texCoord;

        void main() {
            vec2 stepSize = u_direction * u_blurRadius / u_resolution;

            vec4 color = texture2D(u_texture, v_texCoord) * 0.1120;

            color += (texture2D(u_texture, v_texCoord + (-0.5 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (0.5 * stepSize))) * 0.1078;
            color += (texture2D(u_texture, v_texCoord + (-1.0 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (1.0 * stepSize))) * 0.0962;
            color += (texture2D(u_texture, v_texCoord + (-1.5 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (1.5 * stepSize))) * 0.0796;
            color += (texture2D(u_texture, v_texCoord + (-2.0 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (2.0 * stepSize))) * 0.0610;
            color += (texture2D(u_texture, v_texCoord + (-2.5 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (2.5 * stepSize))) * 0.0434;
            color += (texture2D(u_texture, v_texCoord + (-3.0 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (3.0 * stepSize))) * 0.0286;
            color += (texture2D(u_texture, v_texCoord + (-3.5 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (3.5 * stepSize))) * 0.0175;
            color += (texture2D(u_texture, v_texCoord + (-4.0 * stepSize)) +
                      texture2D(u_texture, v_texCoord + (4.0 * stepSize))) * 0.0099;

            gl_FragColor = color;
        }
    """

    /** The blur kernel's sigma, in steps of u_blurRadius (see [BLUR_FRAGMENT_SHADER]). */
    private const val BLUR_KERNEL_SIGMA_STEPS = 1.815f

    /**
     * The u_blurRadius step for a blur [radius] in pixels, so the live wallpaper blurs exactly as
     * strongly as the static one, which uses Android's own radius-to-sigma rule.
     */
    fun blurStepForRadius(radius: Float): Float = blurRadiusToSigma(radius) / BLUR_KERNEL_SIGMA_STEPS

    /**
     * u_vignetteExtent for a [width] × [height] surface: scales v_screenCoord so that its length is
     * the distance from the centre as a fraction of the half-diagonal (1 in the corners), like the
     * static wallpaper's vignette.
     */
    fun vignetteExtent(width: Int, height: Int): Pair<Float, Float> {
        val diagonal = hypot(width.toFloat(), height.toFloat())
        return if (diagonal > 0f) width / diagonal to height / diagonal else 0f to 0f
    }

    /**
     * Fragment shader for color effects (darken, vignette, grayscale).
     * Applied after blur passes. Uses branchless math for optimal GPU performance.
     */
    const val EFFECTS_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D u_texture;
        uniform float u_alpha;
        uniform float u_darkenFactor;
        uniform float u_vignetteFactor;
        uniform vec2 u_vignetteExtent;
        uniform float u_grayscaleFactor;
        uniform float u_adaptiveBrightnessFactor;
        varying vec2 v_texCoord;
        varying vec2 v_screenCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);

            color.rgb *= (1.0 - u_darkenFactor);

            // 2. Vignette, drawn as the static wallpaper draws it (WallpaperUtil.drawVignette): a
            //    circle centred on the screen whose radius shrinks from the half-diagonal as the
            //    strength grows, darkening 0% at the centre, 10% at 70% of the radius and 80% from
            //    the radius out. Off entirely at strength 0.
            float dist = length(v_screenCoord * u_vignetteExtent);
            float radius = max(1.0 - u_vignetteFactor / 1.5, 0.001);
            float t = clamp(dist / radius, 0.0, 1.0);
            float darkAmount = mix(t * (0.1 / 0.7), 0.1 + (t - 0.7) * (0.7 / 0.3), step(0.7, t));
            color.rgb *= (1.0 - darkAmount * step(0.001, u_vignetteFactor));

            // 3. Apply grayscale (branchless - mix handles factor 0 correctly)
            // ITU-R BT.709 standard luminance calculation
            float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            color.rgb = mix(color.rgb, vec3(gray), u_grayscaleFactor);

            color.rgb *= u_adaptiveBrightnessFactor;

            gl_FragColor = vec4(color.rgb, color.a * u_alpha);
        }
    """

}
