package com.ghostech.blehound

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ThemeActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        val title = TextView(this).apply {
            text = "THEME"
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@ThemeActivity))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A0000.toInt(), 0xFF140000.toInt(), 0xFF000000.toInt())
            ).apply { setStroke(dp(1), themeColor(this@ThemeActivity)) }
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val colors = listOf(
            "#FF5522", "#FF0000", "#FF8800", "#FFD700",
            "#00C853", "#00B8D4", "#2962FF", "#7C4DFF",
            "#FF4081", "#FFFFFF"
        )

        val palette = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
        }

        for (rowColors in colors.chunked(5)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                gravity = Gravity.CENTER_HORIZONTAL
            }

            for (hex in rowColors) {
                val swatch = Button(this).apply {
                    text = ""
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor(hex))
                        setStroke(dp(2), 0xFFFFFFFF.toInt())
                        setSize(dp(52), dp(52))
                    }
                    setOnClickListener {
                        prefs.edit().putString("theme_hex", hex).apply()
                        finish()
                    }
                }
                row.addView(
                    swatch,
                    LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                        marginStart = dp(6)
                        marginEnd = dp(6)
                    }
                )
            }

            palette.addView(row)
        }

        val defaultButton = buildButton("RESET TO DEFAULT THEME")
        defaultButton.setOnClickListener {
            prefs.edit().putString("theme_hex", "#FF5522").apply()
            finish()
        }

        root.addView(title)
        root.addView(palette)
        root.addView(defaultButton)

        setContentView(root)
    }

    private fun buildButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = true
        textSize = 13f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(0xFFFFF1E0.toInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF6A0000.toInt(), 0xFF260000.toInt())
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), themeColor(this@ThemeActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
