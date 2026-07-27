package com.cashfteam.resonance

import android.content.Context

/**
 * Persistent state:
 *  - bestLevel:    the highest level the player ever cleared
 *  - currentLevel: the level to resume from ("ادامه" button on the main menu)
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("resonance", Context.MODE_PRIVATE)

    var bestLevel: Int
        get() = sp.getInt("best_level", 1)
        set(value) { sp.edit().putInt("best_level", value).apply() }

    /** The player's current progress. 1 means no saved run (starts fresh). */
    var currentLevel: Int
        get() = sp.getInt("current_level", 1)
        set(value) { sp.edit().putInt("current_level", value).apply() }

    fun hasSavedProgress(): Boolean = currentLevel > 1
    fun clearProgress() { sp.edit().remove("current_level").apply() }
}
