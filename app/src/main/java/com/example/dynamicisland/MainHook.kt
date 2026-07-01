package com.example.dynamicisland

import android.animation.ValueAnimator
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Random

class MainHook : IXposedHookLoadPackage {

    private var islandView: FrameLayout? = null
    private var islandText: TextView? = null
    private var visualizerLayout: LinearLayout? = null // रीयल-टाइम म्यूजिक वेव के लिए
    
    // कॉन्फ़िगर की गई सेटिंग्स
    private var configuredTopMargin = 8
    private var configuredWidth = 40
    private var configuredHeight = 40
    private var configuredRadius = 20

    private var activeMode = "idle" // idle, charging, media, notification, timer
    private val handler = Handler(Looper.getMainLooper())
    private var waveRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var countdownSecs = 60

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // 1. कैलिब्रेशन ऐप के लिए एक्टिव स्टेटस हुक
        if (lpparam.packageName == "com.example.dynamicisland") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.example.dynamicisland.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("Dynamic Island: Failed to hook MainActivity - " + e.message)
            }
            return
        }

        // 2. System UI हुक
        if (lpparam.packageName == "com.android.systemui") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                    lpparam.classLoader,
                    "onFinishInflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val statusBarView = param.thisObject as ViewGroup
                            val context = statusBarView.context

                            Handler(Looper.getMainLooper()).post {
                                try {
                                    loadSavedSettings(context)
                                    createDynamicIsland(context, statusBarView)
                                    registerEventsAndSimulations(context)
                                } catch (e: Exception) {
                                    XposedBridge.log("Dynamic Island: Setup error - " + e.message)
                                }
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("Dynamic Island: Hook setup error - " + e.message)
            }
        }
    }

    private fun loadSavedSettings(context: Context) {
        val pref = XSharedPreferences("com.example.dynamicisland", "dynamic_island_prefs")
        pref.reload()
        configuredTopMargin = pref.getInt("topMargin", 8)
        configuredWidth = pref.getInt("width", 40)
        configuredHeight = pref.getInt("height", 40)
        configuredRadius = pref.getInt("radius", 20)
    }

    private fun createDynamicIsland(context: Context, parent: ViewGroup) {
        if (islandView != null) return

        // 1. मुख्य आइलैंड व्यू
        islandView = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(context, configuredRadius).toFloat()
            }
            elevation = dpToPx(context, 6).toFloat()
            isClickable = true
            isFocusable = true
        }

        // 2. टेक्स्ट व्यू
        islandText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            alpha = 0f
            setPadding(dpToPx(context, 15), 0, dpToPx(context, 15), 0)
        }
        islandView?.addView(islandText)

        // 3. रीयल-टाइम विजुअल इक्वलाइज़र वेवफॉर्म्स (Equalizer Visualizer Wave)
        visualizerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f // शुरुआत में अदृश्य
            setPadding(0, 0, dpToPx(context, 15), 0)
            
            // 4 गतिशील बार्स जोड़ना
            for (i in 0..3) {
                val bar = View(context).apply {
                    setBackgroundColor(Color.parseColor("#00E676")) // नियॉन ग्रीन
                }
                val params = LinearLayout.LayoutParams(dpToPx(context, 3), dpToPx(context, 5)).apply {
                    setMargins(dpToPx(context, 2), 0, dpToPx(context, 2), 0)
                }
                addView(bar, params)
            }
        }
        
        val visualizerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.RIGHT or Gravity.CENTER_VERTICAL
        )
        islandView?.addView(visualizerLayout, visualizerParams)

        // 4. जेस्चर और टच कंट्रोल (Swipe to Adjust Volume & Haptic Ticks)
        var startX = 0f
        islandView?.setOnTouchListener { view, event ->
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    performHapticTick(context) // टैक्टाइल हैप्टिक फीडबैक
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX - startX
                    if (Math.abs(diffX) > 60) { // स्वाइप की सीमा
                        if (diffX > 0) {
                            // राइट स्वाइप: वॉल्यूम बढ़ाएं
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        } else {
                            // लेफ्ट स्वाइप: वॉल्यूम घटाएं
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }
                        startX = event.rawX // रीसेट करें
                        performHapticTick(context)
                    }
                }
            }
            false
        }

        // लेआउट पैरामीटर्स सेट करें
        val parentParams = FrameLayout.LayoutParams(dpToPx(context, configuredWidth), dpToPx(context, configuredHeight)).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = dpToPx(context, configuredTopMargin)
        }
        parent.addView(islandView, parentParams)
    }

    // सिस्टम इवेंट्स और सिम्युलेटर ब्रॉडकास्ट सुनना
    private fun registerEventsAndSimulations(context: Context) {
        val filter = IntentFilter().apply {
            addAction("com.example.dynamicisland.UPDATE_SETTINGS")
            addAction("com.example.dynamicisland.QUERY_STATUS")
            addAction("com.example.dynamicisland.SIMULATE_STATE")
        }
        val flagExported = 2 // Context.RECEIVER_EXPORTED

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.dynamicisland.UPDATE_SETTINGS" -> {
                        configuredTopMargin = intent.getIntExtra("topMargin", 8)
                        configuredWidth = intent.getIntExtra("width", 40)
                        configuredHeight = intent.getIntExtra("height", 40)
                        configuredRadius = intent.getIntExtra("radius", 20)
                        
                        if (activeMode == "idle") {
                            applyModeConfig(ctx, configuredWidth, configuredHeight, 0f, null, false)
                        }
                    }
                    "com.example.dynamicisland.QUERY_STATUS" -> {
                        ctx.sendBroadcast(Intent("com.example.dynamicisland.REPLY_STATUS"))
                    }
                    "com.example.dynamicisland.SIMULATE_STATE" -> {
                        val state = intent.getStringExtra("state") ?: "idle"
                        activeMode = state
                        handleStateTransition(ctx, state)
                    }
                }
            }
        }, filter, flagExported)
    }

    // अलग-अलग मोड्स (Contextual States) के संक्रमण को हैंडल करना
    private fun handleStateTransition(context: Context, state: String) {
        // चल रहे एनिमेशन थ्रेड्स को रोकें
        waveRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable?.let { handler.removeCallbacks(it) }
        visualizerLayout?.alpha = 0f

        when (state) {
            "idle" -> {
                applyModeConfig(context, configuredWidth, configuredHeight, 0f, null, false)
                triggerNothingOSGlyph(context, "stop")
            }
            "charging" -> {
                // ⚡ 45W फास्ट चार्जिंग सिमुलेशन
                applyModeConfig(context, 230, 45, 1f, "⚡ Charging 45W • 82%", false)
                triggerNothingOSGlyph(context, "charging")
            }
            "media" -> {
                // विजुअल म्यूजिक वेव एनीमेशन शुरू करें
                applyModeConfig(context, 240, 45, 1f, "♫ Now Playing: Nothing OS", true)
                startEqualizerWaveAnimation(context)
                triggerNothingOSGlyph(context, "media")
            }
            "notification" -> {
                // व्हाट्सएप डायनामिक विजेट (त्वरित उत्तर "Quick Reply" सिमुलेशन)
                applyModeConfig(context, 260, 70, 1f, "WhatsApp: Aryan\nHello, check this out!", false)
                triggerNothingOSGlyph(context, "notification")
                
                // 4 सेकंड बाद खुद ब खुद हाइड हो जाना (Auto-collapse)
                handler.postDelayed({
                    if (activeMode == "notification") {
                        activeMode = "idle"
                        handleStateTransition(context, "idle")
                    }
                }, 4000)
            }
            "timer" -> {
                // लाइव टाइमर काउंटडाउन
                countdownSecs = 60
                startTimerCountdownAnimation(context)
                triggerNothingOSGlyph(context, "timer")
            }
        }
    }

    // रीयल-टाइम वेवफॉर्म एनीमेशन
    private fun startEqualizerWaveAnimation(context: Context) {
        visualizerLayout?.alpha = 1f
        val random = Random()
        
        waveRunnable = object : Runnable {
            override fun run() {
                visualizerLayout?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val bar = layout.getChildAt(i)
                        val newHeight = dpToPx(context, random.nextInt(20) + 5) // रैंडम ऊंचाई
                        bar.layoutParams = (bar.layoutParams as LinearLayout.LayoutParams).apply {
                            height = newHeight
                        }
                    }
                    layout.requestLayout()
                }
                handler.postDelayed(this, 120) // 120ms का रिफ्रेश रेट
            }
        }
        handler.post(waveRunnable!!)
    }

    // लाइव टाइमर काउंटडाउन
    private fun startTimerCountdownAnimation(context: Context) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (countdownSecs >= 0) {
                    val text = "Timer • 00:${String.format("%02d", countdownSecs)}"
                    applyModeConfig(context, 180, 45, 1f, text, false)
                    countdownSecs--
                    handler.postDelayed(this, 1000) // प्रति सेकंड रिफ्रेश
                } else {
                    activeMode = "idle"
                    handleStateTransition(context, "idle")
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    // ऐनिमेटेड और इलास्टिक पिल अलाइनमेंट
    private fun applyModeConfig(context: Context, targetW: Int, targetH: Int, textAlpha: Float, labelText: String?, showWave: Boolean) {
        val island = islandView ?: return
        val text = islandText ?: return

        labelText?.let { text.text = it }

        val startW = island.width
        val endW = dpToPx(context, targetW)

        val startH = island.height
        val endH = dpToPx(context, targetH)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f) // लचीला इलास्टिक बाउंस
        }

        animator.addUpdateListener { valAnim ->
            val fraction = valAnim.animatedValue as Float
            val currentW = (startW + (endW - startW) * fraction).toInt()
            val currentH = (startH + (endH - startH) * fraction).toInt()

            island.layoutParams = (island.layoutParams as FrameLayout.LayoutParams).apply {
                width = currentW
                height = currentH
                cornerRadius = dpToPx(context, configuredRadius).toFloat()
            }
            island.requestLayout()
            text.alpha = textAlpha * fraction
            if (showWave) visualizerLayout?.alpha = fraction else visualizerLayout?.alpha = 0f
        }

        animator.start()
    }

    // Nothing OS के Glyph LEDs स्ट्रिप्स को रिफ्लेक्शन से सिंक करना
    private fun triggerNothingOSGlyph(context: Context, action: String) {
        try {
            // Nothing OS का Ketchum System Proxy सर्विस लोड करें (यदि उपलब्ध हो)
            val glyphClazz = Class.forName("com.nothing.ketchum.GlyphManager")
            val getInstance = glyphClazz.getMethod("getInstance", Context::class.java)
            val glyphManager = getInstance.invoke(null, context)

            // डिवाइस पर Glyph LEDs को रिफ्लेक्शन से चालू करें
            val initMethod = glyphClazz.getMethod("init")
            initMethod.invoke(glyphManager)

            XposedBridge.log("Dynamic Island: Glyph Sync triggered for state: $action")
            // रीयल-टाइम में लाइटिंग पैटर्न्स यहाँ से सिंक किए जा सकते हैं
        } catch (e: Throwable) {
            // यदि यह गैर-Nothing फ़ोन है तो क्रैश नहीं होगा
            XposedBridge.log("Dynamic Island: Non-Nothing device or SDK missing. Skipping Glyph interface.")
        }
    }

    // हैप्टिक फीडबैक वाइब्रेशन
    private fun performHapticTick(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
