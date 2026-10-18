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
        SearchCategory.SHORTCUT -> ShortcutIcon
        SearchCategory.FOLDER -> FolderIcon
    }

/** Spoken name of the category, e.g. for the badge on a search result. */
@get:StringRes
val SearchCategory.labelRes: Int
    get() = when (this) {
        SearchCategory.APP -> R.string.category_app
        SearchCategory.CONTACT -> R.string.category_contact
        SearchCategory.FILE -> R.string.category_file
        SearchCategory.SHORTCUT -> R.string.category_shortcut
        SearchCategory.FOLDER -> R.string.category_folder
    }

// material-icons-core has no app-grid, file, shortcut or folder glyph, so these reproduce the Material
// "Apps", "Insert Drive File", "Shortcut" and "Folder" icons rather than pulling in the large extended icon set.

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

private val ShortcutIcon: ImageVector by lazy {
    materialIcon(
        name = "Shortcut",
        pathData = "M21,11l-6,-6v5H8c-2.76,0 -5,2.24 -5,5v4h2v-4c0,-1.65 1.35,-3 3,-3h7v5L21,11z",
    )
}

private val FolderIcon: ImageVector by lazy {
    materialIcon(
        name = "Folder",
        pathData = "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
    )
}

/** Material "Grid View": shown on the toggle that lays search's apps out as a grid. */
val GridLayoutIcon: ImageVector by lazy {
    materialIcon(
        name = "GridView",
        pathData = "M3,3v8h8V3H3zM13,3v8h8V3H13zM3,13v8h8v-8H3zM13,13v8h8v-8H13z",
    )
}

/** Material "View List": shown on the toggle that lays search's apps out as a list. */
val ListLayoutIcon: ImageVector by lazy {
    materialIcon(
        name = "ViewList",
        pathData = "M3,14h4v-4H3v4zM3,19h4v-4H3v4zM3,9h4V5H3v4zM8,14h13v-4H8v4zM8,19h13v-4H8v4zM8,5v4h13V5H8z",
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
