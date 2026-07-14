package com.example.resonance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Light haptic tick per ignition, throttled so it never becomes a constant buzz. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private var lastVibe = 0L

    fun tick() {
        val v = vibrator ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastVibe < 35) return
        lastVibe = now
        try {
            v.vibrate(VibrationEffect.createOneShot(8, 60))
        } catch (_: Exception) {}
    }
}
