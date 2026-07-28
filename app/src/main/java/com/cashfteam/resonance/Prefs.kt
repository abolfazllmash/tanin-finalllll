package com.cashfteam.resonance
import android.content.Context
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("resonance", Context.MODE_PRIVATE)
    var bestLevel: Int
        get() = sp.getInt("best_level", 1)
        set(value) { sp.edit().putInt("best_level", value).apply() }
    var currentLevel: Int
        get() = sp.getInt("current_level", 1)
        set(value) { sp.edit().putInt("current_level", value).apply() }
    fun hasSavedProgress(): Boolean = currentLevel > 1
}
