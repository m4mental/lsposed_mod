package com.example.dynamicisland

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // केवल System UI को टारगेट करें
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedBridge.log("Dynamic Island: MainHook loaded into Nothing OS System UI")

            // PhoneStatusBarView को हुक करना
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                lpparam.classLoader,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val statusBarView = param.thisObject as View
                        
                        // मुख्य थ्रेड पर व्यू हायरार्की प्रिंट करें ताकि आप Nothing OS के लेआउट को समझ सकें
                        Handler(Looper.getMainLooper()).post {
                            try {
                                XposedBridge.log("Dynamic Island: StatusBar inflated. Printing layout structure...")
                                logViewHierarchy(statusBarView)
                            } catch (e: Exception) {
                                XposedBridge.log("Dynamic Island: Error in handler - " + e.message)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Dynamic Island: Hook initialization failed - " + e.message)
        }
    }

    // Nothing OS के व्यू आर्किटेक्चर को समझने के लिए हेल्पर फ़ंक्शन
    private fun logViewHierarchy(view: View, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        XposedBridge.log("$indent[NothingOS View] Class: ${view.javaClass.name}, ID: ${view.id}")
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                logViewHierarchy(view.getChildAt(i), depth + 1)
            }
        }
    }
}
