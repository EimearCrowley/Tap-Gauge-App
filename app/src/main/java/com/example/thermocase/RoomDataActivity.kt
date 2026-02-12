package com.example.thermocase

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONObject

class RoomDataActivity : AppCompatActivity() {

    // Formatter for X-axis timestamps
    class TimeAxisFormatter(private val timestamps: List<String>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index in timestamps.indices) {
                timestamps[index]
            } else ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.room_data)

        val roomName = intent.getStringExtra("roomName") ?: return

        val title = findViewById<TextView>(R.id.roomTitle)
        val listView = findViewById<ListView>(R.id.readingList)
        val homeButton = findViewById<Button>(R.id.homeButton)

        title.text = roomName

        val prefs = getSharedPreferences("rooms", MODE_PRIVATE)
        val roomsJson = prefs.getString("data", "{}")
        val rooms = JSONObject(roomsJson!!)

        val readings = rooms.getJSONArray(roomName)

        // -----------------------------
        // Build list of readings (existing)
        // -----------------------------
        val items = mutableListOf<String>()
        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            items.add(
                "Temp: ${r.getString("temp")} °C\n" +
                        "Hum: ${r.getString("hum")} %\n" +
                        "Time: ${r.getString("time")}"
            )
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter

        // -----------------------------
        // Build Graphs
        // -----------------------------
        val tempChart = findViewById<LineChart>(R.id.tempChart)
        val humChart = findViewById<LineChart>(R.id.humChart)

        val tempEntries = ArrayList<Entry>()
        val humEntries = ArrayList<Entry>()
        val timestamps = ArrayList<String>()

        for (i in 0 until readings.length()) {
            val r = readings.getJSONObject(i)
            val temp = r.getString("temp").toFloat()
            val hum = r.getString("hum").toFloat()
            val time = r.getString("time")

            tempEntries.add(Entry(i.toFloat(), temp))
            humEntries.add(Entry(i.toFloat(), hum))
            timestamps.add(time)
        }

        val tempDataSet = LineDataSet(tempEntries, "Temperature (°C)").apply {
            color = Color.RED
            valueTextSize = 12f
            lineWidth = 2f
            circleRadius = 4f
        }

        val humDataSet = LineDataSet(humEntries, "Humidity (%)").apply {
            color = Color.BLUE
            valueTextSize = 12f
            lineWidth = 2f
            circleRadius = 4f
        }

        tempChart.data = LineData(tempDataSet)
        humChart.data = LineData(humDataSet)

        // Apply timestamp formatter
        val formatter = TimeAxisFormatter(timestamps)

        tempChart.xAxis.apply {
            valueFormatter = formatter
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            textSize = 10f
        }

        humChart.xAxis.apply {
            valueFormatter = formatter
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            textSize = 10f
        }

        tempChart.invalidate()
        humChart.invalidate()

        // -----------------------------
        // Home button
        // -----------------------------
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }
}

