package com.example.halauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.GlassConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig

/*
 * The pieces search's bottom bar is made of, shared with the other screens that search (the pin
 * picker), so they all look and sit the same and change together.
 */

/** The shape of a search field: a pill. */
val SearchFieldShape = RoundedCornerShape(percent = 50)

/**
 * A single-line search field on glass: a plain TextField with its fill and underline cleared, so
 * the glass is the surface, and a search icon at its start.
 *
 * @param leadingIconDescription names the field for screen readers when there's no [placeholder]
 * (the field reads out what's inside it); null while a placeholder does that.
 */
@Composable
fun GlassSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIconDescription: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val placeholderColor = ThemeConfig.Foreground.copy(alpha = SearchConfig.PLACEHOLDER_ALPHA)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.liquidGlass(SearchFieldShape, tint = SearchConfig.FIELD_GLASS_TINT),
        shape = SearchFieldShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = ThemeConfig.Foreground,
            unfocusedTextColor = ThemeConfig.Foreground,
            cursorColor = ThemeConfig.Foreground,
            focusedLeadingIconColor = ThemeConfig.Foreground,
            unfocusedLeadingIconColor = ThemeConfig.Foreground,
            focusedTrailingIconColor = ThemeConfig.Foreground,
            unfocusedTrailingIconColor = ThemeConfig.Foreground,
            focusedPlaceholderColor = placeholderColor,
            unfocusedPlaceholderColor = placeholderColor,
        ),
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = leadingIconDescription) },
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

/**
 * A round glass button showing [icon], such as search's grid/list toggle or the pin picker's Done.
 * [action] makes it do something (clickable, toggleable) and goes inside the glass, so its ripple
 * is round; [modifier] places it. [highlighted] adds the sheen of a switch that's on.
 */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp,
    iconSize: Dp,
    action: Modifier,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tint: Float = GlassConfig.DEFAULT_TINT,
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(CircleShape, tint = tint)
            .then(
                if (highlighted) {
                    Modifier.background(ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.THUMB_SHEEN_ALPHA), CircleShape)
                } else {
                    Modifier
                },
            )
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The gap under a bottom search bar: small while the keyboard is up (its own edge sets the bar
 * apart, so the results get the room), the usual margin otherwise.
 */
fun searchBarBottomGap(imeVisible: Boolean): Dp =
    if (imeVisible) SearchConfig.KeyboardGap else SearchConfig.BottomSpacing
