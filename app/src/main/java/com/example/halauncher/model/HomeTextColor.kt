package com.example.halauncher.model

/**
 * The colour of the bare home screen's content (clock, date, quick actions, app sphere) over the
 * wallpaper. [AUTO] follows the wallpaper: dark on light wallpapers, light otherwise. [LIGHT] and
 * [DARK] override it, for wallpapers the system judges wrongly (busy or half-light ones).
 */
enum class HomeTextColor {
    AUTO,
    LIGHT,
    DARK,
}
