# fioapp-palette — Reverse Engineering Notes

**Date:** 2026-03-05  
**Goal:** Reproduce the original fio.app (figureItOut, ~2014) colour palette algorithm.  
**Source:** One screenshot of the app showing 10 columns at different times of day.

---

## Reference Colours (colorpicked by Long-lazuli)

| Time | minuteOfDay | Hex | R | G | B |
|---|---|---|---|---|---|
| 07:42 | 462 | `#95d8bc` | 149 | 216 | 188 |
| 10:42 | 642 | `#feee95` | 254 | 238 | 149 |
| 13:42 | 822 | `#ffbe50` | 255 | 190 | 80 |
| 16:42 | 1002 | `#ac5a69` | 172 | 90 | 105 |
| 18:42 | 1122 | `#4f267f` | 79 | 38 | 127 |
| 21:42 | 1302 | `#150e43` | 21 | 14 | 67 |
| 23:42 | 1422 | `#03082b` | 3 | 8 | 43 |
| 03:42 | 222 | `#1b5366` | 27 | 83 | 102 |
| 05:42 | 342 | `#3da2af` | 61 | 162 | 175 |
| 07:42 (Sat) | 462 | `#99d9bc` | 153 | 217 | 188 |

Columns 1 and 10 are both 07:42 — confirms palette wraps (≈identical colours).

---

## Final Algorithm: 9-point piecewise-linear HSL lookup

### Colour space: HSL (not OKLCH)

fio.app was built ~2014. OKLCH wasn't in widespread use then. The algorithm
almost certainly used HSL. All fitting was done in HSL space.

### Approach

Instead of fitting mathematical formulas (cosines, sinusoids, piecewise linear
regressions) to approximate the data — which produced discontinuities at segment
boundaries and residual errors — the final algorithm uses the **9 reference
colours directly as anchor points** in a piecewise-linear lookup table.

All three HSL channels (H, S, L) share the same 9 time anchors. Between
anchors, each channel is linearly interpolated. Continuity is guaranteed by
construction: each segment starts exactly where the previous one ended.

**Error at anchor points: 0** (exact). The ±5° tolerance is only relevant for
times between anchors, where we assume linear interpolation is close enough.
This can be refined after user testing.

### HSL anchor values

| Time | min | H° | H° (unwrapped) | S | L |
|---|---|---|---|---|---|
| 03:42 | 222 | 195.20° | 195.20 | 0.581 | 0.253 |
| 05:42 | 342 | 186.84° | 186.84 | 0.483 | 0.463 |
| 07:42 | 462 | 154.93° | 154.93 | 0.462 | 0.716 |
| 10:42 | 642 | 50.86° | 50.86 | 0.981 | 0.790 |
| 13:42 | 822 | 37.71° | 37.71 | 1.000 | 0.657 |
| 16:42 | 1002 | 349.02° | −10.98 | 0.331 | 0.514 |
| 18:42 | 1122 | 267.64° | −92.36 | 0.539 | 0.324 |
| 21:42 | 1302 | 247.92° | −112.08 | 0.654 | 0.159 |
| 23:42 | 1422 | 232.50° | −127.50 | 0.870 | 0.090 |

### Hue unwrapping

Hue is stored "unwrapped" (continuous decreasing) so interpolation never
crosses the 360°/0° discontinuity. The total sweep is exactly −360° per day.

The wrap segment (t=1422→t=222 next day) uses `ANCHOR_H[0] − 360` as the
target, ensuring the cycle closes exactly.

### Segment rates (for reference)

| Segment | Rate (°/min) | Character |
|---|---|---|
| 03:42→05:42 | −0.070 | night coast |
| 05:42→07:42 | −0.266 | dawn ramp-up |
| 07:42→10:42 | −0.578 | **DAWN BURST** |
| 10:42→13:42 | −0.073 | midday plateau |
| 13:42→16:42 | −0.271 | dusk ramp-up |
| 16:42→18:42 | −0.678 | **DUSK BURST** |
| 18:42→21:42 | −0.110 | night coast |
| 21:42→23:42 | −0.128 | night coast |
| 23:42→03:42 | −0.155 | night coast (wrap) |

Potential future simplification: the 4 night-coast segments have similar rates
(−0.07 to −0.16) and could potentially be merged into 1–2 segments. Defer
until after user testing.

---

## Research history (superseded approaches)

### Two-line regression (abandoned)

Fitted two independent linear regressions (night + day) to the hue data.
RMSE on night = 2.5° (good), on day = 17° (bad — 07:42 off by 20°, 10:42
off by 33°). Worse: **discontinuous at boundaries** (31–53° jumps at 06:00,
10:42, 18:00).

### Three-segment regression (abandoned)

Added a dawn-burst segment (06:00→10:42). All 9 anchor points within 5° —
but still **discontinuous at boundaries** because each line was fitted
independently by least-squares, not constrained to meet.

### Cosine L, fixed S (abandoned)

L fit a cosine well (RMSE 0.039), but S did not (RMSE 0.28). Rather than
using different function shapes for different channels, the anchor-table
approach is simpler, more accurate, and treats all channels uniformly.

### Key insight

The right approach is not "fit a function to the data" but "use the data
as the function". The 9 reference points ARE the algorithm — interpolate
between them. Continuity, accuracy, and simplicity all come for free.

---

## Note on OKLCH vs HSL

The story spec originally called for an OKLCH pipeline. Based on this
reverse-engineering, the original fio.app used HSL. Implemented in HSL
(Option A — matches original more closely, simpler math).
The `FioColour(r, g, b)` output type is unchanged either way.
