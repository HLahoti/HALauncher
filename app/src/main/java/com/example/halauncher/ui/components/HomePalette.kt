package com.example.halauncher.ui.components

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.halauncher.config.ThemeConfig

/**
 * Colours of the bare home screen (clock, date, quick actions, sphere), which sit straight on the
 * wallpaper: white with a dark shadow over a dark wallpaper, near-black with a white glow over a
 * light one. Overlays don't use it: they sit on a dark scrim, so they stay white whatever the
 * wallpaper.
 *
 * @property isLight whether the wallpaper is light, so content is dark (and so are the status bar icons).
 * @property textShadow shadow under text, in pixels for the current density.
 * @property glyphShadow colour of the blurred copy drawn behind glyphs ([LegibleIcon]).
 * @property glassBase base colour of the sphere's glass (see [liquidGlass]).
 * @property glassLine colour of the glass's edge and the sphere's latitude lines.
 */
@Immutable
data class HomePalette(
    val isLight: Boolean,
    val foreground: Color,
    val textShadow: Shadow,
    val glyphShadow: Color,
    val glassBase: Color,
    val glassLine: Color,
)

/** The home screen's palette, provided by HomeScreen around the content drawn on the wallpaper. */
val LocalHomePalette = staticCompositionLocalOf<HomePalette> { error("No HomePalette provided") }

/** The palette for a light or dark wallpaper, with its shadow converted to this screen's pixels. */
@Composable
fun rememberHomePalette(isLight: Boolean): HomePalette {
    val density = LocalDensity.current
    return remember(isLight, density) {
        val shadowColor = if (isLight) {
            Color.White.copy(alpha = ThemeConfig.LEGIBILITY_GLOW_ALPHA)
        } else {
            Color.Black.copy(alpha = ThemeConfig.LEGIBILITY_SHADOW_ALPHA)
        }
        val shadow = with(density) {
            Shadow(
                color = shadowColor,
                offset = Offset(0f, ThemeConfig.LegibilityShadowOffset.toPx()),
                blurRadius = ThemeConfig.LegibilityShadowBlur.toPx(),
            )
        }
        HomePalette(
            isLight = isLight,
            foreground = if (isLight) ThemeConfig.ForegroundOnLight else ThemeConfig.Foreground,
            textShadow = shadow,
            glyphShadow = shadowColor,
            glassBase = if (isLight) Color.White else Color.Black,
            glassLine = if (isLight) Color.Black else Color.White,
        )
    }
}

/**
 * Whether the home-screen wallpaper is light enough for dark content, following it as it changes.
 * Android 12+ says so outright; Android 8.1–11 is judged by the wallpaper's main colour; older
 * versions can't tell, so they keep the white content.
 */
@Composable
fun rememberWallpaperIsLight(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false
    val context = LocalContext.current
    return rememberWallpaperIsLightApi27(context)
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
private fun rememberWallpaperIsLightApi27(context: Context): Boolean {
    val wallpaperManager = remember(context) { WallpaperManager.getInstance(context) }
    // Read up front rather than in the effect, so a light wallpaper doesn't flash white content first.
    var isLight by remember(wallpaperManager) {
        mutableStateOf(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM).suitsDarkText())
    }
    DisposableEffect(wallpaperManager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) isLight = colors.suitsDarkText()
        }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wallpaperManager.removeOnColorsChangedListener(listener) }
    }
    return isLight
}

/** Whether dark text reads well on these wallpaper colours; false when unknown (e.g. some live wallpapers). */
@RequiresApi(Build.VERSION_CODES.O_MR1)
private fun WallpaperColors?.suitsDarkText(): Boolean = when {
    this == null -> false
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    else -> primaryColor.luminance() > ThemeConfig.LIGHT_WALLPAPER_LUMINANCE
}

/**
 * A home-screen glyph in the palette's colour, over a soft blurred copy of itself in the shadow
 * colour, so it holds up on in-between wallpapers the way the text's shadow does. [modifier] must
 * size it. The blur needs Android 12+; before that the glyph goes without its shadow.
 */
@Composable
fun LegibleIcon(imageVector: ImageVector, modifier: Modifier) {
    val palette = LocalHomePalette.current
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = palette.glyphShadow,
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = ThemeConfig.LegibilityShadowOffset)
                    .blur(ThemeConfig.LegibilityShadowBlur, BlurredEdgeTreatment.Unbounded),
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = palette.foreground,
            modifier = Modifier.matchParentSize(),
        )
    }
}
