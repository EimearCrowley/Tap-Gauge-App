package com.example.thermocase

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONObject

class RoomDataActivity : AppCompatActivity() {

    class TimeAxisFormatter(private val timestamps: List<String>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index in timestamps.indices) timestamps[index] else ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.room_data)

        val roomName = intent.getStringExtra("roomName") ?: return

        val title = findViewById<TextView>(R.id.roomTitle)
        val readingContainer = findViewById<LinearLayout>(R.id.readingContainer)
        val homeButton = findViewById<Button>(R.id.homeButton)

        title.text = roomName

        val prefs = getSharedPreferences("rooms", MODE_PRIVATE)
        val roomsJson = prefs.getString("data", "{}")
        val rooms = JSONObject(roomsJson!!)
        val readings = rooms.getJSONArray(roomName)

        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            val tv = TextView(this)
            tv.text =
                "Temp: ${r.getString("temp")} °C\n" +
                        "Hum: ${r.getString("hum")} %\n" +
                        "Time: ${r.getString("time")}"
            tv.textSize = 18f
            tv.setPadding(0, 0, 0, 30)
            readingContainer.addView(tv)
        }

        val tempChart = findViewById<LineChart>(R.id.tempChart)
        val humChart = findViewById<LineChart>(R.id.humChart)

        val tempEntries = ArrayList<Entry>()
        val humEntries = ArrayList<Entry>()
        val timestamps = ArrayList<String>()

        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            tempEntries.add(Entry(i.toFloat(), r.getString("temp").toFloat()))
            humEntries.add(Entry(i.toFloat(), r.getString("hum").toFloat()))
            timestamps.add(r.getString("time"))
        }

        val tempDataSet = LineDataSet(tempEntries, "Temperature (°C)")
        val humDataSet = LineDataSet(humEntries, "Humidity (%)")

        tempChart.data = LineData(tempDataSet)
        humChart.data = LineData(humDataSet)

        val formatter = TimeAxisFormatter(timestamps)

        fun setupChart(chart: LineChart) {
            chart.xAxis.apply {
                valueFormatter = formatter
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                setLabelCount(4, false)
            }
            chart.setPinchZoom(true)
            chart.isDragEnabled = true
            chart.setScaleEnabled(true)
            chart.description.isEnabled = false
            chart.invalidate()
        }

        setupChart(tempChart)
        setupChart(humChart)

        homeButton.setOnClickListener { finish() }
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


