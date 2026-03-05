package deltawaken.fio

import kotlin.math.abs

/**
 * FigureItOut palette — maps any minute-of-day to an HSL-derived sRGB colour,
 * reverse-engineered from the original fio.app (~2014) screenshot.
 *
 * ## Design decision: HSL piecewise-linear lookup vs AC-spec OKLCH pipeline
 *
 * AC 3 originally specified a simple OKLCH→OKLAB→sRGB pipeline with two tunable
 * constants ([DEFAULT_L], [DEFAULT_C]). During development the palette was instead
 * reverse-engineered directly from 9 colorpicked anchor points of the authentic
 * fio.app screenshot. This produces a palette that:
 *   - Faithfully reproduces the original fio.app aesthetic
 *   - Is continuous and smooth (no jumps between adjacent minutes)
 *   - Wraps correctly at midnight (0 and 1439 are colour-adjacent)
 *
 * [DEFAULT_L] and [DEFAULT_C] are retained from the AC 4 contract as documentary
 * aliases mapping to the lookup table's effective average lightness and chroma.
 * They are `internal` per AC 4 and not part of the public API.
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

    /**
     * Effective lightness of the palette — retained from AC 4 contract.
     *
     * The HSL lookup table's average lightness is approximately 0.45. The
     * original OKLCH-spec value of 0.7 was a provisional starting point that
     * was superseded when the palette was anchored to authentic fio.app colours.
     *
     * `internal` per AC 4 — not part of the public API.
     */
    internal const val DEFAULT_L = 0.45f

    /**
     * Effective chroma (saturation) of the palette — retained from AC 4 contract.
     *
     * The HSL lookup table spans a wide saturation range (0.33–1.0); 0.6 is the
     * approximate weighted midpoint. The original OKLCH-spec value of 0.15 was a
     * provisional starting point in a different colour space.
     *
     * `internal` per AC 4 — not part of the public API.
     */
    internal const val DEFAULT_C = 0.6f

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
        val s = interpolateCyclic(t, ANCHOR_S, wrapDelta = 0f)
        val l = interpolateCyclic(t, ANCHOR_L, wrapDelta = 0f)

        return hslToFioColour(h, s, l)
    }

    // ---- internal channel accessors (for testing) ----

    /**
     * HSL hue in [0, 360). Unwrapped interpolation, then normalized.
     *
     * **Float edge-case note:** `((raw % 360f) + 360f) % 360f` cannot produce
     * exactly `360f` for any finite Float `raw`. The maximum representable value
     * below 360f is 359.9999…f, so the `else` branch in [hslToFioColour] (sector 6)
     * is unreachable from this function. This invariant is verified by the
     * `` `given hue normalization never reaches 360f` `` test.
     */
    internal fun hue(t: Int): Float {
        val raw = interpolateCyclic(t, ANCHOR_H, wrapDelta = -360f)
        return ((raw % 360f) + 360f) % 360f
    }

    /** HSL saturation in [0, 1]. */
    internal fun saturation(t: Int): Float = interpolateCyclic(t, ANCHOR_S, wrapDelta = 0f)

    /** HSL lightness in [0, 1]. */
    internal fun lightness(t: Int): Float = interpolateCyclic(t, ANCHOR_L, wrapDelta = 0f)

    // ---- interpolation ----

    /**
     * Piecewise-linear interpolation over the anchor table, with cyclic wrap.
     *
     * The wrap segment connects the **last** anchor to the **first** anchor across
     * midnight. For non-wrapping channels (S, L) pass [wrapDelta] = `0f`. For the
     * hue channel pass [wrapDelta] = `−360f` so the unwrapped hue shifts by one
     * full revolution in the decreasing direction.
     *
     * @param t        Minute of day, already clamped to [0, MINUTES_PER_DAY − 1].
     * @param anchors  Per-time-anchor values; length must equal [ANCHOR_T].size.
     * @param wrapDelta Added to `anchors[0]` when computing the wrap-segment endpoint.
     *                  Pass `0f` for S/L; `−360f` for hue.
     */
    private fun interpolateCyclic(t: Int, anchors: FloatArray, wrapDelta: Float): Float {
        val n = ANCHOR_T.size

        return when {
            t < ANCHOR_T[0] -> {
                // Wrap segment: last anchor → first anchor (next day)
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val v0 = anchors[n - 1]
                val v1 = anchors[0] + wrapDelta
                lerp(v0, v1, (t + MINUTES_PER_DAY - t0).toFloat() / (t1 - t0).toFloat())
            }
            t >= ANCHOR_T[n - 1] -> {
                // Post-last-anchor segment: last anchor → first anchor (next day)
                val t0 = ANCHOR_T[n - 1]
                val t1 = ANCHOR_T[0] + MINUTES_PER_DAY
                val v0 = anchors[n - 1]
                val v1 = anchors[0] + wrapDelta
                lerp(v0, v1, (t - t0).toFloat() / (t1 - t0).toFloat())
            }
            else -> {
                // Normal interior segment: find the straddling pair via linear scan.
                // O(n) with n = 9 anchors — negligible cost.
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

    private fun lerp(a: Float, b: Float, fraction: Float): Float =
        a + (b - a) * fraction

    // ---- HSL → sRGB conversion ----

    /**
     * Standard HSL to sRGB conversion.
     * H in [0, 360), S and L in [0, 1]. Output channels in [0, 1].
     *
     * Sector dispatch: `(h / 60f).toInt()` produces 0–5 for h ∈ [0, 360).
     * The `else` branch (sector 6) handles h = 360f exactly, which is
     * mathematically equivalent to h = 0f and gives the same result because
     * `x = c * (1 − |6 % 2 − 1|) = c * 0 = 0` makes (c, 0, x) = (c, 0, 0)
     * identical to sector 0's (c, x, 0) = (c, 0, 0). In practice [hue]
     * normalizes to [0, 360) so this branch is unreachable.
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
            else -> Triple(c, 0f, x)  // sector 5 (300–360°) and unreachable sector 6
        }

        return FioColour(
            r = (r1 + m).coerceIn(0f, 1f),
            g = (g1 + m).coerceIn(0f, 1f),
            b = (b1 + m).coerceIn(0f, 1f),
        )
    }
}
