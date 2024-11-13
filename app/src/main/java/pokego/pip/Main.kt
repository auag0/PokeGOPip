package pokego.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.util.Rational
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.ref.WeakReference

class Main : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.nianticlabs.pokemongo") {
            XposedBridge.hookAllMethods(
                findClass(
                    "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity",
                    lpparam.classLoader
                ),
                "onPause",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val weakActivity = WeakReference(activity)

                        if (activity.isInPictureInPictureMode) return

                        val mUnityPlayer = getObjectField(param.thisObject, "mUnityPlayer")
                        var width = callMethod(mUnityPlayer, "getWidth") as Int
                        var height = callMethod(mUnityPlayer, "getHeight") as Int
                        if (width <= 0 || height <= 0) {
                            width = 9
                            height = 16
                        }

                        val view = callMethod(mUnityPlayer, "getView") as View
                        val sourceRectHint = Rect()
                        view.getGlobalVisibleRect(sourceRectHint)

                        val params = PictureInPictureParams.Builder().apply {
                            setAspectRatio(Rational(width, height))
                            setSourceRectHint(sourceRectHint)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setSeamlessResizeEnabled(false)
                            }
                        }.build()
                        activity.enterPictureInPictureMode(params)

                        Handler(activity.mainLooper).postDelayed({
                            weakActivity.get()?.let {
                                callMethod(mUnityPlayer, "resume")
                            }
                        }, 1000)
                    }
                }
            )
        }

        if (lpparam.packageName == "android") {
            XposedBridge.hookAllMethods(
                findClass("android.content.pm.ActivityInfo", lpparam.classLoader),
                "supportsPictureInPicture",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val packageName = getObjectField(param.thisObject, "packageName") as String
                        if (packageName == "com.nianticlabs.pokemongo") {
                            param.result = true
                        }
                    }
                }
            )
        }
    }
}