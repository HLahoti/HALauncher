package com.example.halauncher.model

import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * An installed app that has a launcher entry point.
 *
 * @property link the app's launch intent, resolved when the app list is loaded so the UI
 * never has to query PackageManager while composing.
 */
data class AppInfo(
    override val name: String,
    val packageName: String,
    override val icon: Drawable,
    override val link: Intent,
) : SearchEntry {
    override val id: String get() = "app:$packageName"
    override val category: SearchCategory get() = SearchCategory.APP
}
