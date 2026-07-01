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

    // लाइव चेंज होने वाले वेरिएबल्स
    private var configuredTopMargin = 8
    private var configuredWidth = 40

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedBridge.log("Dynamic Island: Adjustable module loading...")

            XposedHelpers.findAndHookMethod(
                "com.android.systemui.SystemUIApplication",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("Dynamic Island: SystemUIApplication loaded successfully.")
                    }
                }
            )

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
                                XposedBridge.log("Dynamic Island: Adjustable creation failed - " + e.message)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Dynamic Island: Setup error - " + e.message)
        }
    }

    // शुरुआत में शेयर्ड प्रेफरेंसेस से डेटा लोड करना
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

        // कॉन्फ़िगर की गई सेटिंग्स के अनुसार आकार
        val currentSizeWidth = dpToPx(context, configuredWidth)
        val currentSizeHeight = dpToPx(context, 40) // कैमरा गोलाई ऊँचाई
        
        val parentParams = FrameLayout.LayoutParams(currentSizeWidth, currentSizeHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = dpToPx(context, configuredTopMargin)
        }

        parent.addView(islandView, parentParams)
    }

    // ऐप से लाइव बदलाव सुनने के लिए रिसीवर (बिना रीबूट एडजस्टमेंट)
    private fun registerLiveSettingsReceiver(context: Context) {
        val filter = IntentFilter("com.example.dynamicisland.UPDATE_SETTINGS")
        val flagExported = 2 // Context.RECEIVER_EXPORTED (Android 13+ / 16 के लिए ज़रूरी)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val newTopMargin = intent.getIntExtra("topMargin", 8)
                val newWidth = intent.getIntExtra("width", 40)

                configuredTopMargin = newTopMargin
                configuredWidth = newWidth

                // बिना रीबूट तुरंत यूआई अपडेट करें
                islandView?.let { view ->
                    val params = view.layoutParams as FrameLayout.LayoutParams
                    params.topMargin = dpToPx(ctx, newTopMargin)
                    params.width = dpToPx(ctx, newWidth)
                    params.height = dpToPx(ctx, 40) // कैमरे की ऊंचाई
                    view.layoutParams = params
                    view.requestLayout()
                    XposedBridge.log("Dynamic Island Live Adjust: Margin=$newTopMargin, Width=$newWidth")
                }
            }
        }, filter, flagExported)
    }

    private fun registerSystemEvents(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // एनीमेशन ट्रिगर... (समान चार्जिंग डिटेक्ट लॉजिक)
            }
        }, filter)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
