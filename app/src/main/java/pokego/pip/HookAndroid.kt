package pokego.pip

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookAndroid : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookAllMethods(
            findClass("android.content.pm.ActivityInfo", lpparam.classLoader),
            "supportsPictureInPicture",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val packageName = getObjectField(param.thisObject, "packageName") as? String?
                    if (packageName == "com.nianticlabs.pokemongo") {
                        Log.d(TAG, "set supportsPictureInPicture to true")
                        param.result = true
                    }
                }
            }
        )
    }
}