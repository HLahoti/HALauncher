package com.example.halauncher.search.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.halauncher.config.IndexUpdaterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How hard background indexing may work right now. */
enum class Throttle {
    /** Normal debouncing. */
    NORMAL,

    /** Longer debounce windows and short pauses between steps. */
    SLOW,

    /** No indexing; changes keep buffering until conditions improve. */
    PAUSED,
}

/**
 * Device conditions that limit background work.
 *
 * @param thermalStatus one of PowerManager.THERMAL_STATUS_* (0, "none", before Android 10).
 */
data class ResourceState(val powerSaveMode: Boolean, val thermalStatus: Int) {
    val throttle: Throttle
        get() = when {
            thermalStatus >= IndexUpdaterConfig.THERMAL_PAUSE_FROM -> Throttle.PAUSED
            powerSaveMode -> if (IndexUpdaterConfig.PAUSE_ON_BATTERY_SAVER) Throttle.PAUSED else Throttle.SLOW
            thermalStatus >= IndexUpdaterConfig.THERMAL_SLOW_FROM -> Throttle.SLOW
            else -> Throttle.NORMAL
        }

    /**
     * Whether a batch may run now. A user-initiated ([urgent]) batch, such as right after granting
     * a permission, may run under battery saver if [IndexUpdaterConfig.URGENT_BYPASSES_BATTERY_SAVER];
     * heat always wins.
     */
    fun allows(urgent: Boolean): Boolean = when {
        throttle != Throttle.PAUSED -> true
        thermalStatus >= IndexUpdaterConfig.THERMAL_PAUSE_FROM -> false
        else -> urgent && IndexUpdaterConfig.URGENT_BYPASSES_BATTERY_SAVER
    }
}

/**
 * Watches battery saver and the device's thermal status (Android 10+) and publishes them as
 * [state]. The updater slows down or pauses from it.
 */
class ResourceGuard(context: Context) {
    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)

    /** Thermal callbacks exist from Android 10; kept apart so older versions never load them. */
    private val thermal: ThermalWatch? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            ThermalWatch(power) { _state.value = read() }
        } else {
            null
        }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ResourceState> = _state.asStateFlow()

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _state.value = read()
        }
    }

    /** Starts listening; callbacks run on [handler]'s thread. */
    fun start(handler: Handler) {
        ContextCompat.registerReceiver(
            appContext,
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            null,
            handler,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermal?.start(handler)
        _state.value = read()
    }

    fun stop() {
        appContext.unregisterReceiver(powerSaveReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thermal?.stop()
    }

    private fun read(): ResourceState = ResourceState(
        powerSaveMode = power?.isPowerSaveMode == true,
        thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermal?.currentStatus() ?: THERMAL_NONE
        } else {
            THERMAL_NONE
        },
    )

    private companion object {
        /** PowerManager.THERMAL_STATUS_NONE, which only exists from Android 10. */
        const val THERMAL_NONE = 0
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private class ThermalWatch(private val power: PowerManager, onChange: () -> Unit) {
    private val listener = PowerManager.OnThermalStatusChangedListener { onChange() }

    fun currentStatus(): Int = power.currentThermalStatus

    fun start(handler: Handler) = power.addThermalStatusListener({ handler.post(it) }, listener)

    fun stop() = power.removeThermalStatusListener(listener)
}
