package com.example.halauncher.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.halauncher.config.GlassMenuConfig
import com.example.halauncher.config.ThemeConfig

private val MenuShape = RoundedCornerShape(GlassMenuConfig.CornerRadius)

/**
 * A long-press menu on the launcher's liquid glass: a [DropdownMenu] with rounded corners, no
 * solid Material surface or shadow, and light text. Fill it with [GlassMenuTitle] and
 * [GlassMenuItem].
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.liquidGlass(MenuShape, tint = GlassMenuConfig.GLASS_TINT),
        shape = MenuShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        // A shadow would show through the glass as a dark smudge.
        shadowElevation = 0.dp,
        content = content,
    )
}

/** A greyed, non-interactive first line naming what the menu is for. */
@Composable
fun GlassMenuTitle(text: String) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {},
        enabled = false,
        colors = MenuDefaults.itemColors(
            disabledTextColor = ThemeConfig.Foreground.copy(alpha = GlassMenuConfig.TITLE_ALPHA),
        ),
    )
}

/** A line of the menu. */
@Composable
fun GlassMenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = ThemeConfig.Foreground),
    )
}
