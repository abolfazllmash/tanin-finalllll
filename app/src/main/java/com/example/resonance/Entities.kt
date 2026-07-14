package com.example.resonance

enum class OrbType { RING, BEAM, SPLITTER, RESONATOR }
enum class OrbState { IDLE, BLOOM, DEAD }
enum class BloomKind { RADIAL, BEAM }

/** A floating orb. type drives the bloom behavior, hue drives color/resonance. */
class Orb(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var r: Float,
    val hue: Float,
    val type: OrbType,
    val beamAngle: Float,
    val splitAngle: Float,
    val growMul: Float
) {
    var state = OrbState.IDLE
    var kind = BloomKind.RADIAL
    var bloomR = 0f
    var beamLen = 0f
    var bloomMax = 0f
    var beamMax = 0f
    var grow = 0f
    var fade = 0f
    var bloomT = 0f
    var alpha = 1f
}

/** A free-floating expanding pulse (the player's tap, or a splitter's children). */
class Pulse(
    var x: Float, var y: Float,
    var max: Float, var grow: Float, var fade: Float,
    val hue: Float?, val isTap: Boolean
) {
    var r = 0f
    var t = 0f
    var alpha = 1f
}

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var r: Float, val hue: Float,
    var life: Float, val life0: Float
)

class Dust(val x: Float, val y: Float, val r: Float, val phase: Float, val baseA: Float)
