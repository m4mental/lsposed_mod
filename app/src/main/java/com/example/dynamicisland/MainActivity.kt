package com.example.dynamicisland

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

        // मुख्य लेआउट (LinearLayout)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        // 1. शीर्षक (Title)
        val title = TextView(this).apply {
            text = "Dynamic Island Calibrator"
            textSize = 22f
            setPadding(0, 0, 0, 80)
        }
        layout.addView(title)

        // 2. वर्टिकल पोजीशन स्लाइडर (Vertical Position Slider)
        val currentTopMargin = sharedPref.getInt("topMargin", 8)
        val topMarginLabel = TextView(this).apply {
            text = "Vertical Position (ऊंचाई): ${currentTopMargin}dp"
            textSize = 16f
        }
        layout.addView(topMarginLabel)

        val topMarginSeekBar = SeekBar(this).apply {
            max = 100 // अधिकतम 100dp नीचे जा सकता है
            progress = currentTopMargin
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    topMarginLabel.text = "Vertical Position (ऊंचाई): ${progress}dp"
                    sharedPref.edit().putInt("topMargin", progress).apply()
                    sendLiveUpdate(progress, sharedPref.getInt("width", 40))
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(topMarginSeekBar)

        // 3. चौड़ाई स्लाइडर (Width Slider)
        val currentWidth = sharedPref.getInt("width", 40)
        val widthLabel = TextView(this).apply {
            text = "Punch-Hole Width (चौड़ाई): ${currentWidth}dp"
            textSize = 16f
            setPadding(0, 80, 0, 0)
        }
        layout.addView(widthLabel)

        val widthSeekBar = SeekBar(this).apply {
            max = 150 // अधिकतम चौड़ाई 150dp
            progress = currentWidth
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val realWidth = if (progress < 20) 20 else progress // न्यूनतम 20dp
                    widthLabel.text = "Punch-Hole Width (चौड़ाई): ${realWidth}dp"
                    sharedPref.edit().putInt("width", realWidth).apply()
                    sendLiveUpdate(sharedPref.getInt("topMargin", 8), realWidth)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(widthSeekBar)

        setContentView(layout)
    }

    // SystemUI को लाइव बदलाव भेजने के लिए ब्रॉडकास्ट फंक्शन
    private fun sendLiveUpdate(topMargin: Int, width: Int) {
        val intent = Intent("com.example.dynamicisland.UPDATE_SETTINGS").apply {
            putExtra("topMargin", topMargin)
            putExtra("width", width)
        }
        sendBroadcast(intent)
    }
}
