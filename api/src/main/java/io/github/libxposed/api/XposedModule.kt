package io.github.libxposed.api

abstract class XposedModule(
    private val base: XposedInterface,
    val param: XposedModuleInterface.ModuleLoadedParam
) : XposedInterface by base, XposedModuleInterface {
    
    override fun onPackageLoaded(param: XposedModuleInterface.OnPackageLoadedParam) {}
}
