package com.ghostech.blehound

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class NotificationsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }
    private lateinit var trackerButton: Button
    private lateinit var gadgetButton: Button
    private lateinit var droneButton: Button
    private lateinit var fedButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        val title = TextView(this).apply {
            text = "NOTIFICATIONS"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@NotificationsActivity))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A0000.toInt(), 0xFF140000.toInt(), 0xFF000000.toInt())
            ).apply { setStroke(dp(1), themeColor(this@NotificationsActivity)) }
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        trackerButton = buildButton("")
        gadgetButton = buildButton("")
        droneButton = buildButton("")
        fedButton = buildButton("")
        trackerButton.setOnClickListener { pickSound("sound_trackers", 3001) }
        gadgetButton.setOnClickListener { pickSound("sound_gadgets", 3002) }
        droneButton.setOnClickListener { pickSound("sound_drones", 3003) }
        fedButton.setOnClickListener { pickSound("sound_feds", 3004) }

        root.addView(title)
        root.addView(trackerButton)
        root.addView(gadgetButton)
        root.addView(droneButton)
        root.addView(fedButton)

        setContentView(root)
        refresh()
    }

    private fun pickSound(key: String, code: Int) {
        val raw = prefs.getString(key, "__DEFAULT__") ?: "__DEFAULT__"
        val current = when (raw) {
            "__DEFAULT__" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "__SILENT__" -> null
            else -> Uri.parse(raw)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select notification sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        startActivityForResult(intent, code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val picked = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        when (requestCode) {
            3001 -> save("sound_trackers", picked)
            3002 -> save("sound_gadgets", picked)
            3003 -> save("sound_drones", picked)
            3004 -> save("sound_feds", picked)
        }
        refresh()
    }
    private fun save(key: String, uri: Uri?) {
        val value = when {
            uri == null -> "__SILENT__"
            else -> uri.toString()
        }
        prefs.edit().putString(key, value).apply()
    }

    private fun labelFor(key: String): String {
        val raw = prefs.getString(key, "__DEFAULT__") ?: "__DEFAULT__"
        if (raw == "__DEFAULT__") return "DEFAULT"
        if (raw == "__SILENT__") return "SILENT"
        val uri = Uri.parse(raw)
        val rt = RingtoneManager.getRingtone(this, uri) ?: return "DEFAULT"
        return rt.getTitle(this)
    }

    private fun refresh() {
        trackerButton.text = "TRACKERS: " + labelFor("sound_trackers")
        gadgetButton.text = "GADGETS: " + labelFor("sound_gadgets")
        droneButton.text = "DRONES: " + labelFor("sound_drones")
        fedButton.text = "FEDS: " + labelFor("sound_feds")
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
            setStroke(dp(1), themeColor(this@NotificationsActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
