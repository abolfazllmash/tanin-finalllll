package com.example.resonance

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class Scene { MENU, PLAYING, PAUSED }
enum class RoundState { READY, CASCADING, RESULT }

interface GameListener {
    fun onRoundEnd(cleared: Boolean, ignited: Int, total: Int, score: Int)
    fun onBestLevel(best: Int)
}

/**
 * The full simulation. Works in dp space (W/H are the surface size divided by density),
 * so every constant matches the web prototype. The renderer scales the canvas by density.
 *
 * Threading: update() and tap() are expected to be called from the same thread
 * (the GameView Choreographer loop, which runs on the main thread).
 */
class GameEngine {

    var W = 0f
    var H = 0f
    var scale = 1f

    @Volatile var scene = Scene.MENU
    var round = RoundState.READY

    val orbs = ArrayList<Orb>()
    val pulses = ArrayList<Pulse>()
    val particles = ArrayList<Particle>()
    val dust = ArrayList<Dust>()

    var level = 1
    var bestLevel = 1
    var score = 0
    var displayScore = 0f
    var ignitedCount = 0
    var totalOrbs = 0
    var target = 0
    var mult = 1f
    var lastCleared = false
    var tSec = 0f
    var bloomScale = 1f

    var sound: SoundManager? = null
    var haptics: Haptics? = null
    var listener: GameListener? = null

    private var lastHue = -999f
    private var comboPitch = 0
    private var idleT = 0f

    private val hues = floatArrayOf(188f, 318f, 44f)

    // ---- level curves ----
    /** Every 10th level is a GRAND CHALLENGE: one tap must light EVERY orb. */
    fun isGrand(n: Int) = n % 10 == 0

    /** Orb count keeps climbing, so late levels are dense fields. */
    fun levelTotal(n: Int) = min(12 + 4 * (n - 1), 90)

    /** Fraction of orbs you must light. Grand levels demand all of them. */
    fun targetPct(n: Int) = min(0.45f + n * 0.006f, 0.82f)

    fun levelSpeed(n: Int) = min(8f + (n - 1) * 2.0f, 38f)
    fun levelGrowMul(n: Int) = max(0.7f, 1f - (n - 1) * 0.025f)
    fun levelClusters(n: Int) = if (isGrand(n)) 1 else min(1 + n / 3, 5)

    /**
     * How far a bloom reaches, measured in MULTIPLES OF THE GAP BETWEEN ORBS.
     * Above ~1.3 a chain sweeps the field; below ~1.0 it starts dying out.
     * Tying reach to the actual spacing means a denser late-game field can never
     * accidentally become easier - difficulty always rises with the level.
     * Grand levels get a generous reach so a full one-tap cascade is possible.
     */
    fun reachFactor(n: Int) = if (isGrand(n)) 1.95f else max(1.12f, 2.15f - (n - 1) * 0.030f)

    fun configure(wPx: Int, hPx: Int, density: Float) {
        if (density <= 0f || wPx <= 0 || hPx <= 0) return
        val newW = wPx / density
        val newH = hPx / density
        val changed = newW != W || newH != H
        W = newW
        H = newH
        regenDust()
        if (changed) clampOrbs()
    }

    private fun regenDust() {
        dust.clear()
        repeat(46) {
            dust.add(
                Dust(
                    Random.nextFloat() * W,
                    Random.nextFloat() * H,
                    0.6f + Random.nextFloat() * 1.1f,
                    Random.nextFloat() * 6.283f,
                    0.04f + Random.nextFloat() * 0.06f
                )
            )
        }
    }

    private fun clampOrbs() {
        for (o in orbs) {
            if (W > o.r * 2) o.x = o.x.coerceIn(o.r, W - o.r)
            if (H > o.r * 2) o.y = o.y.coerceIn(o.r, H - o.r)
        }
    }

