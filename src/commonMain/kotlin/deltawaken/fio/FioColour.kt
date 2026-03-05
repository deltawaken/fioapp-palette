package deltawaken.fio

/**
 * Platform-neutral colour representation in gamma-corrected sRGB space.
 *
 * All channels are in the range `[0.0, 1.0]` and represent the
 * IEC 61966-2-1 (sRGB) transfer-function-encoded value — i.e. the
 * value you would pass directly to a display or to `android.graphics.Color`
 * / Compose `Color(r, g, b)`.
 *
 * Internally produced by [FioPalette.timeToColour] via HSL → sRGB conversion.
 */
data class FioColour(val r: Float, val g: Float, val b: Float)
