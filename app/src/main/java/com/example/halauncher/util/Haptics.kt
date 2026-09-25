package com.example.halauncher.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import com.example.halauncher.config.HapticsConfig

/**
 * Short vibrations driven through [Vibrator] directly.
 *
 * View.performHapticFeedback (and Compose's LocalHapticFeedback) is silenced when the
 * system "touch feedback" switch is off, which would make the index bar feel dead on
 * such devices. Going through the vibrator keeps the tick working either way.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }

    /** A firmer click, confirming a long-press. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun longPress() {
        val vibrator = vibrator ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        HapticsConfig.FALLBACK_LONG_PRESS_MS,
                        HapticsConfig.FALLBACK_LONG_PRESS_AMPLITUDE,
                    ),
                )
            else ->
                @Suppress("DEPRECATION")
                vibrator.vibrate(HapticsConfig.FALLBACK_LONG_PRESS_MS)
        }
    }

    /** A crisp tick, e.g. for each app reaching the front of the sphere. Strength: [HapticsConfig]. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun tick() {
        val vibrator = vibrator ?: return
        when {
            // Android 11+: the vibrator's own tuned primitive, scaled by config, so the tick's
            // strength is adjustable rather than the fixed EFFECT_TICK used before.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(HapticsConfig.TICK_PRIMITIVE) ->
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(HapticsConfig.TICK_PRIMITIVE, HapticsConfig.TICK_SCALE)
                        .compose(),
                )
            // Android 10 (or 11+ without that primitive): the built-in, non-scalable tick.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            // Android 8–9: a one-shot whose amplitude sets the strength.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        HapticsConfig.FALLBACK_TICK_MS,
                        HapticsConfig.FALLBACK_TICK_AMPLITUDE,
                    ),
                )
            else ->
                @Suppress("DEPRECATION")
                vibrator.vibrate(HapticsConfig.FALLBACK_TICK_MS)
        }
    }
}
