package com.example.tapgauge

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var pressureValue: TextView
    private lateinit var statusLabel: TextView
    private lateinit var recordButton: Button
    private lateinit var startScanButton: Button
    private lateinit var lastScanText: TextView

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuButton: ImageView

    private var lastPressure: String? = null
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        menuButton = findViewById(R.id.menuButton)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }

        pressureValue = findViewById(R.id.pressureValue)
        statusLabel = findViewById(R.id.statusLabel)
        recordButton = findViewById(R.id.recordButton)
        startScanButton = findViewById(R.id.startScanButton)
        lastScanText = findViewById(R.id.lastScanText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        startScanButton.setOnClickListener {
            if (!isScanning) {
                isScanning = true
                startScanButton.text = "Stop Scanning"
                recordButton.visibility = View.GONE
                pressureValue.text = "-- psi"
                statusLabel.text = ""
                lastScanText.text = "Last Scan: --"
                Toast.makeText(this, "Ready to scan NFC tag", Toast.LENGTH_SHORT).show()
            } else {
                isScanning = false
                startScanButton.text = "Start Scan"
                Toast.makeText(this, "Scanning stopped", Toast.LENGTH_SHORT).show()
            }
        }

        recordButton.setOnClickListener { showBlankDialog() }
    }

    override fun onResume() {
        super.onResume()

        loadBlanksIntoDrawer()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_MUTABLE
            )
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (!isScanning) return

        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val ndef = Ndef.get(tag) ?: return

        try {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            ndef.close()

            val text = message?.let { extractText(it) } ?: return
            val pressurePsiString = parsePressure(text) ?: return

            val pressurePsi = pressurePsiString.toFloat()
            pressureValue.text = String.format("%.2f psi", pressurePsi)

            lastPressure = pressurePsiString

            updateSafetyStatus(pressurePsi)
            updateLastScanTime()

            recordButton.visibility = View.VISIBLE
            isScanning = false
            startScanButton.text = "Start Scan"

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read tag", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSafetyStatus(psi: Float) {
        when {
            psi < 7.25 -> {
                statusLabel.text = "SAFE"
                statusLabel.setTextColor(Color.GREEN)
            }
            psi < 14.5 -> {
                statusLabel.text = "WARNING"
                statusLabel.setTextColor(Color.YELLOW)
            }
            else -> {
                statusLabel.text = "DANGER"
                statusLabel.setTextColor(Color.RED)
            }
        }
    }

    private fun updateLastScanTime() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        lastScanText.text = "Last Scan: $timestamp"
    }

    private fun extractText(message: NdefMessage): String? {
        for (record in message.records) {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                return decodeTextRecord(record)
            }
        }
        return null
    }

    private fun decodeTextRecord(record: NdefRecord): String {
        val payload = record.payload
        val status = payload[0].toInt()
        val langLength = status and 0x3F
        val utf8 = (status and 0x80) == 0

        val textBytes = payload.copyOfRange(1 + langLength, payload.size)
        val charset = if (utf8) Charsets.UTF_8 else Charset.forName("UTF-16")

        return String(textBytes, charset)
    }

    // ⭐ FIXED: Now reads the THIRD number (pressure)
    private fun parsePressure(text: String): String? {
        val regex = Regex("""([0-9]+(\.[0-9]+)?)""")
        val nums = regex.findAll(text).map { it.value }.toList()
        return if (nums.size >= 3) nums[2] else null
    }

    private fun showBlankDialog() {
        val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
        val blanksJson = prefs.getString("data", "{}")
        val blanks = JSONObject(blanksJson!!)

        val blankNames = blanks.keys().asSequence().toList()

        val dialogView = layoutInflater.inflate(R.layout.dialog_blank, null)
        val blankInput = dialogView.findViewById<EditText>(R.id.blankInput)
        val blankSpinner = dialogView.findViewById<Spinner>(R.id.blankSpinner)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, blankNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        blankSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Select or Create Blank")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val typedName = blankInput.text.toString().trim()
                val selectedName = blankSpinner.selectedItem?.toString()
                val blankId = if (typedName.isNotEmpty()) typedName else selectedName
                if (blankId != null) saveGauge(blankId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveGauge(blankId: String) {
        val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
        val blanksJson = prefs.getString("data", "{}")
        val blanks = JSONObject(blanksJson!!)

        val readings = if (blanks.has(blankId)) {
            blanks.getJSONArray(blankId)
        } else {
            JSONArray()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        val reading = JSONObject().apply {
            put("pressure", lastPressure)
            put("time", timestamp)
        }

        readings.put(reading)
        blanks.put(blankId, readings)

        prefs.edit().putString("data", blanks.toString()).apply()

        val intent = Intent(this, BlankDataActivity::class.java)
        intent.putExtra("blankId", blankId)
        startActivity(intent)
    }

    private fun loadBlanksIntoDrawer() {
        val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
        val blanksJson = prefs.getString("data", "{}")
        val blanks = JSONObject(blanksJson!!)

        val blankNames = blanks.keys().asSequence().toList()

        val menu = navigationView.menu
        menu.removeGroup(R.id.blankGroup)

        val groupId = R.id.blankGroup

        for (blank in blankNames) {
            val item = menu.add(groupId, Menu.NONE, Menu.NONE, blank)
            item.setIcon(R.drawable.ic_blank)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_clear_data -> {
                    confirmClearData()
                    true
                }

                else -> {
                    val blankId = item.title.toString()
                    val intent = Intent(this, BlankDataActivity::class.java)
                    intent.putExtra("blankId", blankId)
                    startActivity(intent)
                    drawerLayout.closeDrawers()
                    true
                }
            }
        }
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will remove all saved blanks and readings. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
                prefs.edit().clear().apply()
                loadBlanksIntoDrawer()
                Toast.makeText(this, "All blanks cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
