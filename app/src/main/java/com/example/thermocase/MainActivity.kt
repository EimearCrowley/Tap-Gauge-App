package com.example.thermocase

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var tempValue: TextView
    private lateinit var humValue: TextView
    private lateinit var recordButton: Button
    private lateinit var startScanButton: Button

    private var lastTemp: String? = null
    private var lastHum: String? = null

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tempValue = findViewById(R.id.tempValue)
        humValue = findViewById(R.id.humValue)
        recordButton = findViewById(R.id.recordButton)
        startScanButton = findViewById(R.id.startScanButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Start / Stop scanning toggle
        startScanButton.setOnClickListener {
            if (!isScanning) {
                isScanning = true
                startScanButton.text = "Stop Scanning"
                recordButton.visibility = View.GONE
                tempValue.text = "-- °C"
                humValue.text = "-- %"
                Toast.makeText(this, "Ready to scan NFC tag", Toast.LENGTH_SHORT).show()
            } else {
                isScanning = false
                startScanButton.text = "Start Scan"
                Toast.makeText(this, "Scanning stopped", Toast.LENGTH_SHORT).show()
            }
        }

        recordButton.setOnClickListener { showRoomDialog() }
    }

    override fun onResume() {
        super.onResume()

        // Delay foreground dispatch by 1 second to prevent Samsung NFC hijack
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

        ndef.connect()
        val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
        ndef.close()

        val text = message?.let { extractText(it) } ?: return

        val (temp, hum) = parseValues(text) ?: return

        tempValue.text = "$temp °C"
        humValue.text = "$hum %"

        lastTemp = temp
        lastHum = hum

        recordButton.visibility = View.VISIBLE

        isScanning = false
        startScanButton.text = "Start Scan"
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

    private fun parseValues(text: String): Pair<String, String>? {
        val regex = Regex("""([0-9]+(\.[0-9]+)?)""")
        val nums = regex.findAll(text).map { it.value }.toList()
        return if (nums.size >= 2) Pair(nums[0], nums[1]) else null
    }

    private fun showRoomDialog() {
        val prefs = getSharedPreferences("rooms", MODE_PRIVATE)
        val roomsJson = prefs.getString("data", "{}")
        val rooms = JSONObject(roomsJson!!)

        val roomNames = rooms.keys().asSequence().toList()

        val dialogView = layoutInflater.inflate(R.layout.dialog_room, null)
        val roomInput = dialogView.findViewById<EditText>(R.id.roomInput)
        val roomSpinner = dialogView.findViewById<Spinner>(R.id.roomSpinner)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roomNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roomSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Select or Create Room")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val typedName = roomInput.text.toString().trim()
                val selectedName = roomSpinner.selectedItem?.toString()
                val roomName = if (typedName.isNotEmpty()) typedName else selectedName
                if (roomName != null) saveReading(roomName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveReading(roomName: String) {
        // STOP SCANNING BEFORE LEAVING MAINACTIVITY
        isScanning = false
        nfcAdapter?.disableForegroundDispatch(this)

        val prefs = getSharedPreferences("rooms", MODE_PRIVATE)
        val roomsJson = prefs.getString("data", "{}")
        val rooms = JSONObject(roomsJson!!)

        val readings = if (rooms.has(roomName)) {
            rooms.getJSONArray(roomName)
        } else {
            JSONArray()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        val reading = JSONObject().apply {
            put("temp", lastTemp)
            put("hum", lastHum)
            put("time", timestamp)
        }

        readings.put(reading)
        rooms.put(roomName, readings)

        prefs.edit().putString("data", rooms.toString()).apply()

        val intent = Intent(this, RoomDataActivity::class.java)
        intent.putExtra("roomName", roomName)
        startActivity(intent)
    }

}




