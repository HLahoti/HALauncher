package com.example.halauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * An accessibility service whose only job is to lock the screen on request.
 *
 * Locking the screen is a privileged action no ordinary app can perform; an accessibility
 * service can, through [performGlobalAction] with [GLOBAL_ACTION_LOCK_SCREEN] (Android 9+).
 *
 * The service watches nothing — it declares no event types and inspects no windows — so it
 * reads and stores none of the user's screen content. It just stays connected so [lockScreen]
 * has a live service to call. The user turns it on in Accessibility settings; see
 * `rememberDoubleTapLock`, which sends them there when it isn't on yet.
 */
class LockScreenService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    // Nothing is watched, so there is nothing to handle.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        /** The connected service, or null while the user hasn't switched it on (or has turned it off). */
        private var instance: LockScreenService? = null

        /** Locking the screen this way needs Android 9 (API 28) or newer. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /**
         * Whether the user has switched this service on in Accessibility settings. Read from the
         * system's list rather than [instance] so it's right even in the moment after it's enabled,
         * before the service has finished connecting.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, LockScreenService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        /**
         * Locks the screen, if the service is connected and the platform supports it.
         *
         * @return whether the lock was performed. false means the service isn't connected yet, or
         *   the OS is too old (see [isSupported]); the caller decides what to do about that.
         */
        fun lockScreen(): Boolean {
            if (!isSupported) return false
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
