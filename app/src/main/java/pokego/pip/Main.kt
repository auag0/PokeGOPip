package pokego.pip

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.util.Log
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
    companion object {
        private const val TAG = "PokeGoPip"
        private const val ACTION_PIP_BLACK_SCREEN = "ACTION_PIP_BLACK_SCREEN"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.nianticlabs.pokemongo") {
            val activityClass = findClass(
                "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity",
                lpparam.classLoader
            )

            var broadcastReceiver: BroadcastReceiver? = null

            XposedBridge.hookAllMethods(
                activityClass,
                "onCreate",
                object : XC_MethodHook() {
                    @SuppressLint("UnspecifiedRegisterReceiverFlag")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        Log.i(TAG, "called onCreate")
                        val activity = param.thisObject as Activity
                        val mUnityPlayer = getObjectField(param.thisObject, "mUnityPlayer") as View

                        broadcastReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == ACTION_PIP_BLACK_SCREEN) {
                                    if (mUnityPlayer.visibility == View.VISIBLE) {
                                        Log.i(TAG, "hide mUnityPlayer")
                                        mUnityPlayer.visibility = View.GONE
                                    } else {
                                        Log.i(TAG, "show mUnityPlayer")
                                        mUnityPlayer.visibility = View.VISIBLE
                                    }
                                }
                            }
                        }

                        Log.i(TAG, "register receiver")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activity.registerReceiver(
                                broadcastReceiver,
                                IntentFilter(ACTION_PIP_BLACK_SCREEN),
                                Context.RECEIVER_NOT_EXPORTED
                            )
                        } else {
                            activity.registerReceiver(
                                broadcastReceiver,
                                IntentFilter(ACTION_PIP_BLACK_SCREEN)
                            )
                        }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                activityClass,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        Log.i(TAG, "called onDestroy")
                        val activity = param.thisObject as Activity
                        try {
                            Log.i(TAG, "unregister receiver")
                            activity.unregisterReceiver(broadcastReceiver)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "failed to unregister receiver", e)
                        }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                activityClass,
                "onPause",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        Log.i(TAG, "called onPause")
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

                        val actions = listOf(
                            RemoteAction(
                                Icon.createWithResource(
                                    activity,
                                    android.R.drawable.ic_lock_power_off
                                ),
                                "toggle black screen",
                                "toggle black screen",
                                PendingIntent.getBroadcast(
                                    activity,
                                    0,
                                    Intent(ACTION_PIP_BLACK_SCREEN).apply {
                                        this.`package` = activity.packageName
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        )

                        val params = PictureInPictureParams.Builder().apply {
                            setAspectRatio(Rational(width, height))
                            setSourceRectHint(sourceRectHint)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setSeamlessResizeEnabled(false)
                            }
                            setActions(actions)
                        }.build()
                        Log.i(TAG, "enterPictureInPictureMode")
                        activity.enterPictureInPictureMode(params)

                        Handler(activity.mainLooper).postDelayed({
                            weakActivity.get()?.let {
                                Log.i(TAG, "call resume")
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
                            Log.e(TAG, "set supportsPictureInPicture to true")
                            param.result = true
                        }
                    }
                }
            )
        }
    }
}