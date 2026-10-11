package com.example.halauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.example.halauncher.config.GlassConfig

/**
 * A translucent "liquid glass" surface for use over the wallpaper.
 *
 * Android can't blur the system wallpaper behind a single composable (window blur applies
 * to the whole window), so this imitates glass with light: a faint dark base for contrast
 * on bright wallpapers, a white sheen that fades top to bottom, and an edge that catches
 * light at the top.
 *
 * Over a light wallpaper the home screen frosts it instead: a white [base] and a dark [edge]
 * (see [HomePalette]).
 *
 * @param tint opacity of the base; raise it for large surfaces that sit over text.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Float = GlassConfig.DEFAULT_TINT,
    base: Color = Color.Black,
    edge: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(base.copy(alpha = tint))
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = GlassConfig.SHEEN_TOP_ALPHA),
            0.5f to Color.White.copy(alpha = GlassConfig.SHEEN_MIDDLE_ALPHA),
            1f to Color.White.copy(alpha = GlassConfig.SHEEN_BOTTOM_ALPHA),
        ),
    )
    .border(
        width = GlassConfig.EdgeWidth,
        brush = Brush.verticalGradient(
            0f to edge.copy(alpha = GlassConfig.EDGE_TOP_ALPHA),
            0.5f to edge.copy(alpha = GlassConfig.EDGE_MIDDLE_ALPHA),
            1f to edge.copy(alpha = GlassConfig.EDGE_BOTTOM_ALPHA),
        ),
        shape = shape,
    )
