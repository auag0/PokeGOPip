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
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference

class HookPokeGO : IXposedHookLoadPackage {
    companion object {
        private const val ACTION_TOGGLE_BLACK_SCREEN = "ACTION_PIP_BLACK_SCREEN"
        private const val ACTION_TOGGLE_STARTUP_MODE = "ACTION_START_UP_BLACK_SCREEN"
    }

    private var broadcastReceiver: BroadcastReceiver? = null
    private lateinit var unityMainActivityClass: Class<*>
    private var toast: Toast? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.i(TAG, "started pokemon go")

        unityMainActivityClass = findClass(
            "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity",
            lpparam.classLoader
        )

        XposedBridge.hookAllMethods(
            unityMainActivityClass,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    handleOnCreate(activity)
                }
            }
        )

        XposedBridge.hookAllMethods(
            Activity::class.java,
            "onUserLeaveHint",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (activity::class.java != unityMainActivityClass) {
                        return
                    }
                    handleOnUserLeaveHint(activity)
                }
            }
        )

        XposedBridge.hookAllMethods(
            unityMainActivityClass,
            "onDestroy",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    handleOnDestroy(activity)
                }
            }
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun handleOnCreate(activity: Activity) {
        Log.i(TAG, "called onCreate")

        val mUnityPlayer = getObjectField(activity, "mUnityPlayer") as View

        val prefs = activity.getSharedPreferences("pip", Context.MODE_PRIVATE)
        if (prefs.getBoolean("start_up", false)) {
            mUnityPlayer.visibility = View.GONE
            Log.i(TAG, "Startup black screen enabled: hide mUnityPlayer")
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "received broadcast: ${intent.action}")

                when (intent.action) {
                    ACTION_TOGGLE_BLACK_SCREEN -> {
                        if (mUnityPlayer.visibility == View.VISIBLE) {
                            mUnityPlayer.visibility = View.GONE
                            Log.i(TAG, "hide mUnityPlayer")
                        } else {
                            mUnityPlayer.visibility = View.VISIBLE
                            Log.i(TAG, "show mUnityPlayer")
                        }
                    }

                    ACTION_TOGGLE_STARTUP_MODE -> {
                        val newStartUp = !prefs.getBoolean("start_up", false)
                        prefs.edit().putBoolean("start_up", newStartUp).apply()
                        Log.i(TAG, "Startup black screen pref updated: $newStartUp")
                        toast(
                            activity,
                            "Startup black screen set to $newStartUp"
                        )
                    }
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        activity.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(ACTION_TOGGLE_BLACK_SCREEN)
            addAction(ACTION_TOGGLE_STARTUP_MODE)
        }, flags)

        Log.i(TAG, "registered receiver")
    }

    private fun handleOnUserLeaveHint(activity: Activity) {
        Log.i(TAG, "called onUserLeaveHint")

        val activityRef = WeakReference(activity)

        val mUnityPlayer = getObjectField(activity, "mUnityPlayer")
        var width = callMethod(mUnityPlayer, "getWidth") as Int
        var height = callMethod(mUnityPlayer, "getHeight") as Int
        if (width <= 0 || height <= 0) {
            width = 9
            height = 16
        }
        Log.i(TAG, "width: $width, height: $height")

        val view = callMethod(mUnityPlayer, "getView") as View
        val viewBounds = Rect()
        view.getGlobalVisibleRect(viewBounds)

        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(
                    activity,
                    android.R.drawable.ic_lock_power_off
                ),
                "Toggle Black Screen",
                "Toggle black screen visibility",
                PendingIntent.getBroadcast(
                    activity,
                    0,
                    Intent(ACTION_TOGGLE_BLACK_SCREEN).apply {
                        this.`package` = activity.packageName
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ),
            RemoteAction(
                Icon.createWithResource(
                    activity,
                    android.R.drawable.ic_popup_sync
                ),
                "Toggle Startup Mode",
                "Toggle startup black screen mode",
                PendingIntent.getBroadcast(
                    activity,
                    1,
                    Intent(ACTION_TOGGLE_STARTUP_MODE).apply {
                        this.`package` = activity.packageName
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        )

        val params = PictureInPictureParams.Builder().apply {
            setAspectRatio(Rational(width, height))
            setSourceRectHint(viewBounds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setSeamlessResizeEnabled(false)
            }
            setActions(actions)
        }.build()

        activity.enterPictureInPictureMode(params)
        Log.i(TAG, "entered picture in picture mode")

        Handler(activity.mainLooper).postDelayed({
            activityRef.get()?.let {
                callMethod(mUnityPlayer, "resume")
                Log.i(TAG, "resumed after entering picture in picture mode")
            } ?: Log.e(TAG, "Activity reference lost: resume skipped")
        }, 1000)
    }

    private fun handleOnDestroy(activity: Activity) {
        Log.i(TAG, "called onDestroy")

        try {
            activity.unregisterReceiver(broadcastReceiver)
            Log.i(TAG, "unregistered receiver")
        } catch (e: Throwable) {
            Log.e(TAG, "failed to unregister receiver", e)
        }
    }

    private fun toast(context: Context, msg: Any?, length: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(context, msg.toString(), length)
        toast?.show()
    }
}