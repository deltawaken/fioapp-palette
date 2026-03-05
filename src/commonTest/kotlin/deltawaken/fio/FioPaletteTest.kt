package deltawaken.fio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [FioPalette] — H, S, L channels and full RGB output,
 * verified against 9 reference colours colorpicked from the original
 * fio.app (~2014) screenshot.
 *
 * All anchor points are exact (0 error) by construction of the
 * piecewise-linear lookup. These tests guard against regressions.
 */
class FioPaletteTest {

    // ---- Reference data ----
    // (minuteOfDay, H°, S, L, R, G, B)
    private data class Ref(
        val t: Int,
        val h: Float, val s: Float, val l: Float,
        val r: Int, val g: Int, val b: Int,
    )

    private val refs = listOf(
        Ref( 222, 195.20f, 0.581f, 0.253f,  27,  83, 102),  // 03:42
        Ref( 342, 186.84f, 0.483f, 0.463f,  61, 162, 175),  // 05:42
        Ref( 462, 154.93f, 0.462f, 0.716f, 149, 216, 188),  // 07:42
        Ref( 642,  50.86f, 0.981f, 0.790f, 254, 238, 149),  // 10:42
        Ref( 822,  37.71f, 1.000f, 0.657f, 255, 190,  80),  // 13:42
        Ref(1002, 349.02f, 0.331f, 0.514f, 172,  90, 105),  // 16:42
        Ref(1122, 267.64f, 0.539f, 0.324f,  79,  38, 127),  // 18:42
        Ref(1302, 247.92f, 0.654f, 0.159f,  21,  14,  67),  // 21:42
        Ref(1422, 232.50f, 0.870f, 0.090f,   3,   8,  43),  // 23:42
    )

    // ---- Hue tests (0° tolerance — exact at anchors) ----

    private fun assertHueExact(ref: Ref) {
        val actual = FioPalette.hue(ref.t)
        var diff = abs(actual - ref.h) % 360f
        if (diff > 180f) diff = 360f - diff
        assertTrue(
            diff < 0.1f,
            "t=${ref.t}: hue expected=${ref.h}° got=$actual° diff=$diff°"
        )
    }

    @Test fun `hue exact at t=222`()  = assertHueExact(refs[0])
    @Test fun `hue exact at t=342`()  = assertHueExact(refs[1])
    @Test fun `hue exact at t=462`()  = assertHueExact(refs[2])
    @Test fun `hue exact at t=642`()  = assertHueExact(refs[3])
    @Test fun `hue exact at t=822`()  = assertHueExact(refs[4])
    @Test fun `hue exact at t=1002`() = assertHueExact(refs[5])
    @Test fun `hue exact at t=1122`() = assertHueExact(refs[6])
    @Test fun `hue exact at t=1302`() = assertHueExact(refs[7])
    @Test fun `hue exact at t=1422`() = assertHueExact(refs[8])

    // ---- Saturation tests (0.001 tolerance — exact at anchors) ----

    private fun assertSatExact(ref: Ref) {
        val actual = FioPalette.saturation(ref.t)
        val diff = abs(actual - ref.s)
        assertTrue(
            diff < 0.001f,
            "t=${ref.t}: saturation expected=${ref.s} got=$actual diff=$diff"
        )
    }

    @Test fun `saturation exact at t=222`()  = assertSatExact(refs[0])
    @Test fun `saturation exact at t=342`()  = assertSatExact(refs[1])
    @Test fun `saturation exact at t=462`()  = assertSatExact(refs[2])
    @Test fun `saturation exact at t=642`()  = assertSatExact(refs[3])
    @Test fun `saturation exact at t=822`()  = assertSatExact(refs[4])
    @Test fun `saturation exact at t=1002`() = assertSatExact(refs[5])
    @Test fun `saturation exact at t=1122`() = assertSatExact(refs[6])
    @Test fun `saturation exact at t=1302`() = assertSatExact(refs[7])
    @Test fun `saturation exact at t=1422`() = assertSatExact(refs[8])

    // ---- Lightness tests (0.001 tolerance — exact at anchors) ----

    private fun assertLightExact(ref: Ref) {
        val actual = FioPalette.lightness(ref.t)
        val diff = abs(actual - ref.l)
        assertTrue(
            diff < 0.001f,
            "t=${ref.t}: lightness expected=${ref.l} got=$actual diff=$diff"
        )
    }

    @Test fun `lightness exact at t=222`()  = assertLightExact(refs[0])
    @Test fun `lightness exact at t=342`()  = assertLightExact(refs[1])
    @Test fun `lightness exact at t=462`()  = assertLightExact(refs[2])
    @Test fun `lightness exact at t=642`()  = assertLightExact(refs[3])
    @Test fun `lightness exact at t=822`()  = assertLightExact(refs[4])
    @Test fun `lightness exact at t=1002`() = assertLightExact(refs[5])
    @Test fun `lightness exact at t=1122`() = assertLightExact(refs[6])
    @Test fun `lightness exact at t=1302`() = assertLightExact(refs[7])
    @Test fun `lightness exact at t=1422`() = assertLightExact(refs[8])

    // ---- Full RGB output tests (±2/255 tolerance for float rounding) ----

    private fun assertRgbNear(ref: Ref, tolerance: Int = 2) {
        val colour = FioPalette.timeToColour(ref.t)
        val rActual = (colour.r * 255f).toInt()
        val gActual = (colour.g * 255f).toInt()
        val bActual = (colour.b * 255f).toInt()
        assertTrue(
            abs(rActual - ref.r) <= tolerance &&
            abs(gActual - ref.g) <= tolerance &&
            abs(bActual - ref.b) <= tolerance,
            "t=${ref.t}: RGB expected=(${ref.r},${ref.g},${ref.b}) " +
            "got=($rActual,$gActual,$bActual)"
        )
    }

    @Test fun `rgb near reference at t=222`()  = assertRgbNear(refs[0])
    @Test fun `rgb near reference at t=342`()  = assertRgbNear(refs[1])
    @Test fun `rgb near reference at t=462`()  = assertRgbNear(refs[2])
    @Test fun `rgb near reference at t=642`()  = assertRgbNear(refs[3])
    @Test fun `rgb near reference at t=822`()  = assertRgbNear(refs[4])
    @Test fun `rgb near reference at t=1002`() = assertRgbNear(refs[5])
    @Test fun `rgb near reference at t=1122`() = assertRgbNear(refs[6])
    @Test fun `rgb near reference at t=1302`() = assertRgbNear(refs[7])
    @Test fun `rgb near reference at t=1422`() = assertRgbNear(refs[8])

    // ---- Boundary / wrap sanity ----

    @Test fun `hue at t=0 is in valid range`() {
        val h = FioPalette.hue(0)
        assertTrue(h in 0f..360f, "hue(0)=$h")
    }

    @Test fun `hue at t=1439 is in valid range`() {
        val h = FioPalette.hue(1439)
        assertTrue(h in 0f..360f, "hue(1439)=$h")
    }

    @Test fun `saturation at t=0 is in valid range`() {
        val s = FioPalette.saturation(0)
        assertTrue(s in 0f..1f, "saturation(0)=$s")
    }

    @Test fun `lightness at t=0 is in valid range`() {
        val l = FioPalette.lightness(0)
        assertTrue(l in 0f..1f, "lightness(0)=$l")
    }
}
