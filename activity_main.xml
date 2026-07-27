package com.cashfteam.resonance

/** Persian (Farsi) digit helpers, so every number in the UI reads like the text around it. */
object Fa {
    private val DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** "12" -> "۱۲" */
    fun num(n: Int): String = num(n.toString())

    fun num(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '0'..'9') DIGITS[ch - '0'] else ch)
        return sb.toString()
    }

    /** 1.4f -> "۱٫۴" (Persian decimal separator) */
    fun dec1(v: Float): String {
        val whole = v.toInt()
        val frac = ((v - whole) * 10).toInt().coerceIn(0, 9)
        return num(whole) + "٫" + num(frac)
    }
}
