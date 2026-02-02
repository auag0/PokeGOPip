package io.github.auag0.pokegopip

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private val hookPokeGO = HookPokeGO()

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        hookPokeGO.initZygote(startupParam)
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> HookAndroid().handleLoadPackage(lpparam)
            "com.nianticlabs.pokemongo" -> hookPokeGO.handleLoadPackage(lpparam)
        }
    }
}