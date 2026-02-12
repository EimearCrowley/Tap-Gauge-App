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

    private lateinit var tempValue: TextView
    private lateinit var humValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tempValue = findViewById(R.id.tempValue)
        humValue = findViewById(R.id.humValue)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Default placeholder values
        tempValue.text = "-- °C"
        humValue.text = "-- %"
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
        val ndef = Ndef.get(tag) ?: return

        ndef.connect()
        val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
        ndef.close()

        val text = message?.let { extractText(it) } ?: return

        val (temp, hum) = parseValues(text) ?: return

        tempValue.text = "$temp °C"
        humValue.text = "$hum %"
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

    private fun parseValues(text: String): Pair<String, String>? {
        return if (text.contains(",")) {
            val parts = text.split(",")
            val temp = parts.getOrNull(0) ?: return null
            val hum = parts.getOrNull(1) ?: return null
            Pair(temp, hum)
        } else {
            val regex = Regex("""([0-9]+(\.[0-9]+)?)""")
            val nums = regex.findAll(text).map { it.value }.toList()
            if (nums.size >= 2) Pair(nums[0], nums[1]) else null
        }
    }
}





