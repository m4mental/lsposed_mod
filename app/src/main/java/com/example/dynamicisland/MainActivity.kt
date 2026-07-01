package com.example.dynamicisland

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    // यह डमी फ़ंक्शन है। मॉड्यूल एक्टिव होने पर Xposed इसे हुक करके 'true' कर देगा।
    private fun isModuleActive(): Boolean {
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

        // मुख्य लेआउट
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        // 1. शीर्षक
        val title = TextView(this).apply {
            text = "Dynamic Island Calibrator"
            textSize = 22f
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)

        // 2. मॉड्यूल एक्टिव/इनएक्टिव स्टेटस टेक्स्ट (Live Status)
        val statusText = TextView(this).apply {
            if (isModuleActive()) {
                text = "● Module Status: ACTIVE"
                setTextColor(Color.GREEN)
            } else {
                text = "● Module Status: INACTIVE (Enable in LSPosed & Reboot)"
                setTextColor(Color.RED)
            }
            textSize = 14f
            setPadding(0, 0, 0, 80)
        }
        layout.addView(statusText)

        // 3. वर्टिकल पोजीशन स्लाइडर
        val currentTopMargin = sharedPref.getInt("topMargin", 8)
        val topMarginLabel = TextView(this).apply {
            text = "Vertical Position (ऊंचाई): ${currentTopMargin}dp"
            textSize = 16f
        }
        layout.addView(topMarginLabel)

        val topMarginSeekBar = SeekBar(this).apply {
            max = 100
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

        // 4. चौड़ाई स्लाइडर
        val currentWidth = sharedPref.getInt("width", 40)
        val widthLabel = TextView(this).apply {
            text = "Punch-Hole Width (चौड़ाई): ${currentWidth}dp"
            textSize = 16f
            setPadding(0, 80, 0, 0)
        }
        layout.addView(widthLabel)

        val widthSeekBar = SeekBar(this).apply {
            max = 150
            progress = currentWidth
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val realWidth = if (progress < 20) 20 else progress
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

    private fun sendLiveUpdate(topMargin: Int, width: Int) {
        val intent = Intent("com.example.dynamicisland.UPDATE_SETTINGS").apply {
            putExtra("topMargin", topMargin)
            putExtra("width", width)
        }
        sendBroadcast(intent)
    }
}
