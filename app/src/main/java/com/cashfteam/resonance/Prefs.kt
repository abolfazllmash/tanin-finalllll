package com.cashfteam.resonance

import android.content.Context

/** Session/best-level persistence (the native counterpart of the web build's in-memory best). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("resonance", Context.MODE_PRIVATE)

    var bestLevel: Int
        get() = sp.getInt("best_level", 1)
        set(value) { sp.edit().putInt("best_level", value).apply() }
}
