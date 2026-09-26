package com.example.halauncher.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.halauncher.R

/**
 * Device-admin receiver whose only purpose is to let HALauncher lock the screen.
 *
 * It declares just the force-lock policy (see res/xml/device_admin.xml) and overrides nothing: an
 * active device admin can call [DevicePolicyManager.lockNow], which is all the launcher needs.
 *
 * This deliberately replaces the earlier accessibility-service approach. Unlike an accessibility
 * service, a device admin cannot read the screen or inject input, so banking and other
 * security-sensitive apps don't flag its presence. The user activates it through the system's
 * device-admin dialog; see `rememberDoubleTapLock`, which sends them there when it isn't on yet.
 */
class LockAdminReceiver : DeviceAdminReceiver()

/** Locks the screen through the device-admin API. See [LockAdminReceiver]. */
object DeviceLock {

    private fun componentName(context: Context) =
        ComponentName(context.applicationContext, LockAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** Whether the user has activated HALauncher as a device admin. */
    fun isEnabled(context: Context): Boolean =
        dpm(context).isAdminActive(componentName(context))

    /**
     * Locks the screen if HALauncher is an active device admin.
     *
     * @return whether the lock was performed. false means the admin isn't active (the caller should
     *   prompt to enable it), or the platform refused the call.
     */
    fun lockScreen(context: Context): Boolean {
        val dpm = dpm(context)
        val admin = componentName(context)
        if (!dpm.isAdminActive(admin)) return false
        return try {
            dpm.lockNow()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Removes HALauncher's device-admin activation. Called when the user turns the lock setting off,
     * so it also clears the block on uninstalling the app while a device admin is active.
     */
    fun disable(context: Context) {
        val dpm = dpm(context)
        val admin = componentName(context)
        if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
    }

    /** An intent that opens the system dialog asking the user to activate HALauncher as a device admin. */
    fun addAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.lock_admin_explanation),
            )
        }
}
