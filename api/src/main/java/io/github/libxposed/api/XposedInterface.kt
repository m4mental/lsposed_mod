package io.github.libxposed.api

import java.lang.reflect.Member

interface XposedInterface {
    interface Hooker<T>
    
    interface BeforeCallback {
        val thisObject: Any?
        val args: Array<Any?>
        fun setResult(res: Any?)
        fun setThrowable(ex: Throwable?)
    }
    
    interface AfterCallback {
        val thisObject: Any?
        val args: Array<Any?>
        val result: Any?
        val throwable: Throwable?
        fun setResult(res: Any?)
        fun setThrowable(ex: Throwable?)
    }
    
    fun hookMethod(method: java.lang.reflect.Method, hookerClass: Class<out Hooker<*>>): Any
}
