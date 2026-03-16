package com.ghostech.blehound

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DetailActivity : Activity() {

    private lateinit var rssiView: TextView
    private lateinit var summaryView: TextView
    private lateinit var detailsView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var address: String? = null

    private val refresher = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = intent.getStringExtra("address")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val headerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF2A0000.toInt(),
                    0xFF140000.toInt(),
                    0xFF000000.toInt()
                )
            ).apply {
                setStroke(dp(1), 0xFFFF2200.toInt())
            }
        }

        val titleView = TextView(this).apply {
            text = "BLE HOUND DETAIL"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(0xFFFF2233.toInt())
            setShadowLayer(12f, 0f, 0f, 0xFFFF7700.toInt())
            letterSpacing = 0.08f
            setPadding(0, 0, 0, dp(8))
        }

        rssiView = TextView(this).apply {
            text = "-- dBm"
            gravity = Gravity.CENTER
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFF0D0.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        summaryView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFFD2A0.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        headerPanel.addView(titleView)
        headerPanel.addView(rssiView)
        headerPanel.addView(summaryView)

        detailsView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF9CFF9C.toInt())
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(detailsView)
        }

        root.addView(headerPanel)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun render() {
        val addr = address ?: return
        val d = BleStore.devices[addr]

        if (d == null) {
            rssiView.text = "-- dBm"
            summaryView.text = "Device not found"
            detailsView.text = "No cached device found for:\n$addr"
            return
        }

        val ageMs = System.currentTimeMillis() - d.lastSeenMs
        val ageText = when {
            ageMs < 1000 -> "${ageMs}ms"
            else -> String.format("%.1fs", ageMs / 1000.0)
        }

        rssiView.text = "${d.rssi} dBm"
        val deviceClass = classifyDevice(d)

        summaryView.text =
            "CLASS:${deviceClass}   MFG:${d.manufacturerText}   AGE:$ageText"

        detailsView.text = buildString {
            append("NAME           : ${d.name}\n")
            append("ADDRESS        : ${d.address}\n")
            append("CLASS          : ${deviceClass}\n")
            append("LIVE RSSI      : ${d.rssi}\n")
            append("PACKETS SEEN   : ${d.packetCount}\n")
            append("AGE            : $ageText\n")
            append("MANUFACTURER   : ${d.manufacturerText}\n\n")
            append("MANUFACTURER DATA\n")
            append("${d.manufacturerDataText}\n\n")
            append("SERVICE UUIDS\n")
            append("${d.serviceUuidsText}\n\n")
            append("RAW ADVERTISEMENT\n")
            append("${d.rawAdvText}\n")
        }
    }
}
