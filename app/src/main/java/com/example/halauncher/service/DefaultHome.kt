package com.example.halauncher.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Whether HALauncher is the device's default home app, and how to ask to become it.
 *
 * A launcher only opens on the Home button once it's the default home. There's no runtime-permission
 * dialog for this: on Android 10+ it's the system "home" role (a confirmation dialog); before that,
 * and as a fallback, it's the "Default home app" settings screen the user picks from. Either way the
 * choice is the user's, and the launcher re-checks [isDefault] when it comes back to the foreground.
 */
object DefaultHome {

    /** Whether HALauncher currently handles the Home intent as the default. */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.applicationContext.packageName
    }

    /**
     * An intent that asks the user to make HALauncher the default home app: the system's home-role
     * dialog on Android 10+ (when the role is available and not already held), otherwise the
     * "Default home app" settings screen. The caller launches it (ideally for a result) and re-checks
     * [isDefault] on resume.
     */
    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
