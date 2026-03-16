package com.ghostech.blehound

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Vibrator
import android.os.VibrationEffect
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.appcompat.widget.AppCompatTextView
import java.util.LinkedHashMap

data class BleSeenDevice(
    var name: String,
    var address: String,
    var rssi: Int,
    var packetCount: Int,
    var lastSeenMs: Long,
    var manufacturerText: String,
    var manufacturerDataText: String,
    var serviceUuidsText: String,
    var rawAdvText: String
)

object BleStore {
    val devices = LinkedHashMap<String, BleSeenDevice>()
    var shouldScan = false
    var isListFrozen = false
    var vibrateOnTracker = false
    var soundOnTracker = false
    var trackerSeenThisSession = false
    var lastTrackerNotifyMs = 0L
}

fun classifyDevice(d: BleSeenDevice): String {
    val name = d.name.lowercase()
    val mfg = d.manufacturerText.uppercase()
    val raw = d.rawAdvText.uppercase()
    val svc = d.serviceUuidsText.uppercase()
    val mfgData = d.manufacturerDataText.uppercase()

    if (mfg == "APPLE") {
        if ("AIRTAG" in name) return "AirTag"
        if ("FINDMY" in name || "FIND MY" in name) return "Find My"
        if ("AIRPODS" in name || "AIRPOD" in name) return "AirPods"
        if ("IBEACON" in name) return "iBeacon"
        if ("004C" in mfgData && ("12 19" in raw || "004C 12" in raw || "004C 19" in raw)) return "AirTag"
        if ("004C" in mfgData && "0215" in raw.replace(" ", "")) return "iBeacon"
        if ("BEATS" in name) return "Beats"
        return "Apple BLE"
    }

    if ("META" in name || "RAYBAN" in name || "RAY-BAN" in name) return "Meta Glasses"
    if ("TILE" in name) return "Tile"
    if ("SMARTTAG" in name || "GALAXY TAG" in name || (mfg == "SAMSNG" && "TAG" in name)) return "Galaxy Tag"
    if ("BEACON" in name) return "Beacon"
    if ("EARBUD" in name || "HEADSET" in name) return "Audio BLE"
    if ("WATCH" in name) return "Watch"
    if ("PHONE" in name) return "Phone BLE"
    if ("ESP32" in name || "ARDUINO" in name) return "Dev Board"
    if ("NORDIC" in mfg) return "Nordic BLE"
    if ("META" in mfg) return "Meta BLE"
    if ("GOOGL" in mfg || "GOOGLE" in mfg) return "Google BLE"
    if ("MSFT" in mfg) return "Microsoft BLE"
    if ("SAMSNG" in mfg) return "Samsung BLE"
    if (svc != "-" && svc.isNotBlank()) return "BLE Device"

    return "-"
}

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var trackerLegendView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var vibrateButton: Button
    private lateinit var soundButton: Button
    private lateinit var listView: ListView
    private lateinit var adapter: DeviceListAdapter

    private var scanner: BluetoothLeScanner? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var isScannerActive = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private var uiDirty = false

    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            if (uiDirty && !BleStore.isListFrozen) {
                renderDeviceList()
                uiDirty = false
            }
            uiHandler.postDelayed(this, 450)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "BLE HOUND"
            gravity = Gravity.CENTER
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(0xFFFF2233.toInt())
            setShadowLayer(14f, 0f, 0f, 0xFFFF7700.toInt())
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(6))
        }

        statusView = TextView(this).apply {
            text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            gravity = Gravity.CENTER
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFF4444.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        trackerLegendView = TextView(this).apply {
            text = "[ TRACKER ] = tracker detected"
            gravity = Gravity.CENTER
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFFFF66.toInt())
            setPadding(0, 0, 0, dp(10))
        }

        vibrator = getSystemService(Vibrator::class.java)
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }

        startButton = buildHellButton("START")
        startButton.setOnClickListener { startScan() }

        stopButton = buildHellButton("STOP")
        stopButton.setOnClickListener { lockList() }

        vibrateButton = buildHellButton("VIBRATE")
        vibrateButton.setOnClickListener {
            BleStore.vibrateOnTracker = !BleStore.vibrateOnTracker
            updateButtonStates()
        }

        soundButton = buildHellButton("SOUND")
        soundButton.setOnClickListener {
            BleStore.soundOnTracker = !BleStore.soundOnTracker
            updateButtonStates()
        }

        buttonBar.addView(
            startButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        )
        buttonBar.addView(
            stopButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        )
        buttonBar.addView(
            vibrateButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        )
        buttonBar.addView(
            soundButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
        )

        headerPanel.addView(titleView)
        headerPanel.addView(statusView)
        headerPanel.addView(trackerLegendView)
        headerPanel.addView(buttonBar)

        val tableHeader = buildTableHeader()

        listView = ListView(this).apply {
            divider = null
            cacheColorHint = 0
            setBackgroundColor(0xFF000000.toInt())
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        adapter = DeviceListAdapter(this) { device ->
            openDetail(device.address)
        }
        listView.adapter = adapter

        root.addView(headerPanel)
        root.addView(tableHeader)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                1
            )
        } else {
            initBle()
        }

        uiHandler.post(uiRefreshRunnable)
        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        renderDeviceList()
    }

    override fun onDestroy() {
        BleStore.shouldScan = isScannerActive
        super.onDestroy()
        uiHandler.removeCallbacks(uiRefreshRunnable)
        hardStopScanner()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initBle()
        } else {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun buildHellButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = true
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFF1E0.toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF6A0000.toInt(),
                    0xFF260000.toInt()
                )
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFFFF4400.toInt())
            }
            setPadding(dp(10), dp(14), dp(10), dp(14))
        }
    }

    private fun buildHeaderCell(text: String, weight: Float): TextView {
        return TextView(this).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFF80FF80.toInt())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun setHellButtonState(button: Button, enabledState: Boolean) {
        button.isEnabled = enabledState
        button.alpha = if (enabledState) 1.0f else 0.38f
    }

    private fun isTrackerClass(classText: String): Boolean {
        return classText == "AirTag" ||
            classText == "Tile" ||
            classText == "Galaxy Tag" ||
            classText == "Find My"
    }

    private fun notifyTrackerDetected() {
        val now = System.currentTimeMillis()
        if (now - BleStore.lastTrackerNotifyMs < 3000) return
        BleStore.lastTrackerNotifyMs = now

        if (BleStore.vibrateOnTracker) {
            vibrator?.let { v ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(180)
                }
            }
        }

        if (BleStore.soundOnTracker) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            uiHandler.postDelayed({
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            }, 220)
        }
    }

    private fun updateButtonStates() {
        val startEnabled = !isScannerActive || BleStore.isListFrozen
        val stopEnabled = isScannerActive && !BleStore.isListFrozen
        setHellButtonState(startButton, startEnabled)
        setHellButtonState(stopButton, stopEnabled)

        vibrateButton.text = "VIBRATE"
        vibrateButton.alpha = if (BleStore.vibrateOnTracker) 1.0f else 0.55f

        soundButton.text = "SOUND"
        soundButton.alpha = if (BleStore.soundOnTracker) 1.0f else 0.55f
    }

    private fun buildTableHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))

            addView(buildHeaderCell("RSSI", 0.9f))
            addView(buildHeaderCell("MAC", 3.0f))
            addView(buildHeaderCell("MFG", 1.3f))
            addView(buildHeaderCell("CLASS", 1.9f))
        }
    }

    private fun initBle() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
            return
        }

        if (!adapter.isEnabled) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
            return
        }

        scanner = adapter.bluetoothLeScanner

        if (BleStore.shouldScan) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                scanner?.startScan(null, settings, scanCallback)
                isScannerActive = true
            }
        }

        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun startScan() {
        BleStore.isListFrozen = false

        if (isScannerActive) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            BleStore.shouldScan = true
            renderDeviceList()
            updateButtonStates()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        isScannerActive = true
        BleStore.shouldScan = true
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun lockList() {
        if (!isScannerActive) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
            return
        }

        BleStore.isListFrozen = true
        BleStore.shouldScan = true
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun hardStopScanner() {
        if (!isScannerActive) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.stopScan(scanCallback)
        }
        isScannerActive = false
        updateButtonStates()
    }

    private fun openDetail(address: String) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("address", address)
        startActivity(intent)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = result.device.name ?: record?.deviceName ?: "Unknown"
            val addr = result.device.address ?: "Unknown"
            val rssi = result.rssi
            val now = System.currentTimeMillis()

            val manufacturerText = detectManufacturer(record)
            val manufacturerDataText = formatManufacturerData(record)
            val serviceUuidsText = formatServiceUuids(record)
            val rawAdvText = formatRawAdv(record)

            val existing = BleStore.devices[addr]
            if (existing == null) {
                BleStore.devices[addr] = BleSeenDevice(
                    name = name,
                    address = addr,
                    rssi = rssi,
                    packetCount = 1,
                    lastSeenMs = now,
                    manufacturerText = manufacturerText,
                    manufacturerDataText = manufacturerDataText,
                    serviceUuidsText = serviceUuidsText,
                    rawAdvText = rawAdvText
                )
            } else {
                existing.name = name
                existing.rssi = rssi
                existing.packetCount += 1
                existing.lastSeenMs = now
                existing.manufacturerText = manufacturerText
                existing.manufacturerDataText = manufacturerDataText
                existing.serviceUuidsText = serviceUuidsText
                existing.rawAdvText = rawAdvText
            }

            val classText = classifyDevice(BleStore.devices[addr]!!)
            if (isTrackerClass(classText)) {
                BleStore.trackerSeenThisSession = true
                notifyTrackerDetected()
            }

            if (!BleStore.isListFrozen) {
                uiDirty = true
            }
        }
    }

    private fun renderDeviceList() {
        val sorted = BleStore.devices.values.sortedWith(
            compareByDescending<BleSeenDevice> { it.rssi }
                .thenByDescending { it.lastSeenMs }
        )

        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        trackerLegendView.text =
            if (BleStore.trackerSeenThisSession) "[ TRACKER ] = tracker detected"
            else "[ TRACKER ] = no tracker seen yet"

        adapter.replaceData(sorted.take(250))
        updateButtonStates()
    }

    private fun detectManufacturer(record: ScanRecord?): String {
        if (record == null) return "-"
        val data = record.manufacturerSpecificData ?: return "-"
        if (data.size() == 0) return "-"

        val ids = mutableListOf<Int>()
        for (i in 0 until data.size()) {
            ids.add(data.keyAt(i))
        }

        return when {
            ids.contains(0x004C) -> "APPLE"
            ids.contains(0x0006) -> "MSFT"
            ids.contains(0x0075) -> "SAMSNG"
            ids.contains(0x00E0) -> "GOOGL"
            ids.contains(0x00D2) -> "NRDIC"
            else -> String.format("%04X", ids.first())
        }
    }

    private fun formatManufacturerData(record: ScanRecord?): String {
        if (record == null) return "-"
        val data = record.manufacturerSpecificData ?: return "-"
        if (data.size() == 0) return "-"

        val parts = mutableListOf<String>()
        for (i in 0 until data.size()) {
            val id = data.keyAt(i)
            val bytes = data.valueAt(i)
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            parts.add(String.format("0x%04X=[%s]", id, hex))
        }
        return parts.joinToString(" | ")
    }

    private fun formatServiceUuids(record: ScanRecord?): String {
        val uuids = record?.serviceUuids ?: return "-"
        if (uuids.isEmpty()) return "-"
        return uuids.joinToString(" | ") { it.uuid.toString() }
    }

    private fun formatRawAdv(record: ScanRecord?): String {
        val bytes = record?.bytes ?: return "-"
        if (bytes.isEmpty()) return "-"
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}


