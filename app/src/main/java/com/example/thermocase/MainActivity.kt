package com.example.thermocase

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var outputText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        outputText = findViewById(R.id.outputText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        outputText.text = "Tap an NFC Thermo‑Case tag"
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )

        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val ndef = Ndef.get(tag)

        if (ndef == null) {
            outputText.text = "Tag detected, but no NDEF data"
            return
        }

        ndef.connect()
        val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
        ndef.close()

        if (message == null) {
            outputText.text = "Empty NDEF message"
            return
        }

        val text = extractText(message)

        if (text == null) {
            outputText.text = "No text record found"
        } else {
            outputText.text = parse(text)
        }
    }

    private fun extractText(message: NdefMessage): String? {
        for (record in message.records) {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT)
            ) {
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

    private fun parse(text: String): String {
        // Supports formats like:
        // "22,45"
        // "22.5,45.2"
        // "Temperature: 22.5\nRelative Humidity: 45.2"

        return if (text.contains(",")) {
            val parts = text.split(",")
            val temp = parts.getOrNull(0) ?: "?"
            val hum = parts.getOrNull(1) ?: "?"
            "Temperature: $temp °C\nHumidity: $hum %"
        } else {
            val regex = Regex("""([0-9]+(\.[0-9]+)?)""")
            val nums = regex.findAll(text).map { it.value }.toList()

            if (nums.size >= 2) {
                "Temperature: ${nums[0]} °C\nHumidity: ${nums[1]} %"
            } else {
                "Raw data: $text"
            }
        }
    }
}



