package com.ghostech.blehound

import android.content.Context
import android.graphics.Color

fun themeHex(context: Context): String {
    val prefs = context.getSharedPreferences("blehound_prefs", Context.MODE_PRIVATE)
    return prefs.getString("theme_hex", "#FF5522") ?: "#FF5522"
}

fun themeColor(context: Context): Int {
    return try { Color.parseColor(themeHex(context)) } catch (_: Exception) { Color.parseColor("#FF5522") }
}

fun isWhiteTheme(context: Context): Boolean {
    return themeHex(context).equals("#FFFFFF", ignoreCase = true) ||
           themeHex(context).equals("#FFFFFFFF", ignoreCase = true)
}

fun themedRowTextColor(context: Context): Int {
    return if (isWhiteTheme(context)) Color.parseColor("#000000") else Color.parseColor("#FFFFFF")
}

fun themedListRowColor(context: Context): Int {
    val hex = themeHex(context)
    return if (hex.equals("#FF5522", ignoreCase = true)) {
        Color.parseColor("#8F0A0A")
    } else {
        themeColor(context)
    }
}

fun isDefaultTheme(context: Context): Boolean {
    val hex = themeHex(context)
    return hex.equals("#FF5522", ignoreCase = true)
}
