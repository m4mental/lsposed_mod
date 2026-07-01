package com.example.dynamicisland

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    // SystemUI से आने वाला "हाँ, मैं एक्टिव हूँ" रिप्लाई सुनने के लिए रिसीवर
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.dynamicisland.REPLY_STATUS") {
                statusText.text = "● Module Status: ACTIVE"
                statusText.setTextColor(Color.GREEN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "Dynamic Island Calibrator"
            textSize = 22f
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)

        // स्टेटस टेक्स्ट इंडिकेटर (शुरुआत में लाल रंग में INACTIVE रहेगा)
        statusText = TextView(this).apply {
            text = "● Module Status: INACTIVE (Enable in LSPosed & Reboot)"
            setTextColor(Color.RED)
            textSize = 14f
            setPadding(0, 0, 0, 80)
        }
        layout.addView(statusText)

        // वर्टिकल पोजीशन स्लाइडर
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

        // चौड़ाई स्लाइडर
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

    override fun onResume() {
        super.onResume()
        // रिप्लाई रिसीवर रजिस्टर करें
        val filter = IntentFilter("com.example.dynamicisland.REPLY_STATUS")
        registerReceiver(statusReceiver, filter, 2) // RECEIVER_EXPORTED

        // SystemUI को पूछने के लिए ब्रॉडकास्ट भेजें कि क्या मॉड्यूल चल रहा है
        val intent = Intent("com.example.dynamicisland.QUERY_STATUS")
        sendBroadcast(intent)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {}
    }

    private fun sendLiveUpdate(topMargin: Int, width: Int) {
        val intent = Intent("com.example.dynamicisland.UPDATE_SETTINGS").apply {
            putExtra("topMargin", topMargin)
            putExtra("width", width)
        }
        sendBroadcast(intent)
    }
}
