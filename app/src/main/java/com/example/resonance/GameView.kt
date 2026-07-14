package com.example.resonance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the game field + on-canvas HUD via a Choreographer-driven loop, and
 * forwards taps to the engine. Interactive UI (menus, pause button) lives in
 * Android Views on top of this SurfaceView.
 */
class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback, Choreographer.FrameCallback {

    lateinit var engine: GameEngine

    private var running = false
    private var lastFrameNs = 0L
    private var density = 1f

    private val raviFont: Typeface? = try {
        ResourcesCompat.getFont(context, R.font.ravi_medium)
    } catch (_: Exception) {
        null
    }

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tLevel = textPaint(18f, 0.95f, Paint.Align.LEFT, true)
    private val tGoal = textPaint(12f, 0.62f, Paint.Align.LEFT, false)
    private val tMult = textPaint(18f, 0.9f, Paint.Align.CENTER, true)
    private val tPrompt = textPaint(14f, 0.85f, Paint.Align.CENTER, false)
    private val topInset = 24f

    init {
        holder.addCallback(this)
        density = resources.displayMetrics.density
        setZOrderOnTop(false)
    }

    private fun textPaint(sizeDp: Float, alpha: Float, align: Paint.Align, bold: Boolean) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(alpha, 255, 255, 255)
            textSize = sizeDp
            textAlign = align
            typeface = raviFont ?: Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        }

    override fun surfaceCreated(holder: SurfaceHolder) {
        density = resources.displayMetrics.density
        engine.configure(width, height, density)
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        engine.configure(w, h, density)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        var dt = if (lastFrameNs == 0L) 0f else (frameTimeNanos - lastFrameNs) / 1_000_000_000f
        lastFrameNs = frameTimeNanos
        if (dt > 0.05f) dt = 0.05f
        if (engine.scene == Scene.PLAYING) engine.update(dt)

        if (holder.surface.isValid) {
            val c = holder.lockCanvas()
            if (c != null) {
                try {
                    c.save()
                    c.scale(density, density)
                    renderGame(c)
                    c.restore()
                } finally {
                    holder.unlockCanvasAndPost(c)
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            engine.tap(event.x / density, event.y / density)
            return true
        }
        return super.onTouchEvent(event)
    }

    // ---------------- rendering ----------------

    private fun renderGame(c: Canvas) {
        val w = engine.W
        val h = engine.H
        val sc = engine.scale

        c.drawColor(Color.parseColor("#06070C"))

        // vignette
        base.style = Paint.Style.FILL
        base.shader = RadialGradient(
            w / 2, h * 0.42f, max(w, h) * 0.75f,
            intArrayOf(argb(0.28f, 30, 40, 70), argb(0f, 2, 3, 7)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, base)
        base.shader = null

        // dust (additive)
        glow.style = Paint.Style.FILL
        glow.shader = null
        for (d in engine.dust) {
            val a = d.baseA * (0.5f + 0.5f * sin(engine.tSec * 0.8f + d.phase))
            glow.color = argb(a, 150, 180, 255)
            c.drawCircle(d.x, d.y, d.r, glow)
        }

        // beams
        for (o in engine.orbs) if (o.state == OrbState.BLOOM && o.kind == BloomKind.BEAM) drawBeam(c, o, sc)
        // radial blooms
        for (o in engine.orbs) if (o.state == OrbState.BLOOM && o.kind == BloomKind.RADIAL) ring(c, o.x, o.y, o.bloomR, o.hue, o.alpha)
        // pulses
        for (p in engine.pulses) ring(c, p.x, p.y, p.r, p.hue, p.alpha)
        // orb glows
        for (o in engine.orbs) if (o.state != OrbState.DEAD) orbGlow(c, o)
        // particles (additive)
        glow.style = Paint.Style.FILL
        glow.shader = null
        for (pa in engine.particles) {
            val al = max(0f, pa.life / pa.life0)
            glow.color = hsl(pa.hue, 0.90f, 0.70f, al * 0.9f)
            c.drawCircle(pa.x, pa.y, pa.r, glow)
        }
        // cores + glyphs (normal blend)
        for (o in engine.orbs) if (o.state != OrbState.DEAD) drawCore(c, o)

        drawHud(c)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, hue: Float?, alpha: Float) {
        if (r < 1f) return
        val outer = r + 8f
        fun col(a: Float) = if (hue == null) hsl(215f, 0.55f, 0.90f, a) else hsl(hue, 0.92f, 0.66f, a)
        glow.style = Paint.Style.FILL
        glow.shader = RadialGradient(
            x, y, outer,
            intArrayOf(0x00000000, col(0.40f * alpha), col(0.85f * alpha), 0x00000000),
            floatArrayOf(0f, 0.80f, 0.93f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, outer, glow)
        glow.shader = null
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = 2f
        glow.color = col(0.85f * alpha)
        c.drawCircle(x, y, r, glow)
        glow.style = Paint.Style.FILL
    }

    private fun drawBeam(c: Canvas, o: Orb, sc: Float) {
        val ca = cos(o.beamAngle)
        val sa = sin(o.beamAngle)
        val len = o.beamLen
        val x1 = o.x - ca * len
        val y1 = o.y - sa * len
        val x2 = o.x + ca * len
        val y2 = o.y + sa * len
        glow.style = Paint.Style.STROKE
        glow.strokeCap = Paint.Cap.ROUND
        glow.shader = LinearGradient(
            x1, y1, x2, y2,
            intArrayOf(0x00000000, hsl(o.hue, 0.92f, 0.66f, 0.85f * o.alpha), 0x00000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        glow.strokeWidth = 11f * sc
        c.drawLine(x1, y1, x2, y2, glow)
        glow.shader = null
        glow.color = hsl(o.hue, 0.92f, 0.66f, 0.9f * o.alpha)
        glow.strokeWidth = 2.5f * sc
        c.drawLine(x1, y1, x2, y2, glow)
        glow.style = Paint.Style.FILL
        glow.strokeCap = Paint.Cap.BUTT
    }

    private fun orbGlow(c: Canvas, o: Orb) {
        val ba = if (o.state == OrbState.BLOOM) 1f else 0.85f
        val gr = o.r * 3.2f
        glow.style = Paint.Style.FILL
        glow.shader = RadialGradient(
            o.x, o.y, gr,
            intArrayOf(hsl(o.hue, 0.92f, 0.66f, 0.55f * ba), hsl(o.hue, 0.92f, 0.66f, 0.22f * ba), 0x00000000),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(o.x, o.y, gr, glow)
        glow.shader = null
    }

    private fun drawCore(c: Canvas, o: Orb) {
        base.style = Paint.Style.FILL
        base.color = hsl(o.hue, 0.95f, 0.74f, if (o.state == OrbState.BLOOM) 1f else 0.95f)
        c.drawCircle(o.x, o.y, o.r * 0.62f, base)
        base.color = argb(if (o.state == OrbState.BLOOM) 0.95f else 0.8f, 255, 255, 255)
        c.drawCircle(o.x, o.y, o.r * 0.26f, base)

        base.color = argb(0.9f, 255, 255, 255)
        base.strokeCap = Paint.Cap.ROUND
        base.strokeWidth = 1.6f
        when (o.type) {
            OrbType.BEAM -> {
                val ca = cos(o.beamAngle)
                val sa = sin(o.beamAngle)
                val l = o.r * 1.5f
                base.style = Paint.Style.STROKE
                c.drawLine(o.x - ca * l, o.y - sa * l, o.x + ca * l, o.y + sa * l, base)
                base.style = Paint.Style.FILL
            }
            OrbType.SPLITTER -> {
                val a = o.splitAngle
                val dx = cos(a)
                val dy = sin(a)
                val l = o.r * 1.3f
                c.drawCircle(o.x + dx * l, o.y + dy * l, 1.7f, base)
                c.drawCircle(o.x - dx * l, o.y - dy * l, 1.7f, base)
            }
            OrbType.RESONATOR -> {
                base.style = Paint.Style.STROKE
                base.alpha = (0.55f * 255).toInt()
                c.drawCircle(o.x, o.y, o.r * 1.45f, base)
                base.alpha = 255
                base.style = Paint.Style.FILL
            }
            OrbType.RING -> { /* clean */ }
        }
    }

    private fun drawHud(c: Canvas) {
        val w = engine.W
        val h = engine.H
        val lvlText = if (engine.isGrand(engine.level))
            "مرحله ${Fa.num(engine.level)} • چالش بزرگ"
        else
            "مرحله ${Fa.num(engine.level)}"
        c.drawText(lvlText, 18f, topInset + 16f, tLevel)
        val goalText = if (engine.isGrand(engine.level))
            "همه‌ی ${Fa.num(engine.totalOrbs)} گوی را روشن کن"
        else
            "${Fa.num(engine.target)} گوی از ${Fa.num(engine.totalOrbs)} را روشن کن"
        c.drawText(goalText, 18f, topInset + 32f, tGoal)

        tMult.alpha = if (engine.mult > 1f) 255 else (0.35f * 255).toInt()
        c.drawText("\u00D7" + Fa.dec1(engine.mult), w / 2, topInset + 15f, tMult)

        val prompt = when (engine.round) {
            RoundState.READY -> if (engine.isGrand(engine.level))
                "یک لمس \u2014 همه‌ی گوی‌ها باید روشن شوند"
            else
                "یک لمس بزن \u2014 ${Fa.num(engine.target)} گوی را روشن کن"
            RoundState.CASCADING -> "${Fa.num(engine.ignitedCount)} از ${Fa.num(engine.target)} روشن" +
                if (engine.ignitedCount >= engine.target) " \u2014 کامل" else "\u2026"
            RoundState.RESULT -> ""
        }
        if (prompt.isNotEmpty()) c.drawText(prompt, w / 2, h - 24f, tPrompt)

        // progress bar
        base.style = Paint.Style.FILL
        base.color = argb(0.12f, 255, 255, 255)
        c.drawRect(0f, h - 6f, w, h, base)
        val pct = if (engine.target > 0) min(1f, engine.ignitedCount.toFloat() / engine.target) else 0f
        base.color = if (engine.ignitedCount >= engine.target && engine.target > 0)
            Color.parseColor("#5EE08A") else Color.parseColor("#39D3FF")
        c.drawRect(0f, h - 6f, w * pct, h, base)
    }

    // ---------------- color helpers ----------------

    private fun argb(a: Float, r: Int, g: Int, b: Int): Int {
        val al = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hsl(h: Float, s: Float, l: Float, a: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val rgb = ColorUtils.HSLToColor(floatArrayOf(hue, s, l))
        val al = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (al shl 24) or (rgb and 0x00FFFFFF)
    }
}