class OutlinedTextView(context: android.content.Context) : AppCompatTextView(context) {
    var strokeColor: Int = 0xFF000000.toInt()
    var strokeWidthPx: Float = 4f

    override fun onDraw(canvas: Canvas) {
        val originalColors = currentTextColor

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidthPx
        setTextColor(strokeColor)
        super.onDraw(canvas)

        paint.style = Paint.Style.FILL
        setTextColor(originalColors)
        super.onDraw(canvas)
    }
}


class DeviceListAdapter(
    private val activity: Activity,
    private val onDetailsClick: (BleSeenDevice) -> Unit
) : BaseAdapter() {

    private val items = mutableListOf<BleSeenDevice>()

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private fun nameCharLimit(): Int {
        val isLandscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) 32 else 22
    }

    fun replaceData(newItems: List<BleSeenDevice>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): BleSeenDevice = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun buildCell(text: String, weight: Float): TextView {
        return TextView(activity).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val rowBg = if (position % 2 == 0) 0xFF8F0A0A.toInt() else 0xFF050505.toInt()
        val classText = classifyDevice(item)
        val isTrackerRow =
            classText == "AirTag" ||
            classText == "Tile" ||
            classText == "Galaxy Tag" ||
            classText == "Find My"

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(rowBg)
                if (isTrackerRow) {
                    setStroke(dp(2), 0xFFFFFF00.toInt())
                }
            }
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(rowBg)
        }

        val bottomRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(rowBg)
        }

        val clippedMfg = if (item.manufacturerText.length > 6) item.manufacturerText.take(6) else item.manufacturerText
        val clippedName = item.name.replace("\n", " ").let {
            if (it.length > nameCharLimit()) it.take(nameCharLimit()) else it
        }

        topRow.addView(buildCell(item.rssi.toString(), 0.9f))
        topRow.addView(buildCell(item.address, 3.0f))
        topRow.addView(buildCell(clippedMfg, 1.3f))
        val isTrackerClass =
            classText == "AirTag" ||
            classText == "Tile" ||
            classText == "Galaxy Tag" ||
            classText == "Find My"

        val classView = if (isTrackerClass) {
            OutlinedTextView(activity).apply {
                text = classText
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 11f
                setTextColor(0xFFFF8800.toInt())
                strokeColor = 0xFF000000.toInt()
                strokeWidthPx = dp(1).toFloat() + 1f
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        } else {
            buildCell(classText, 1.9f)
        }

        topRow.addView(classView)

        val nameBox = TextView(activity).apply {
            
text = android.text.SpannableString("NAME: " + clippedName).apply {
    setSpan(
        android.text.style.ForegroundColorSpan(0xFF80FF80.toInt()),
        0,
        5,
        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}

            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFFF2F2F2.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF303030.toInt())
                setStroke(dp(1), 0xFF555555.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
        }

        bottomRow.addView(nameBox)

        container.addView(topRow)
        container.addView(bottomRow)

        container.setOnClickListener { onDetailsClick(item) }

        return container
    }
}
