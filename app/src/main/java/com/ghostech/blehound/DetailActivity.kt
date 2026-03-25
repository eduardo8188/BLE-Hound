package com.ghostech.blehound

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : Activity() {

    private lateinit var rssiView: TextView
    private lateinit var summaryView: TextView
    private lateinit var detailsView: TextView
    private var pendingSaveText: String = ""
    private var pendingSaveName: String = "SIGNAL-LOG-0001.txt"

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

        val saveButton = Button(this).apply {
            text = "SAVE LOG"
            setOnClickListener { saveCurrentDeviceLog() }
        }

        headerPanel.addView(titleView)
        headerPanel.addView(rssiView)
        headerPanel.addView(summaryView)
        headerPanel.addView(saveButton)

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

    private fun nowStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun nextLogFileName(deviceClass: String): String {
        val prefs = getSharedPreferences("blehound_logs", MODE_PRIVATE)
        val key = "log_counter_" + deviceClass.uppercase().replace(" ", "_")
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return deviceClass.uppercase().replace(" ", "-") + "-LOG-" + next.toString().padStart(4, '0') + ".txt"
    }

    private fun buildClassificationReason(d: BleSeenDevice): String {
        val n = d.name.uppercase()
        val m = d.manufacturerText.uppercase()
        val md = d.manufacturerDataText.uppercase()
        val raw = d.rawAdvText.uppercase()
        val svc = d.serviceUuidsText.uppercase()
        val mac = d.address.lowercase()
        if (d.isWifi) {
            if (mac == "de:ad:be:ef:de:ad") return "Exact Wi-Fi Pwnagotchi BSSID match"
            if (mac.startsWith("de:ad:be")) return "Wi-Fi Pwnagotchi-style MAC prefix match"
            if (d.name.lowercase().contains("pwnagotchi")) return "Wi-Fi name contains pwnagotchi"
            if (isLikelyWifiDrone(d.name.lowercase())) return "Wi-Fi name matched drone keyword"
            if (isLikelyWifiPineapple(d.name.lowercase(), mac)) return "Wi-Fi SSID/BSSID matched Pineapple heuristic"
            return "Wi-Fi classification based on SSID/BSSID heuristics"
        }
        if (m == "APPLE" && ("AIRTAG" in n || ("004C" in md && ("12 19" in raw || "004C 12" in raw || "004C 19" in raw))) ) {
            return "Apple manufacturer data / BLE advertisement matched AirTag pattern"
        }
        if (mac == "80:e1:26" || mac == "80:e1:27" || mac == "0c:fa:22" || "3081" in svc || "3082" in svc || "3083" in svc || "3080" in svc || "FLIPPER" in n) {
            return "Flipper Zero signature matched MAC, service UUID, or name"
        }
        if ("PWNAGOTCHI" in n || mac.startsWith("de:ad:be")) return "Pwnagotchi signature matched name or MAC prefix"
        if ("HC-03" in n || "HC-05" in n || "HC-06" in n) return "Card skimmer signature matched HC-03/05/06 naming"
        if (d.droneLat != null && d.droneLon != null) return "Drone coordinates parsed from BLE payload"
        if (raw.contains("16 FA FF 0D") || raw.replace(" ", "").contains("16FAFF0D")) return "BLE raw advertisement contains 16 FA FF 0D drone signature"
        if (svc.contains("FFFA")) return "BLE service UUID contains FFFA"
        if ("DJI" in n || "PARROT" in n || "SKYDIO" in n || "AUTEL" in n || "ANAFI" in n) return "BLE name matched drone vendor keyword"
        if (mac.startsWith("00:25:df") || "AXON" in n) return "Axon signature matched MAC prefix or name"
        if ("FS EXT BATTERY" in n || "PENGUIN" in n || "FLOCK" in n || "PIGVISION" in n) return "Flock name signature matched"
        if (m == "XUNTONG") return "Manufacturer matched XUNTONG (0x09C8)"
        if (md.contains("09C8")) return "Manufacturer data contains 0x09C8"
        return "Classification based on current BLE/Wi-Fi signature rules"
    }

    private fun buildResearchLog(d: BleSeenDevice): String {
        val deviceClass = classifyDevice(d)
        val reason = buildClassificationReason(d)
        val ageMs = System.currentTimeMillis() - d.lastSeenMs
        val ageText = if (ageMs < 1000) "${ageMs}ms" else String.format(Locale.US, "%.1fs", ageMs / 1000.0)

        return buildString {
            append("BLE HOUND SIGNAL LOG\n")
            append("====================\n\n")

            append("LOGGED AT           : ${nowStamp()}\n")
            append("CLASS               : ${deviceClass}\n")
            append("CLASS BASIS         : ${reason}\n")
            append("NAME                : ${d.name}\n")
            append("MAC ADDRESS         : ${d.address}\n")
            append("MANUFACTURER        : ${d.manufacturerText}\n")
            append("RSSI AT SAVE        : ${d.rssi} dBm\n")
            append("PACKETS OBSERVED    : ${d.packetCount}\n")
            append("AGE AT SAVE         : ${ageText}\n")

            if (d.droneLat != null && d.droneLon != null) {
                append("DRONE LATITUDE      : ${d.droneLat}\n")
                append("DRONE LONGITUDE     : ${d.droneLon}\n")
            }

            append("\nMANUFACTURER DATA\n")
            append("-----------------\n")
            append("${d.manufacturerDataText}\n")

            append("\nSERVICE UUIDS\n")
            append("-------------\n")
            append("${d.serviceUuidsText}\n")

            append("\nRAW ADVERTISEMENT\n")
            append("-----------------\n")
            append("${d.rawAdvText}\n")
        }
    }

    private fun saveCurrentDeviceLog() {
        val addr = address ?: return
        val d = BleStore.devices[addr] ?: return
        val deviceClass = classifyDevice(d)
        pendingSaveName = nextLogFileName(deviceClass)
        pendingSaveText = buildResearchLog(d)

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, pendingSaveName)
        }
        startActivityForResult(intent, SAVE_LOG_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAVE_LOG_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openOutputStream(uri)?.use {
                it.write(pendingSaveText.toByteArray())
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getDeviceDescription(classText: String): String {
        return when (classText) {
            "Flipper Zero" -> "Portable multi-tool for pentesters and geeks. Can emulate RFID, NFC, sub-GHz remotes, and more. Commonly used for security research and sometimes malicious replay attacks."
            "Pwnagotchi" -> "AI-powered WiFi auditing tool built on Raspberry Pi. Passively captures WPA handshakes to audit wireless network security. Often carried by security researchers."
            "WiFi Pineapple" -> "Rogue access point and WiFi auditing platform by Hak5. Used for man-in-the-middle attacks, credential harvesting, and wireless network reconnaissance. Identified by OUI pattern xx:13:37."
            "Card Skimmer" -> "WARNING: Potential card skimmer detected. HC-05/HC-06 Bluetooth modules are commonly found in illegal skimming devices attached to ATMs, gas pumps, and POS terminals. Exercise caution."
            "Drone" -> "Unmanned Aerial Vehicle (UAV) detected via BLE Remote ID broadcast or WiFi. FAA regulations require drones to broadcast identification and location data. Coordinates shown below if available."
            "Axon" -> "Axon (formerly TASER International) law enforcement equipment detected. Likely a body-worn camera (Axon Body), conducted energy device, or related accessory."
            "Flock" -> "Flock Safety Automated License Plate Recognition (ALPR) system detected. These cameras are used by law enforcement and private communities to log vehicle plate data."
            "AirTag" -> "Apple AirTag Bluetooth tracker. Can be used legitimately for item tracking but also potentially for unwanted surveillance."
            "Tile" -> "Tile Bluetooth tracker. Used for finding personal items. Be aware of potential unwanted tracking."
            "Galaxy Tag" -> "Samsung Galaxy SmartTag Bluetooth tracker."
            "Find My" -> "Apple Find My network compatible device."
            "Dev Board" -> "Development board (ESP32/Arduino). General purpose microcontroller often used in DIY projects, IoT devices, and security research tools."
            else -> ""
        }
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
        val description = getDeviceDescription(deviceClass)

        summaryView.text =
            "CLASS:${deviceClass}   MFG:${d.manufacturerText}   AGE:$ageText"

        detailsView.text = buildString {
            append("NAME           : ${d.name}\n")
            append("ADDRESS        : ${d.address}\n")
            append("CLASS          : ${deviceClass}\n")
            if (description.isNotEmpty()) {
                append("DESCRIPTION    : ${description}\n")
            }
            if (d.droneLat != null && d.droneLon != null) {
                append("DRONE LAT      : ${d.droneLat}\n")
                append("DRONE LON      : ${d.droneLon}\n")
            }
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

private const val SAVE_LOG_REQUEST = 2002
