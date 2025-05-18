package pokego.pip

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class Main : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        val handler = when (lpparam.packageName) {
            "android" -> HookAndroid()
            "com.nianticlabs.pokemongo" -> HookPokeGO()
            else -> return
        }
        handler.handleLoadPackage(lpparam)
    }
}