package com.example.tapgauge

import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONObject

class BlankDataActivity : AppCompatActivity() {

    class TimeAxisFormatter(private val timestamps: List<String>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index in timestamps.indices) timestamps[index] else ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blank_data)

        val blankId = intent.getStringExtra("blankId") ?: return

        val title = findViewById<TextView>(R.id.blankTitle)
        val readingContainer = findViewById<LinearLayout>(R.id.readingContainer)
        val homeButton = findViewById<Button>(R.id.homeButton)
        val shareButton = findViewById<Button>(R.id.shareButton)
        val deleteBlankButton = findViewById<Button>(R.id.deleteBlankButton)
        val scanAgainButton = findViewById<Button>(R.id.scanAgainButton)

        title.text = blankId

        val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
        val blanksJson = prefs.getString("data", "{}")
        val blanks = JSONObject(blanksJson!!)
        val readings = blanks.getJSONArray(blankId)

        val shareBuilder = StringBuilder()
        shareBuilder.append("Blank: $blankId\n\n")

        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            val tv = TextView(this)

            val pressurePa = r.getString("pressure").toFloat() * 100
            val status = getSafetyStatus(pressurePa)

            tv.text =
                "Pressure: ${pressurePa.toInt()} Pa\n" +
                        "Status: $status\n" +
                        "Time: ${r.getString("time")}"

            tv.textSize = 18f
            tv.setPadding(0, 0, 0, 40)

            when (status) {
                "SAFE" -> tv.setTextColor(Color.GREEN)
                "WARNING" -> tv.setTextColor(Color.YELLOW)
                "DANGER" -> tv.setTextColor(Color.RED)
            }

            readingContainer.addView(tv)

            shareBuilder.append(
                "Pressure: ${pressurePa.toInt()} Pa\n" +
                        "Status: $status\n" +
                        "Time: ${r.getString("time")}\n\n"
            )
        }

        val pressureChart = findViewById<LineChart>(R.id.pressureChart)
        val pressureEntries = ArrayList<Entry>()
        val timestamps = ArrayList<String>()

        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            val pressurePa = r.getString("pressure").toFloat() * 100

            pressureEntries.add(Entry(i.toFloat(), pressurePa))
            timestamps.add(r.getString("time"))
        }

        val pressureDataSet = LineDataSet(pressureEntries, "Pressure (Pa)").apply {
            color = Color.BLUE
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(Color.BLUE)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        pressureChart.data = LineData(pressureDataSet)

        val formatter = TimeAxisFormatter(timestamps)

        fun setupChart(chart: LineChart) {
            chart.xAxis.apply {
                valueFormatter = formatter
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                setLabelCount(4, false)
            }
            chart.axisRight.isEnabled = false
            chart.setPinchZoom(true)
            chart.isDragEnabled = true
            chart.setScaleEnabled(true)
            chart.description.isEnabled = false
            chart.legend.isEnabled = true
            chart.invalidate()
        }

        setupChart(pressureChart)

        homeButton.setOnClickListener { finish() }

        shareButton.setOnClickListener {
            shareText(shareBuilder.toString())
        }

        deleteBlankButton.setOnClickListener {
            confirmDeleteBlank(blankId)
        }

        scanAgainButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private fun getSafetyStatus(pa: Float): String {
        return when {
            pa < 50000 -> "SAFE"
            pa < 100000 -> "WARNING"
            else -> "DANGER"
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Pressure Readings"))
    }

    private fun confirmDeleteBlank(blankId: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Blank")
            .setMessage("Delete all readings for $blankId?")
            .setPositiveButton("Delete") { _, _ ->
                val prefs = getSharedPreferences("blanks", MODE_PRIVATE)
                val blanksJson = prefs.getString("data", "{}")
                val blanks = JSONObject(blanksJson!!)
                blanks.remove(blankId)
                prefs.edit().putString("data", blanks.toString()).apply()
                Toast.makeText(this, "Blank deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
    }
}
