package com.example.dynamicisland

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

    private var configuredTopMargin = 8
    private var configuredWidth = 40

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // हम केवल SystemUI को हुक कर रहे हैं, सेल्फ-हुकिंग को पूरी तरह हटा दिया गया है
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
            elevation = dpToPx(context, 4).toFloat()
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

        parent.addView(islandView, parentParams)
    }

    private fun registerLiveSettingsReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction("com.example.dynamicisland.UPDATE_SETTINGS")
            addAction("com.example.dynamicisland.QUERY_STATUS") // नया स्टेटस क्वेरी एक्शन
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

                        islandView?.let { view ->
                            val params = view.layoutParams as FrameLayout.LayoutParams
                            params.topMargin = dpToPx(ctx, newTopMargin)
                            params.width = dpToPx(ctx, newWidth)
                            view.layoutParams = params
                            view.requestLayout()
                        }
                    }
                    "com.example.dynamicisland.QUERY_STATUS" -> {
                        // जब कैलिब्रेशन ऐप पूछेगा, तो SystemUI उसे रिप्लाई भेजेगा
                        val replyIntent = Intent("com.example.dynamicisland.REPLY_STATUS")
                        ctx.sendBroadcast(replyIntent)
                        XposedBridge.log("Dynamic Island: Active Status Replied successfully.")
                    }
                }
            }
        }, filter, flagExported)
    }

    private fun registerSystemEvents(context: Context) {
        // ... (charging animations if needed)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