    fun newField(lvl: Int) {
        if (W < 1f || H < 1f) return
        level = lvl
        if (level > bestLevel) {
            bestLevel = level
            listener?.onBestLevel(bestLevel)
        }
        scale = sqrt((W * H) / (500f * 440f)).coerceIn(0.92f, 1.5f)
        val countMul = scale.coerceIn(1.0f, 1.45f)
        totalOrbs = (levelTotal(level) * countMul).roundToInt().coerceIn(5, GameConfig.MAX_ORBS)
        target = if (isGrand(level)) totalOrbs
                 else max(1, (totalOrbs * targetPct(level)).roundToInt()).coerceAtMost(totalOrbs - 1)


        orbs.clear(); pulses.clear(); particles.clear()

        val margin = GameConfig.MARGIN * scale
        val speed = levelSpeed(level) * scale
        val gmul = levelGrowMul(level)

        // --- Orb radius adapts to the crowd ---
        // As the orb count climbs, orbs shrink just enough that they all still fit
        // comfortably in the field instead of overlapping or failing to place.
        val fieldArea = max(1f, (W - 2 * margin) * (H - 2 * margin))
        val areaPerOrb = fieldArea / max(1, totalOrbs)
        // Crowded late levels shrink the orbs so the whole count still fits cleanly.
        val rFit = 0.20f * sqrt(areaPerOrb)
        val rBase = (GameConfig.ORB_MIN_R + GameConfig.ORB_R_VAR * 0.5f) * scale
        val orbR = min(rBase, rFit).coerceAtLeast(4.5f * scale)

        // Spacing follows the orb size; reach is then set RELATIVE to that spacing.
        val minD = max(orbR * 2.3f, 16f * scale)
        val ringReach = minD * reachFactor(level)
        bloomScale = ringReach / (GameConfig.RING_BLOOM * scale)
        val c = levelClusters(level)
        val clusterR = min((30f + (totalOrbs.toFloat() / c) * 2.2f) * scale, min(W, H) * 0.34f)

        val cx = FloatArray(c)
        val cy = FloatArray(c)
        for (i in 0 until c) {
            cx[i] = margin + clusterR + Random.nextFloat() * max(1f, W - 2 * (margin + clusterR))
            cy[i] = margin + clusterR + Random.nextFloat() * max(1f, H - 2 * (margin + clusterR))
        }
        // push clusters apart so there are real gaps between them
        repeat(8) {
            for (a in 0 until c) for (b in a + 1 until c) {
                val ddx = cx[b] - cx[a]
                val ddy = cy[b] - cy[a]
                var dd = sqrt(ddx * ddx + ddy * ddy)
                if (dd == 0f) dd = 1f
                val want = 2 * clusterR + GameConfig.CLUSTER_GAP * scale
                if (dd < want) {
                    val push = (want - dd) / 2
                    val ux = ddx / dd
                    val uy = ddy / dd
                    cx[a] -= ux * push; cy[a] -= uy * push
                    cx[b] += ux * push; cy[b] += uy * push
                }
            }
        }
        for (i in 0 until c) {
            cx[i] = cx[i].coerceIn(margin + 10, W - margin - 10)
            cy[i] = cy[i].coerceIn(margin + 10, H - margin - 10)
        }

        for (i in 0 until totalOrbs) {
            val ci = i % c
            var x = 0f
            var y = 0f
            var ok = false
            var att = 0
            do {
                val rad = clusterR * sqrt(Random.nextFloat())
                val ang = Random.nextFloat() * 6.283f
                x = (cx[ci] + cos(ang) * rad).coerceIn(margin, W - margin)
                y = (cy[ci] + sin(ang) * rad).coerceIn(margin, H - margin)
                ok = true
                for (o in orbs) {
                    val dx = o.x - x
                    val dy = o.y - y
                    if (dx * dx + dy * dy < minD * minD) { ok = false; break }
                }
                att++
            } while (!ok && att < 24)

            val r = orbR * (0.88f + Random.nextFloat() * 0.24f)
            val tr = Random.nextFloat()
            val type = when {
                tr < 0.56f -> OrbType.RING
                tr < 0.74f -> OrbType.BEAM
                tr < 0.88f -> OrbType.SPLITTER
                else -> OrbType.RESONATOR
            }
            orbs.add(
                Orb(
                    x, y,
                    (Random.nextFloat() * 2 - 1) * speed,
                    (Random.nextFloat() * 2 - 1) * speed,
                    r,
                    hues[Random.nextInt(hues.size)],
                    type,
                    Random.nextFloat() * 6.283f,
                    Random.nextFloat() * 6.283f,
                    gmul
                )
            )
        }

        score = 0
        displayScore = 0f
        ignitedCount = 0
        mult = 1f
        lastHue = -999f
        comboPitch = 0
        idleT = 0f
        round = RoundState.READY
    }

