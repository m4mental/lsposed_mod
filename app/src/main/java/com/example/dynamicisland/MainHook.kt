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
import android.os.Build
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
    private var visualizerLayout: LinearLayout? = null
    
    private var configuredTopMargin = 8
    private var configuredWidth = 40
    private var configuredHeight = 40
    private var configuredRadius = 20

    private var activeMode = "idle"
    private val handler = Handler(Looper.getMainLooper())
    private var waveRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var countdownSecs = 60

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.example.dynamicisland") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.example.dynamicisland.MainActivity",
                    lpparam.classLoader,
                    "isXposedActive", // 🟢 सही डमी फ़ंक्शन हुक नेम
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.setResult(true)
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("Dynamic Island: Failed to hook MainActivity - " + e.message)
            }
            return
        }

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

        islandView = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setCornerRadius(dpToPx(context, configuredRadius).toFloat())
            }
            elevation = dpToPx(context, 6).toFloat()
            isClickable = true
            isFocusable = true
        }

        islandText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setGravity(Gravity.CENTER_VERTICAL or Gravity.LEFT)
            alpha = 0f
            setPadding(dpToPx(context, 15), 0, dpToPx(context, 15), 0)
        }
        islandView?.addView(islandText)

        visualizerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(0, 0, dpToPx(context, 15), 0)
            
            for (i in 0..3) {
                val bar = View(context).apply {
                    setBackgroundColor(Color.parseColor("#00E676"))
                }
                val params = LinearLayout.LayoutParams(dpToPx(context, 3), dpToPx(context, 5)).apply {
                    leftMargin = dpToPx(context, 2)
                    rightMargin = dpToPx(context, 2)
                }
                addView(bar, params)
            }
        }
        
        val visualizerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.RIGHT or Gravity.CENTER_VERTICAL
        )
        islandView?.addView(visualizerLayout, visualizerParams)

        var startX = 0f
        islandView?.setOnTouchListener { _, event ->
            val ev = event ?: return@setOnTouchListener false
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX
                    performHapticTick(context)
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = ev.rawX - startX
                    if (Math.abs(diffX) > 60) {
                        if (diffX > 0) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                        } else {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                        }
                        startX = ev.rawX
                        performHapticTick(context)
                    }
                }
            }
            false
        }

        val parentParams = FrameLayout.LayoutParams(dpToPx(context, configuredWidth), dpToPx(context, configuredHeight)).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = dpToPx(context, configuredTopMargin)
        }
        parent.addView(islandView, parentParams)
    }

    private fun registerEventsAndSimulations(context: Context) {
        val filter = IntentFilter().apply {
            addAction("com.example.dynamicisland.UPDATE_SETTINGS")
            addAction("com.example.dynamicisland.QUERY_STATUS")
            addAction("com.example.dynamicisland.SIMULATE_STATE")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val c = ctx ?: return
                val i = intent ?: return
                val action = i.action ?: return

                when (action) {
                    "com.example.dynamicisland.UPDATE_SETTINGS" -> {
                        configuredTopMargin = i.getIntExtra("topMargin", 8)
                        configuredWidth = i.getIntExtra("width", 40)
                        configuredHeight = i.getIntExtra("height", 40)
                        configuredRadius = i.getIntExtra("radius", 20)
                        
                        if (activeMode == "idle") {
                            applyModeConfig(ctx, configuredWidth, configuredHeight, 0f, null, false)
                        }
                    }
                    "com.example.dynamicisland.QUERY_STATUS" -> {
                        ctx.sendBroadcast(Intent("com.example.dynamicisland.REPLY_STATUS"))
                    }
                    "com.example.dynamicisland.SIMULATE_STATE" -> {
                        val state = i.getStringExtra("state") ?: "idle"
                        activeMode = state
                        handleStateTransition(ctx, state)
                    }
                }
            }
        }

        safeRegisterReceiver(context, receiver, filter)
    }

    private fun handleStateTransition(context: Context, state: String) {
        waveRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable?.let { handler.removeCallbacks(it) }
        visualizerLayout?.alpha = 0f

        when (state) {
            "idle" -> {
                applyModeConfig(context, configuredWidth, configuredHeight, 0f, null, false)
            }
            "charging" -> {
                applyModeConfig(context, 230, 45, 1f, "⚡ Charging 45W • 82%", false)
            }
            "media" -> {
                applyModeConfig(context, 240, 45, 1f, "♫ Now Playing: Nothing OS", true)
                startEqualizerWaveAnimation(context)
            }
            "notification" -> {
                applyModeConfig(context, 260, 70, 1f, "WhatsApp: Aryan\nHello, check this out!", false)
                
                handler.postDelayed({
                    if (activeMode == "notification") {
                        activeMode = "idle"
                        handleStateTransition(context, "idle")
                    }
                }, 4000)
            }
            "timer" -> {
                countdownSecs = 60
                startTimerCountdownAnimation(context)
            }
        }
    }

    private fun startEqualizerWaveAnimation(context: Context) {
        visualizerLayout?.alpha = 1f
        val random = Random()
        
        waveRunnable = object : Runnable {
            override fun run() {
                visualizerLayout?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val bar = layout.getChildAt(i)
                        val newHeight = dpToPx(context, random.nextInt(20) + 5)
                        bar.layoutParams = (bar.layoutParams as LinearLayout.LayoutParams).apply {
                            height = newHeight
                        }
                    }
                    layout.requestLayout()
                }
                handler.postDelayed(this, 120)
            }
        }
        handler.post(waveRunnable!!)
    }

    private fun startTimerCountdownAnimation(context: Context) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (countdownSecs >= 0) {
                    val text = "Timer • 00:${String.format("%02d", countdownSecs)}"
                    applyModeConfig(context, 180, 45, 1f, text, false)
                    countdownSecs--
                    handler.postDelayed(this, 1000)
                } else {
                    activeMode = "idle"
                    handleStateTransition(context, "idle")
                }
            }
        }
        handler.post(timerRunnable!!)
    }

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
            interpolator = OvershootInterpolator(1.2f)
        }

        animator.addUpdateListener { valAnim ->
            val fraction = valAnim.animatedFraction
            val currentW = (startW + (endW - startW) * fraction).toInt()
            val currentH = (startH + (endH - startH) * fraction).toInt()

            island.layoutParams = (island.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = currentW
                height = currentH
            }
            
            (island.background as? GradientDrawable)?.setCornerRadius(dpToPx(context, configuredRadius).toFloat())
            
            island.requestLayout()
            text.setAlpha(textAlpha * fraction)
            if (showWave) visualizerLayout?.setAlpha(fraction) else visualizerLayout?.setAlpha(0f)
        }

        animator.start()
    }

    private fun performHapticTick(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
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
            XposedBridge.log("Dynamic Island: Safe Receiver Registration failed - " + e.message)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
