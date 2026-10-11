/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.dualscreen

import android.app.AlarmManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.WindowManager
import android.view.WindowManagerGlobal
import kotlin.math.roundToInt
import vendor.lge.hardware.dualscreen.CaseInfo
import vendor.lge.hardware.dualscreen.ICaseCallback
import vendor.lge.hardware.dualscreen.IDualScreen

class DualScreenService :
    Service(), DisplayManager.DisplayListener, InputManager.InputDeviceListener {

    private val thread = HandlerThread(TAG)
    private lateinit var handler: Handler
    private lateinit var displays: DisplayManager
    private lateinit var inputs: InputManager
    private lateinit var power: PowerManager
    private lateinit var alarms: AlarmManager
    private lateinit var settings: ContentObserver

    private var hal: IDualScreen? = null
    private var info = CaseInfo()
    private var battery = -1
    private var charging = false
    private var displayUniqueId: String? = null
    private var configuredDisplay = Display.INVALID_DISPLAY
    private val associations = HashMap<String, String>()

    private val connectRunnable = Runnable { connect() }
    private val minute = AlarmManager.OnAlarmListener { refresh() }
    private val death = IBinder.DeathRecipient { handler.post { halDied() } }

    private val callback =
        object : ICaseCallback.Stub() {
            override fun onCaseChanged(info: CaseInfo) {
                handler.post { caseChanged(info) }
            }

            override fun getInterfaceVersion(): Int = ICaseCallback.VERSION

            override fun getInterfaceHash(): String = ICaseCallback.HASH
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    battery = if (level >= 0 && scale > 0) minOf(100, level * 100 / scale) else -1
                    charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                }
                refresh()
            }
        }

    override fun onCreate() {
        super.onCreate()
        thread.start()
        handler = Handler(thread.looper)
        displays = getSystemService(DisplayManager::class.java)
        inputs = getSystemService(InputManager::class.java)
        power = getSystemService(PowerManager::class.java)
        alarms = getSystemService(AlarmManager::class.java)
        displays.registerDisplayListener(
            this,
            handler,
            DisplayManager.EVENT_TYPE_DISPLAY_ADDED or
                DisplayManager.EVENT_TYPE_DISPLAY_REMOVED or
                DisplayManager.EVENT_TYPE_DISPLAY_CHANGED or
                DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS,
        )
        inputs.registerInputDeviceListener(this, handler)
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
            }
        registerReceiver(receiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED)
        settings =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    refresh()
                }
            }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            settings,
        )
        handler.post(connectRunnable)
    }

    private fun connect() {
        if (hal != null) return
        val binder = ServiceManager.getService(HAL)
        if (binder == null) {
            handler.postDelayed(connectRunnable, 5000)
            return
        }
        try {
            binder.linkToDeath(death, 0)
            val service = IDualScreen.Stub.asInterface(binder)
            hal = service
            service.setCallback(callback)
            refresh()
        } catch (e: RemoteException) {
            halDied()
        }
    }

    private fun halDied() {
        hal?.asBinder()?.unlinkToDeath(death, 0)
        hal = null
        info = CaseInfo()
        displayUniqueId = null
        associateTouch()
        alarms.cancel(minute)
        handler.removeCallbacks(connectRunnable)
        handler.postDelayed(connectRunnable, 1000)
    }

    private fun caseChanged(newInfo: CaseInfo) {
        val previous = info
        info = newInfo
        if (!newInfo.connected) {
            displayUniqueId = null
            configuredDisplay = Display.INVALID_DISPLAY
        }
        if (
            newInfo.connected &&
                newInfo.coverState == 1 &&
                (!previous.connected || previous.coverState != 1)
        ) {
            power.goToSleep(SystemClock.uptimeMillis())
        } else if (
            newInfo.connected &&
                previous.connected &&
                previous.coverState == 1 &&
                newInfo.coverState == 0
        ) {
            power.wakeUp(
                SystemClock.uptimeMillis(),
                PowerManager.WAKE_REASON_WAKE_MOTION,
                "dualscreen:open",
            )
        }
        associateTouch()
        refresh()
    }

    private fun associateTouch() {
        if (info.connected) {
            var external: Display? = null
            var count = 0
            for (display in
                displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)) {
                if (display.type == Display.TYPE_EXTERNAL) {
                    external = display
                    count++
                }
            }
            if (count == 1 && external != null) {
                displayUniqueId = external.uniqueId
                if (configuredDisplay != external.displayId) {
                    try {
                        displays.enableConnectedDisplay(external.displayId)
                        val wm = WindowManagerGlobal.getWindowManagerService()
                        if (wm != null) {
                            wm.setDisplayImePolicy(
                                external.displayId,
                                WindowManager.DISPLAY_IME_POLICY_LOCAL,
                            )
                            configuredDisplay = external.displayId
                        } else {
                            Log.w(TAG, "No window manager service; will retry")
                        }
                    } catch (e: RemoteException) {
                        Log.w(TAG, "Cannot configure case display", e)
                    }
                }
            }
        }
        val desired = HashSet<String>()
        val uniqueId = displayUniqueId
        if (info.connected && uniqueId != null) {
            for (id in inputs.inputDeviceIds) {
                val device = inputs.getInputDevice(id)
                if (
                    device != null &&
                        device.vendorId == 0x1004 &&
                        device.productId == 0x637a &&
                        device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)
                ) {
                    val descriptor = device.descriptor
                    desired.add(descriptor)
                    if (uniqueId != associations[descriptor]) {
                        inputs.addUniqueIdAssociationByDescriptor(descriptor, uniqueId)
                        associations[descriptor] = uniqueId
                    }
                }
            }
        }
        for (descriptor in HashSet(associations.keys)) {
            if (descriptor !in desired) {
                inputs.removeUniqueIdAssociationByDescriptor(descriptor)
                associations.remove(descriptor)
            }
        }
    }

    private fun refresh() {
        alarms.cancel(minute)
        val service = hal ?: return
        val primary: Display? = displays.getDisplay(Display.DEFAULT_DISPLAY)
        val brightness = primary?.brightnessInfo
        val value =
            if (brightness == null || !brightness.brightness.isFinite()) {
                0.5f
            } else {
                brightness.brightness
            }
        val level = (value.coerceIn(0f, 1f) * 255).roundToInt()
        val interactive = power.isInteractive(Display.DEFAULT_DISPLAY)
        val external: Display? = displays.getDisplay(configuredDisplay)
        if (info.connected && external != null) {
            val awake = interactive && info.coverState == 0
            if (awake != power.isInteractive(external.displayId)) {
                val now = SystemClock.uptimeMillis()
                if (awake) {
                    power.wakeUp(
                        now,
                        PowerManager.WAKE_REASON_APPLICATION,
                        "dualscreen:display",
                        external.displayId,
                    )
                } else {
                    power.goToSleep(
                        external.displayId,
                        now,
                        PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                        0,
                    )
                }
            }
        }
        try {
            service.setDisplayState(interactive, level)
            if (info.connected && info.coverState == 1 && info.width > 0 && info.height > 0) {
                service.drawCover(
                    info.width,
                    info.height,
                    CoverClock.render(this, info.width, info.height, battery, charging),
                )
                val nextMinute = (System.currentTimeMillis() / 60000 + 1) * 60000
                alarms.setExact(AlarmManager.RTC_WAKEUP, nextMinute, TAG, minute, handler)
            }
        } catch (e: RemoteException) {
            halDied()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cover display changed during update", e)
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        associateTouch()
        refresh()
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (configuredDisplay == displayId) configuredDisplay = Display.INVALID_DISPLAY
        associateTouch()
        refresh()
    }

    override fun onDisplayChanged(displayId: Int) {
        associateTouch()
        refresh()
    }

    override fun onInputDeviceAdded(deviceId: Int) = associateTouch()

    override fun onInputDeviceRemoved(deviceId: Int) = associateTouch()

    override fun onInputDeviceChanged(deviceId: Int) = associateTouch()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(receiver)
        contentResolver.unregisterContentObserver(settings)
        displays.unregisterDisplayListener(this)
        inputs.unregisterInputDeviceListener(this)
        handler.post {
            alarms.cancel(minute)
            hal?.let {
                try {
                    it.setCallback(null)
                } catch (ignored: RemoteException) {}
                it.asBinder().unlinkToDeath(death, 0)
            }
            hal = null
            for (descriptor in HashSet(associations.keys)) {
                inputs.removeUniqueIdAssociationByDescriptor(descriptor)
            }
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LgeDualScreen"
        val HAL = IDualScreen.DESCRIPTOR + "/default"
    }
}