    /** Returns true if a pulse was launched. */
    fun tap(x: Float, y: Float): Boolean {
        if (scene != Scene.PLAYING || round != RoundState.READY) return false
        pulses.add(Pulse(x, y, GameConfig.TAP_RADIUS * scale * bloomScale, 0.5f * levelGrowMul(level), 0.45f, null, true))
        round = RoundState.CASCADING
        idleT = 0f
        sound?.playWhoosh()
        return true
    }

    fun continueAfterResult() {
        if (round != RoundState.RESULT) return
        newField(if (lastCleared) level + 1 else level)
    }

    private fun igniteOrb(o: Orb) {
        if (o.state != OrbState.IDLE) return
        o.state = OrbState.BLOOM
        o.bloomT = 0f
        o.alpha = 1f
        val gm = o.growMul
        when (o.type) {
            OrbType.BEAM -> {
                o.kind = BloomKind.BEAM; o.beamLen = 0f
                o.beamMax = GameConfig.BEAM_REACH * scale * bloomScale; o.grow = 0.45f * gm; o.fade = 0.4f
            }
            OrbType.RESONATOR -> {
                o.kind = BloomKind.RADIAL; o.bloomR = 0f
                o.bloomMax = GameConfig.RESONATOR_BLOOM * scale * bloomScale; o.grow = 0.45f * gm; o.fade = 0.5f
            }
            OrbType.SPLITTER -> {
                o.kind = BloomKind.RADIAL; o.bloomR = 0f
                o.bloomMax = GameConfig.SPLITTER_BLOOM * scale * bloomScale; o.grow = 0.4f * gm; o.fade = 0.4f
                val a = o.splitAngle
                val off = GameConfig.SPLITTER_OFFSET * scale
                var s = -1
                while (s <= 1) {
                    pulses.add(
                        Pulse(
                            o.x + cos(a) * off * s, o.y + sin(a) * off * s,
                            GameConfig.SPLITTER_CHILD * scale * bloomScale, 0.45f * gm, 0.45f, o.hue, false
                        )
                    )
                    s += 2
                }
            }
            OrbType.RING -> {
                o.kind = BloomKind.RADIAL; o.bloomR = 0f
                o.bloomMax = GameConfig.RING_BLOOM * scale * bloomScale; o.grow = 0.5f * gm; o.fade = 0.45f
            }
        }

        ignitedCount++
        comboPitch++
        mult = if (o.hue == lastHue) min(GameConfig.MULT_CAP, ((mult + 0.1f) * 10f).roundToInt() / 10f) else 1.0f
        lastHue = o.hue
        if (o.type == OrbType.RESONATOR) {
            mult = min(GameConfig.MULT_CAP, ((mult + 0.5f) * 10f).roundToInt() / 10f)
        }
        score += (GameConfig.BASE_POINTS * mult).roundToInt()
        spawnParticles(o.x, o.y, o.hue, 8)
        sound?.playPluck(comboPitch)
        haptics?.tick()
    }

    private fun igniteRadial(cx: Float, cy: Float, rad: Float) {
        for (o in orbs) {
            if (o.state == OrbState.IDLE) {
                val dx = o.x - cx
                val dy = o.y - cy
                val rr = rad + o.r * 0.4f
                if (dx * dx + dy * dy <= rr * rr) igniteOrb(o)
            }
        }
    }

