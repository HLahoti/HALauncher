package com.example.halauncher.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.example.halauncher.R
import com.example.halauncher.model.SearchCategory

/**
 * The icon shown for every search result of this category. The `when` is exhaustive, so
 * a new category won't compile until it has an icon.
 */
val SearchCategory.icon: ImageVector
    get() = when (this) {
        SearchCategory.APP -> AppsIcon
        SearchCategory.CONTACT -> Icons.Default.Person
        SearchCategory.FILE -> FileIcon
    }

/** Spoken name of the category, e.g. for the badge on a search result. */
@get:StringRes
val SearchCategory.labelRes: Int
    get() = when (this) {
        SearchCategory.APP -> R.string.category_app
        SearchCategory.CONTACT -> R.string.category_contact
        SearchCategory.FILE -> R.string.category_file
    }

// material-icons-core has no app-grid or file glyph, so these two reproduce the Material
// "Apps" and "Insert Drive File" icons rather than pulling in the large extended icon set.

private val AppsIcon: ImageVector by lazy {
    materialIcon(
        name = "Apps",
        pathData = "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4zM10,14h4v-4h-4v4z" +
            "M16,4v4h4V4h-4zM10,8h4V4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
    )
}

private val FileIcon: ImageVector by lazy {
    materialIcon(
        name = "InsertDriveFile",
        pathData = "M6,2c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8" +
            "l-6,-6H6zM13,9V3.5L18.5,9H13z",
    )
}

/** A 24dp Material-style filled icon from SVG path data (tinted by Icon at draw time). */
private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
