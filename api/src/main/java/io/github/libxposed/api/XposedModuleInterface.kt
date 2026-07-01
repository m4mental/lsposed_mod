package io.github.libxposed.api

interface XposedModuleInterface {
    interface ModuleLoadedParam
    interface OnPackageLoadedParam {
        val packageName: String
        val classLoader: ClassLoader
    }
    fun onPackageLoaded(param: OnPackageLoadedParam)
}