    private fun igniteBeam(o: Orb) {
        val ca = cos(o.beamAngle)
        val sa = sin(o.beamAngle)
        for (t in orbs) {
            if (t.state == OrbState.IDLE) {
                val dx = t.x - o.x
                val dy = t.y - o.y
                val al = dx * ca + dy * sa
                val pe = -dx * sa + dy * ca
                if (abs(al) <= o.beamLen + t.r && abs(pe) <= GameConfig.BEAM_HALF_WIDTH * scale + t.r * 0.4f) {
                    igniteOrb(t)
                }
            }
        }
    }

    private fun isActive(): Boolean {
        if (pulses.isNotEmpty()) return true
        for (o in orbs) if (o.state == OrbState.BLOOM) return true
        return false
    }

    private fun endRound() {
        round = RoundState.RESULT
        lastCleared = ignitedCount >= target
        listener?.onRoundEnd(lastCleared, ignitedCount, totalOrbs, score)
    }

    private fun spawnParticles(x: Float, y: Float, hue: Float, n: Int) {
        if (particles.size > 520) return
        repeat(n) {
            val a = Random.nextFloat() * 6.283f
            val sp = (30f + Random.nextFloat() * 70f) * scale
            val life = 0.5f + Random.nextFloat() * 0.45f
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, (1.3f + Random.nextFloat() * 1.8f) * scale, hue, life, life))
        }
    }

    fun update(dt: Float) {
        tSec += dt
        // move orbs (frozen on the result screen)
        for (o in orbs) {
            if (o.state == OrbState.DEAD) continue
            if (round != RoundState.RESULT) {
                o.x += o.vx * dt; o.y += o.vy * dt
                if (o.x < o.r) { o.x = o.r; o.vx = -o.vx }
                if (o.x > W - o.r) { o.x = W - o.r; o.vx = -o.vx }
                if (o.y < o.r) { o.y = o.r; o.vy = -o.vy }
                if (o.y > H - o.r) { o.y = H - o.r; o.vy = -o.vy }
            }
        }
        // blooms
        for (o in orbs) {
            if (o.state != OrbState.BLOOM) continue
            o.bloomT += dt
            if (o.kind == BloomKind.RADIAL) {
                val p = min(1f, o.bloomT / o.grow)
                o.bloomR = o.bloomMax * easeOut(p)
                if (p < 1f) igniteRadial(o.x, o.y, o.bloomR)
            } else {
                val p = min(1f, o.bloomT / o.grow)
                o.beamLen = o.beamMax * easeOut(p)
                if (p < 1f) igniteBeam(o)
            }
            if (o.bloomT > o.grow) o.alpha = max(0f, 1f - (o.bloomT - o.grow) / o.fade)
            if (o.bloomT > o.grow + o.fade) o.state = OrbState.DEAD
        }
        // pulses (index loop: splitters may append while iterating)
        var k = 0
        while (k < pulses.size) {
            val pu = pulses[k]
            pu.t += dt
            val g = min(1f, pu.t / pu.grow)
            pu.r = pu.max * easeOut(g)
            if (g < 1f) igniteRadial(pu.x, pu.y, pu.r)
            pu.alpha = if (pu.t > pu.grow) max(0f, 1f - (pu.t - pu.grow) / pu.fade) else 1f
            k++
        }
        var i = pulses.size - 1
        while (i >= 0) { if (pulses[i].t > pulses[i].grow + pulses[i].fade) pulses.removeAt(i); i-- }
        // particles
        for (pa in particles) {
            pa.x += pa.vx * dt; pa.y += pa.vy * dt
            pa.vx *= 0.92f; pa.vy *= 0.92f
            pa.life -= dt
        }
        i = particles.size - 1
        while (i >= 0) { if (particles[i].life <= 0f) particles.removeAt(i); i-- }
        // score easing
        displayScore += (score - displayScore) * min(1f, dt * 12f)
        // end-of-round detection
        if (round == RoundState.CASCADING) {
            if (isActive()) idleT = 0f else {
                idleT += dt
                if (idleT > GameConfig.CASCADE_END_IDLE) endRound()
            }
        }
    }

    private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
}
