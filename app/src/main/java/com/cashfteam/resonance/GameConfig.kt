package com.cashfteam.resonance

/**
 * Central tunables. All sizes are in dp at scale = 1; the engine multiplies them
 * by a per-field [GameEngine.scale] derived from the screen size, and the renderer
 * draws in a density-scaled canvas, so values match the web prototype 1:1.
 */
object GameConfig {
    const val MAX_ORBS = 80

    // Bloom reach (dp @ scale 1)
    const val TAP_RADIUS = 64f
    const val RING_BLOOM = 52f
    const val RESONATOR_BLOOM = 42f
    const val SPLITTER_BLOOM = 34f
    const val SPLITTER_CHILD = 50f
    const val BEAM_REACH = 120f
    const val BEAM_HALF_WIDTH = 12f
    const val SPLITTER_OFFSET = 14f

    // Orb size (dp @ scale 1)
    const val ORB_MIN_R = 9f
    const val ORB_R_VAR = 4f

    const val MIN_SPACING = 20f
    const val MARGIN = 30f
    const val CLUSTER_GAP = 50f

    const val BASE_POINTS = 10
    const val MULT_CAP = 6f
    const val CLOSE_FRACTION = 0.15f   // "So close!" if you miss the goal by <= this fraction
    const val CASCADE_END_IDLE = 0.35f // seconds of no activity before the round ends
}
