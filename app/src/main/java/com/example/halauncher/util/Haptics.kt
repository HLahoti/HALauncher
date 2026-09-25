package com.example.halauncher.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import com.example.halauncher.config.HapticsConfig
import com.example.halauncher.data.LauncherSettingsStore
import kotlin.math.roundToInt

/**
 * Short vibrations driven through [Vibrator] directly.
 *
 * View.performHapticFeedback (and Compose's LocalHapticFeedback) is silenced when the
 * system "touch feedback" switch is off, which would make the index bar feel dead on
 * such devices. Going through the vibrator keeps the tick working either way.
 *
 * @param intensity the strength every buzz is scaled to, 0f (off) to 1f (strongest). Defaults to
 *   the user's saved setting, read on each buzz so a change takes effect at once; the settings
 *   panel passes the live slider value instead, to preview it as it's dragged.
 */
class Haptics(
    context: Context,
    private val intensity: () -> Float = LauncherSettingsStore(context)::hapticIntensity,
) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }

    /** A firmer click, confirming a long-press. Silent when the intensity is turned down to off. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun longPress() {
        val vibrator = vibrator ?: return
        if (intensity() <= 0f) return
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

    /** A crisp tick, e.g. for each app reaching the front of the sphere, at the chosen [intensity]. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun tick() {
        val vibrator = vibrator ?: return
        val level = intensity().coerceIn(0f, 1f)
        if (level <= 0f) return
        when {
            // Android 11+: the vibrator's own tuned primitive, scaled to the chosen intensity, so
            // the tick's strength is adjustable rather than the fixed EFFECT_TICK used before.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(HapticsConfig.TICK_PRIMITIVE) ->
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(HapticsConfig.TICK_PRIMITIVE, level)
                        .compose(),
                )
            // Android 10 (or 11+ without that primitive): the built-in tick, which isn't scalable,
            // so intensity only turns it on or off here.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            // Android 8–9: a one-shot whose amplitude carries the intensity.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(VibrationEffect.createOneShot(HapticsConfig.FALLBACK_TICK_MS, amplitudeFor(level)))
            else ->
                @Suppress("DEPRECATION")
                vibrator.vibrate(HapticsConfig.FALLBACK_TICK_MS)
        }
    }

    /** Maps an intensity of 0f–1f onto a vibrator amplitude of 1–255 (0 never reaches here). */
    private fun amplitudeFor(level: Float): Int = (level * 255).roundToInt().coerceIn(1, 255)
}
