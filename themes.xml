package com.cashfteam.resonance

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Tiny synth: generates short tones with AudioTrack on demand. Pitch climbs with the
 * combo count over a pentatonic scale so a chain sounds like a rising arpeggio.
 * All calls are wrapped so audio failures never crash the game.
 */
class SoundManager {

    @Volatile var muted = false

    private val sampleRate = 44100
    private val scale = intArrayOf(0, 3, 5, 7, 10)
    private val tones = HashMap<Int, ShortArray>()
    private val active = AtomicInteger(0)
    private var whoosh: ShortArray? = null

    private fun buildTone(freq: Double, durSec: Double): ShortArray {
        val n = (sampleRate * durSec).toInt()
        val out = ShortArray(n)
        val w = 2.0 * PI * freq
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val attack = min(1.0, t / 0.005)
            val env = attack * exp(-t * 13.0)
            out[i] = (sin(w * t) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun toneFor(combo: Int): ShortArray = tones.getOrPut(combo) {
        val idx = (combo - 1).coerceAtLeast(0)
        val oct = idx / scale.size
        val deg = idx % scale.size
        val semis = scale[deg] + 12 * min(oct, 3)
        val freq = 196.0 * Math.pow(2.0, semis / 12.0)
        buildTone(freq, 0.30)
    }

    private fun playBuffer(buf: ShortArray) {
        try {
            @Suppress("DEPRECATION")
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buf.size * 2,
                AudioTrack.MODE_STATIC
            )
            track.write(buf, 0, buf.size)
            active.incrementAndGet()
            track.setNotificationMarkerPosition(buf.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try { t?.release() } catch (_: Exception) {}
                    active.decrementAndGet()
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            track.play()
        } catch (_: Exception) {
            active.decrementAndGet()
        }
    }

    fun playPluck(combo: Int) {
        if (muted) return
        if (active.get() > 6) return
        playBuffer(toneFor(combo))
    }

    fun playWhoosh() {
        if (muted) return
        val w = whoosh ?: buildTone(58.0, 0.26).also { whoosh = it }
        playBuffer(w)
    }
}
