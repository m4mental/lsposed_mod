package com.example.dynamicisland

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {

    private var islandView: FrameLayout? = null
    private var islandText: TextView? = null
    private var isExpanded = false
    private var isPopupState = false // क्या आइलैंड अभी बड़े पॉप-अप मोड में है

    // डिफ़ॉल्ट कॉन्फ़िगरेशन (ऐप से लाइव अपडेट होगा)
    private var configuredTopMargin = 8
    private var configuredWidth = 40

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        
        // 1. यदि हमारा खुद का कैलिब्रेशन ऐप लोड हो रहा है, तो एक्टिव स्टेटस को 'true' पर हुक करें
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
                XposedBridge.log("Dynamic Island: MainActivity successfully hooked for Active Status.")
            } catch (e: Throwable) {
                XposedBridge.log("Dynamic Island: Failed to hook MainActivity status - " + e.message)
            }
            return
        }

        // 2. यदि System UI लोड हो रहा है
        if (lpparam.packageName == "com.android.systemui") {
            try {
                XposedBridge.log("Dynamic Island: Loading module into System UI...")

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
                                    registerSystemEvents(context)
                                    registerLiveSettingsReceiver(context)
                                } catch (e: Exception) {
                                    XposedBridge.log("Dynamic Island: Initialization failed - " + e.message)
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
    }

    private fun createDynamicIsland(context: Context, parent: ViewGroup) {
        if (islandView != null) return

        islandView = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(context, 20).toFloat()
            }
            elevation = dpToPx(context, 6).toFloat()
            isClickable = true
            isFocusable = true
        }

        islandText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            alpha = 0f
            setPadding(dpToPx(context, 12), 0, dpToPx(context, 12), 0)
        }

        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        islandView?.addView(islandText, textParams)

        val currentSizeWidth = dpToPx(context, configuredWidth)
        val currentSizeHeight = dpToPx(context, 40)
        
        val parentParams = FrameLayout.LayoutParams(currentSizeWidth, currentSizeHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = dpToPx(context, configuredTopMargin)
        }

        // 1. सिंगल टैप फीचर (Tap to Open App)
        islandView?.setOnClickListener {
            if (isPopupState) {
                // यदि बड़ा पॉप-अप खुला है, तो टैप करने पर वापस छोटा कर दें
                collapseIsland(context)
            } else {
                // अन्यथा कैलिब्रेटर ऐप खोलें
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.example.dynamicisland")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    XposedBridge.log("Dynamic Island: Cannot launch calibrator - " + e.message)
                }
            }
        }

        // 2. प्रेस एंड होल्ड फीचर (Press & Hold to Expand into Popup Window)
        islandView?.setOnLongClickListener {
            if (!isExpanded && !isPopupState) {
                expandToPopup(context)
            }
            true
        }

        parent.addView(islandView, parentParams)
        XposedBridge.log("Dynamic Island: View successfully initialized over Nothing Phone 2a punch-hole.")
    }

    // 3. चार्जिंग अलर्ट सिस्टम (Power Connection Listener)
    private fun registerSystemEvents(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val flagExported = 2 // Context.RECEIVER_EXPORTED

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    when (intent.action) {
                        Intent.ACTION_POWER_CONNECTED -> {
                            // बैटरी का लाइव प्रतिशत (Sync Battery Percentage)
                            val batteryIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                            XposedBridge.log("Dynamic Island Triggered: Charging connected at $level%")
                            triggerIslandAnimation(ctx, "Charging • $level%")
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            XposedBridge.log("Dynamic Island Triggered: Charging disconnected")
                            triggerIslandAnimation(ctx, "Charger Disconnected")
                        }
                    }
                } catch (e: Exception) {
                    XposedBridge.log("Dynamic Island Event Error: " + e.message)
                }
            }
        }, filter, flagExported)
    }

    // ब्रॉडकास्ट लाइव अपडेट रिसीवर (स्लाइडर के लिए)
    private fun registerLiveSettingsReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction("com.example.dynamicisland.UPDATE_SETTINGS")
            addAction("com.example.dynamicisland.QUERY_STATUS")
        }
        val flagExported = 2 // Context.RECEIVER_EXPORTED

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.dynamicisland.UPDATE_SETTINGS" -> {
                        val newTopMargin = intent.getIntExtra("topMargin", 8)
                        val newWidth = intent.getIntExtra("width", 40)

                        configuredTopMargin = newTopMargin
                        configuredWidth = newWidth

                        if (!isExpanded && !isPopupState) {
                            islandView?.let { view ->
                                val params = view.layoutParams as FrameLayout.LayoutParams
                                params.topMargin = dpToPx(ctx, newTopMargin)
                                params.width = dpToPx(ctx, newWidth)
                                view.layoutParams = params
                                view.requestLayout()
                            }
                        }
                    }
                    "com.example.dynamicisland.QUERY_STATUS" -> {
                        val replyIntent = Intent("com.example.dynamicisland.REPLY_STATUS")
                        ctx.sendBroadcast(replyIntent)
                    }
                }
            }
        }, filter, flagExported)
    }

    // चार्जिंग के लिए इलास्टिक एनिमेशन (बड़ा होकर ऑटो-छोटा होना)
    private fun triggerIslandAnimation(context: Context, text: String) {
        val island = islandView ?: return
        val textView = islandText ?: return
        if (isExpanded || isPopupState) return

        isExpanded = true
        textView.text = text

        val startWidth = dpToPx(context, configuredWidth)
        val endWidth = dpToPx(context, 200) // पिल चौड़ाई

        val startHeight = dpToPx(context, 40)
        val endHeight = dpToPx(context, 42)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.4f)
        }

        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float
            val currentWidth = (startWidth + (endWidth - startWidth) * fraction).toInt()
            val currentHeight = (startHeight + (endHeight - startHeight) * fraction).toInt()

            island.layoutParams = (island.layoutParams as FrameLayout.LayoutParams).apply {
                width = currentWidth
                height = currentHeight
            }
            island.requestLayout()
            textView.alpha = fraction
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                Handler(Looper.getMainLooper()).postDelayed({
                    collapseIsland(context)
                }, 3000) // 3 सेकंड तक प्रदर्शित करें
            }
        })

        animator.start()
    }

    // प्रेस एंड होल्ड द्वारा बड़ी पॉप-अप विंडो में विस्तार (Expand to Interactive Control)
    private fun expandToPopup(context: Context) {
        val island = islandView ?: return
        val textView = islandText ?: return

        isPopupState = true
        textView.text = "♫  Now Playing\nNothing Track - Remix"

        val startWidth = dpToPx(context, configuredWidth)
        val endWidth = dpToPx(context, 280) // बड़ी पॉप-अप खिड़की की चौड़ाई

        val startHeight = dpToPx(context, 40)
        val endHeight = dpToPx(context, 100) // बड़ी पॉप-अप खिड़की की ऊंचाई

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator(1.2f) // स्प्रिंग एनीमेशन
        }

        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float
            val currentWidth = (startWidth + (endWidth - startWidth) * fraction).toInt()
            val currentHeight = (startHeight + (endHeight - startHeight) * fraction).toInt()

            island.layoutParams = (island.layoutParams as FrameLayout.LayoutParams).apply {
                width = currentWidth
                height = currentHeight
            }
            island.requestLayout()
            textView.alpha = fraction
        }

        animator.start()
    }

    // वापस नॉर्मल कैमरे के पीछे छोटा (Collapse) होने का लॉजिक
    private fun collapseIsland(context: Context) {
        val island = islandView ?: return
        val textView = islandText ?: return

        val startWidth = island.width
        val endWidth = dpToPx(context, configuredWidth)

        val startHeight = island.height
        val endHeight = dpToPx(context, 40)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = OvershootInterpolator(0.8f)
        }

        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float
            val currentWidth = (startWidth - (startWidth - endWidth) * fraction).toInt()
            val currentHeight = (startHeight - (startHeight - endHeight) * fraction).toInt()

            island.layoutParams = (island.layoutParams as FrameLayout.LayoutParams).apply {
                width = currentWidth
                height = currentHeight
            }
            island.requestLayout()
            textView.alpha = 1f - fraction
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isExpanded = false
                isPopupState = false
            }
        })

        animator.start()
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
