package com.example.halauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * The work profiles on this device: profiles a work or school (device policy) app manages, set up
 * alongside the personal one. Their apps are listed as separate "(work)" apps.
 *
 * Other kinds of profile are left out: Android 15's Private Space (whose apps must stay hidden
 * unless it's unlocked) and clone profiles. Telling them apart needs Android 15; before that the
 * only profiles a phone has in practice are work profiles, so every other profile counts as one.
 */
object WorkProfiles {
    fun all(context: Context): List<UserHandle> {
        val userManager = context.getSystemService(UserManager::class.java) ?: return emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val me = Process.myUserHandle()
        return userManager.userProfiles.filter { it != me && isWork(launcherApps, it) }
    }

    /** The work profile, or null if there's none (the first, should there somehow be several). */
    fun first(context: Context): UserHandle? = all(context).firstOrNull()

    private fun isWork(launcherApps: LauncherApps?, profile: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return true
        return try {
            launcherApps?.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
        } catch (e: SecurityException) {
            false
        }
    }
}
