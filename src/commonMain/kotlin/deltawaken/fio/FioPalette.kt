package deltawaken.fio

import kotlin.math.abs

/**
 * FigureItOut palette — maps any minute-of-day to an HSL-derived sRGB colour,
 * reverse-engineered from the original fio.app (~2014) screenshot.
 *
 * ## Algorithm — 9-point piecewise-linear lookup
 *
 * Nine reference colours colorpicked from the original screenshot define anchor
 * points for H, S, and L in HSL space. Between anchors each channel is linearly
 * interpolated, guaranteeing continuity.
 *
 * The hue sweeps exactly −360° per day (decreasing: mint → yellow → orange →
 * purple → navy → mint). Hue anchors are stored "unwrapped" (continuous
 * decreasing) so interpolation never crosses the 360°/0° boundary.
 *
 * S and L anchors are plain HSL values — no unwrapping needed.
 *
 * ## Conversion: HSL → sRGB
 */
object FioPalette {

    private const val MINUTES_PER_DAY = 1440

    // ---- Anchor table — shared time axis ----
    // All three channels (H, S, L) share the same 9 time anchors.

    private val ANCHOR_T = intArrayOf(222, 342, 462, 642, 822, 1002, 1122, 1302, 1422)

    // Hue anchors — unwrapped (continuous decreasing, may be negative)
    private val ANCHOR_H = floatArrayOf(
        195.20f,   // 03:42
        186.84f,   // 05:42
        154.93f,   // 07:42
         50.86f,   // 10:42
         37.71f,   // 13:42
        -10.98f,   // 16:42  (349.02 − 360)
        -92.36f,   // 18:42  (267.64 − 360)
       -112.08f,   // 21:42  (247.92 − 360)
       -127.50f,   // 23:42  (232.50 − 360)
    )

    // Saturation anchors — plain HSL saturation [0, 1]
    private val ANCHOR_S = floatArrayOf(
        0.581f,   // 03:42
        0.483f,   // 05:42
        0.462f,   // 07:42
        0.981f,   // 10:42
        1.000f,   // 13:42
        0.331f,   // 16:42
        0.539f,   // 18:42
        0.654f,   // 21:42
        0.870f,   // 23:42
    )

    // Lightness anchors — plain HSL lightness [0, 1]
    private val ANCHOR_L = floatArrayOf(
        0.253f,   // 03:42
        0.463f,   // 05:42
        0.716f,   // 07:42
        0.790f,   // 10:42
        0.657f,   // 13:42
        0.514f,   // 16:42
        0.324f,   // 18:42
        0.159f,   // 21:42
        0.090f,   // 23:42
    )

    /**
     * Returns the palette colour for the given [minuteOfDay] (0–1 439).
     *
     * Values outside `[0, 1 439]` are clamped to the nearest boundary.
     */
    fun timeToColour(minuteOfDay: Int): FioColour {
        val t = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)

        val h = hue(t)
        val s = interpolate(t, ANCHOR_S)
        val l = interpolate(t, ANCHOR_L)

        return hslToFioColour(h, s, l)
    }

    // ---- internal channel accessors (for testing) ----

    /**
     * HSL hue in [0, 360). Unwrapped interpolation, then normalized.
     */
    internal fun hue(t: Int): Float {
        val raw = interpolateHue(t)
        return ((raw % 360f) + 360f) % 360f
    }

    /** HSL saturation in [0, 1]. */
    internal fun saturation(t: Int): Float = interpolate(t, ANCHOR_S)

    /** HSL lightness in [0, 1]. */
    internal fun lightness(t: Int): Float = interpolate(t, ANCHOR_L)

    // ---- interpolation ----

    /**
     * Piecewise-linear interpolation for a standard (non-wrapping) channel.
     * Used for S and L.
     */
    private fun interpolate(t: Int, anchors: FloatArray): Float {
        val n = ANCHOR_T.size

        return when {
            t < ANCHOR_T[0] -> {
                // Wrap segment: last → first (next day)
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val v0 = anchors[n - 1]
                val v1 = anchors[0]
                lerp(v0, v1, (t + MINUTES_PER_DAY - t0).toFloat() / (t1 - t0).toFloat())
            }
            t >= ANCHOR_T[n - 1] -> {
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val v0 = anchors[n - 1]
                val v1 = anchors[0]
                lerp(v0, v1, (t - t0).toFloat() / (t1 - t0).toFloat())
            }
            else -> {
                var i = 0
                while (i < n - 2 && t >= ANCHOR_T[i + 1]) i++
                val t0 = ANCHOR_T[i]
                val t1 = ANCHOR_T[i + 1]
                val v0 = anchors[i]
                val v1 = anchors[i + 1]
                lerp(v0, v1, (t - t0).toFloat() / (t1 - t0).toFloat())
            }
        }
    }

    /**
     * Piecewise-linear interpolation for hue (unwrapped, decreasing).
     * The wrap segment shifts the first anchor by −360° to maintain
     * the continuous decreasing direction.
     */
    private fun interpolateHue(t: Int): Float {
        val n = ANCHOR_T.size

        return when {
            t < ANCHOR_T[0] -> {
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val h0 = ANCHOR_H[n - 1]
                val h1 = ANCHOR_H[0] - 360f  // one full cycle
                lerp(h0, h1, (t + MINUTES_PER_DAY - t0).toFloat() / (t1 - t0).toFloat())
            }
            t >= ANCHOR_T[n - 1] -> {
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val h0 = ANCHOR_H[n - 1]
                val h1 = ANCHOR_H[0] - 360f
                lerp(h0, h1, (t - t0).toFloat() / (t1 - t0).toFloat())
            }
            else -> {
                var i = 0
                while (i < n - 2 && t >= ANCHOR_T[i + 1]) i++
                val t0 = ANCHOR_T[i]
                val t1 = ANCHOR_T[i + 1]
                val h0 = ANCHOR_H[i]
                val h1 = ANCHOR_H[i + 1]
                lerp(h0, h1, (t - t0).toFloat() / (t1 - t0).toFloat())
            }
        }
    }

    private fun lerp(a: Float, b: Float, fraction: Float): Float =
        a + (b - a) * fraction

    // ---- HSL → sRGB conversion ----

    /**
     * Standard HSL to sRGB conversion.
     * H in [0, 360), S and L in [0, 1]. Output channels in [0, 1].
     */
    private fun hslToFioColour(h: Float, s: Float, l: Float): FioColour {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when ((h / 60f).toInt()) {
            0    -> Triple(c, x, 0f)
            1    -> Triple(x, c, 0f)
            2    -> Triple(0f, c, x)
            3    -> Triple(0f, x, c)
            4    -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return FioColour(
            r = (r1 + m).coerceIn(0f, 1f),
            g = (g1 + m).coerceIn(0f, 1f),
            b = (b1 + m).coerceIn(0f, 1f),
        )
    }
}
