package com.example.dynamicisland

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var contentFrame: LinearLayout
    private var isModuleActive = false

    // 🟢 डमी फ़ंक्शन को 'public' (कोटलिन में डिफ़ॉल्ट) किया गया है ताकि कंपाइलर इसे इनलाइन (Inline) करके मिटा न सके (FIXED!)
    fun isXposedActive(): Boolean {
        return false
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            if (i.action == "com.example.dynamicisland.REPLY_STATUS") {
                isModuleActive = true
                statusText.text = "● Module Status: ACTIVE"
                statusText.setTextColor(Color.GREEN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#0C0C0C"))
        }

        val title = TextView(this).apply {
            text = "Island Control Panel"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-condensed-light", Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        mainLayout.addView(title)

        statusText = TextView(this).apply {
            if (isXposedActive()) {
                text = "● Module Status: ACTIVE"
                setTextColor(Color.GREEN)
            } else {
                text = "● Module Status: INACTIVE (Enable in LSPosed & Reboot)"
                setTextColor(Color.RED)
            }
            textSize = 13f
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(statusText)

        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 30)
        }

        val btnTabCalibration = Button(this).apply {
            text = "Calibration Settings"
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setTextColor(Color.WHITE)
        }

        val btnTabSimulator = Button(this).apply {
            text = "Live Simulation"
            setBackgroundColor(Color.parseColor("#111111"))
            setTextColor(Color.GRAY)
        }

        val paramCalib = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }
        val paramSim = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }

        tabContainer.addView(btnTabCalibration, paramCalib)
        tabContainer.addView(btnTabSimulator, paramSim)
        mainLayout.addView(tabContainer)

        contentFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(contentFrame)

        loadCalibrationSettings()

        btnTabCalibration.setOnClickListener {
            btnTabCalibration.setBackgroundColor(Color.parseColor("#1E1E1E"))
            btnTabCalibration.setTextColor(Color.WHITE)
            btnTabSimulator.setBackgroundColor(Color.parseColor("#111111"))
            btnTabSimulator.setTextColor(Color.GRAY)
            loadCalibrationSettings()
        }

        btnTabSimulator.setOnClickListener {
            btnTabSimulator.setBackgroundColor(Color.parseColor("#1E1E1E"))
            btnTabSimulator.setTextColor(Color.WHITE)
            btnTabCalibration.setBackgroundColor(Color.parseColor("#111111"))
            btnTabCalibration.setTextColor(Color.GRAY)
            loadSimulatorSettings()
        }

        setContentView(mainLayout)
    }

    private fun loadCalibrationSettings() {
        contentFrame.removeAllViews()
        val sharedPref = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

        fun addSlider(label: String, key: String, maxVal: Int, minVal: Int, unit: String) {
            val currentVal = sharedPref.getInt(key, minVal)
            val labelView = TextView(this).apply {
                text = "$label: $currentVal$unit"
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 20, 0, 10)
            }
            contentFrame.addView(labelView)

            val seekBar = SeekBar(this).apply {
                setMax(maxVal - minVal)
                setProgress(currentVal - minVal)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        val realValue = progress + minVal
                        labelView.text = "$label: $realValue$unit"
                        sharedPref.edit().putInt(key, realValue).apply()
                        sendLiveUpdate()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            contentFrame.addView(seekBar)
        }

        addSlider("Vertical Offset (Y-axis)", "topMargin", 150, 0, "dp")
        addSlider("Punch-Hole Width", "width", 300, 40, "dp")
        addSlider("Punch-Hole Height", "height", 100, 20, "dp")
        addSlider("Corner Radius", "radius", 50, 0, "dp")
    }

    private fun loadSimulatorSettings() {
        contentFrame.removeAllViews()

        val desc = TextView(this).apply {
            text = "अलग-अलग मोड्स को स्क्रीन पर रीयल-टाइम में टेस्ट करें:"
            setTextColor(Color.LIGHTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, 40)
        }
        contentFrame.addView(desc)

        fun createSimButton(title: String, stateValue: String, color: String) {
            val btn = Button(this).apply {
                text = title
                setBackgroundColor(Color.parseColor(color))
                setTextColor(Color.WHITE)
                setPadding(0, 30, 0, 30)
            }
            btn.setOnClickListener {
                val intent = Intent("com.example.dynamicisland.SIMULATE_STATE").apply {
                    putExtra("state", stateValue)
                }
                sendBroadcast(intent)
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10
                bottomMargin = 15
            }
            contentFrame.addView(btn, params)
        }

        createSimButton("Simulate CHARGING (⚡ 45W Fast Charging)", "charging", "#4CAF50")
        createSimButton("Simulate MEDIA (Equalizer Visualizer Wave)", "media", "#2196F3")
        createSimButton("Simulate NOTIFICATION (WhatsApp Rich Widget)", "notification", "#9C27B0")
        createSimButton("Simulate TIMER (Live Countdown)", "timer", "#FF9800")
        createSimButton("Simulate IDLE Mode (Hide Behind Camera)", "idle", "#333333")
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.dynamicisland.REPLY_STATUS")
        
        safeRegisterReceiver(this, statusReceiver, filter)
        
        sendBroadcast(Intent("com.example.dynamicisland.QUERY_STATUS"))
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {}
    }

    private fun sendLiveUpdate() {
        val sharedPref = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)
        val intent = Intent("com.example.dynamicisland.UPDATE_SETTINGS").apply {
            putExtra("topMargin", sharedPref.getInt("topMargin", 8))
            putExtra("width", sharedPref.getInt("width", 40))
            putExtra("height", sharedPref.getInt("height", 40))
            putExtra("radius", sharedPref.getInt("radius", 20))
        }
        sendBroadcast(intent)
    }

    private fun safeRegisterReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val method = Context::class.java.getMethod(
                    "registerReceiver",
                    BroadcastReceiver::class.java,
                    IntentFilter::class.java,
                    Int::class.java
                )
                method.invoke(context, receiver, filter, 2)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Throwable) {
            context.registerReceiver(receiver, filter)
        }
    }
}
