package com.example.dynamicisland

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {

    private var islandView: FrameLayout? = null
    private var islandText: TextView? = null
    private var isExpanded = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // केवल System UI को टारगेट करें
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedBridge.log("Dynamic Island: Loading for Nothing Phone (2a) Punch-Hole...")

            // PhoneStatusBarView को हुक करना
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
                                createDynamicIsland(context, statusBarView)
                                registerSystemEvents(context)
                            } catch (e: Exception) {
                                XposedBridge.log("Dynamic Island: View Creation failed - " + e.message)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Dynamic Island: Hook setup failed - " + e.message)
        }
    }

    // Dynamic Island यूआई को स्क्रीन पर बनाना
    private fun createDynamicIsland(context: Context, parent: ViewGroup) {
        if (islandView != null) return

        // 1. मुख्य ब्लैक पिल (Island Container)
        islandView = FrameLayout(context).apply {
            // बैकग्राउंड का शेप: पूरी तरह से गोल (Rounded Pill)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dpToPx(context, 20).toFloat() // गोल कोना
            }
            elevation = dpToPx(context, 4).toFloat()
        }

        // 2. इसके अंदर टेक्स्ट व्यू (Text View)
        islandText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            alpha = 0f // शुरुआत में छिपा हुआ
            setPadding(dpToPx(context, 12), 0, dpToPx(context, 12), 0)
        }

        // टेक्स्ट व्यू को मुख्य पिल के अंदर जोड़ें
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        islandView?.addView(islandText, textParams)

        // 3. अलाइनमेंट सेटिंग्स (Nothing Phone 2a के कैमरे के ठीक ऊपर फिट करने के लिए)
        // शुरुआत का आकार: कैमरे के समान गोल (40dp x 40dp)
        val initialSize = dpToPx(context, 40)
        val parentParams = FrameLayout.LayoutParams(initialSize, initialSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            
            // कैमरे की सही ऊंचाई सेट करने के लिए यहाँ 'topMargin' बदल सकते हैं
            topMargin = dpToPx(context, 8) 
        }

        // स्टेटस बार में इस लेआउट को जोड़े
        parent.addView(islandView, parentParams)
        XposedBridge.log("Dynamic Island: Dynamic Island View added successfully over punch hole.")
    }

    // सिस्टम इवेंट्स (जैसे चार्जिंग प्लग-इन) सुनना
    private fun registerSystemEvents(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        // बैटरी प्रतिशत जानें
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        triggerIslandAnimation(ctx, "Charging • $level%")
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        triggerIslandAnimation(ctx, "Charger Disconnected")
                    }
                }
            }
        }, filter)
    }

    // इलास्टिक स्प्रिंग एनीमेशन का लॉजिक
    private fun triggerIslandAnimation(context: Context, text: String) {
        val island = islandView ?: return
        val textView = islandText ?: return
        if (isExpanded) return // यदि पहले से खुला है, तो दोबारा रन न करें

        isExpanded = true
        textView.text = text

        // आकार बदलना: 40dp गोल से लेकर 200dp चौड़े कैप्सूल तक
        val startWidth = dpToPx(context, 40)
        val endWidth = dpToPx(context, 200)

        val startHeight = dpToPx(context, 40)
        val endHeight = dpToPx(context, 42)

        // एनीमेशन के लिए वैल्यू एनिमेटर
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450 // एनीमेशन स्पीड (ms)
            interpolator = OvershootInterpolator(1.4f) // स्प्रिंग जैसा लचीला इफ़ेक्ट
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

            // जैसे-जैसे पिल बढ़े, टेक्स्ट को धीरे-धीरे दिखाएँ (Fade In)
            textView.alpha = fraction
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 3 सेकंड तक प्रदर्शित करने के बाद वापस ऑटो-कोलैप्स (छोटा) करें
                Handler(Looper.getMainLooper()).postDelayed({
                    collapseIsland(context)
                }, 3000)
            }
        })

        animator.start()
    }

    // वापस कैमरे के पीछे छुपने (Collapse) का लॉजिक
    private fun collapseIsland(context: Context) {
        val island = islandView ?: return
        val textView = islandText ?: return

        val startWidth = island.width
        val endWidth = dpToPx(context, 40)

        val startHeight = island.height
        val endHeight = dpToPx(context, 40)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            // सिकुड़ते समय स्प्रिंग बाउंस कम रखें ताकि आसानी से कैमरे में समा जाए
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

            // टेक्स्ट को धीरे-धीरे छिपाएँ (Fade Out)
            textView.alpha = 1f - fraction
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isExpanded = false
            }
        })

        animator.start()
    }

    // Helper: dp को पिक्सल में बदलने के लिए
    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
