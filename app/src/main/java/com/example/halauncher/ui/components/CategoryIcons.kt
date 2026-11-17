package com.example.halauncher.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
        SearchCategory.EVENT -> EventIcon
        SearchCategory.FILE -> FileIcon
        SearchCategory.SHORTCUT -> ShortcutIcon
        SearchCategory.FOLDER -> FolderIcon
        SearchCategory.SETTING -> Icons.Default.Settings
    }

/** Spoken name of the category, e.g. for the badge on a search result. */
@get:StringRes
val SearchCategory.labelRes: Int
    get() = when (this) {
        SearchCategory.APP -> R.string.category_app
        SearchCategory.CONTACT -> R.string.category_contact
        SearchCategory.EVENT -> R.string.category_event
        SearchCategory.FILE -> R.string.category_file
        SearchCategory.SHORTCUT -> R.string.category_shortcut
        SearchCategory.FOLDER -> R.string.category_folder
        SearchCategory.SETTING -> R.string.category_setting
    }

// material-icons-core has no app-grid, event, file, shortcut, folder, timer, alarm, message or link
// glyph, so these reproduce the Material ones rather than pulling in the large extended icon set.

private val AppsIcon: ImageVector by lazy {
    materialIcon(
        name = "Apps",
        pathData = "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4zM10,14h4v-4h-4v4z" +
            "M16,4v4h4V4h-4zM10,8h4V4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
    )
}

/** Material "Event": calendar events, and the quick action that adds one. */
val EventIcon: ImageVector by lazy {
    materialIcon(
        name = "Event",
        pathData = "M17,12h-5v5h5v-5zM16,1v2L8,3L8,1L6,1v2L5,3c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14" +
            "c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2h-1L18,1h-2zM19,19L5,19L5,8h14v11z",
    )
}

/** Material "Timer": the quick action that starts one. */
val TimerIcon: ImageVector by lazy {
    materialIcon(
        name = "Timer",
        pathData = "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42" +
            "C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61z" +
            "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
    )
}

/** Material "Alarm": the quick action that sets one. */
val AlarmIcon: ImageVector by lazy {
    materialIcon(
        name = "Alarm",
        pathData = "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
            "M12.5,8L11,8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9" +
            "c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
    )
}

/** Material "Message": the quick action that texts a number. */
val MessageIcon: ImageVector by lazy {
    materialIcon(
        name = "Message",
        pathData = "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
            "M18,14H6v-2h12v2zM18,11H6V9h12v2zM18,8H6V6h12v2z",
    )
}

/** Material "Link": the quick action that opens a web address. */
val LinkIcon: ImageVector by lazy {
    materialIcon(
        name = "Link",
        pathData = "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4L11,7L7,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9L7,15.1" +
            "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2L8,11v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4L13,17h4" +
            "c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
    )
}

/** Material "Filter List": the filter pill beside search's field. */
val FilterIcon: ImageVector by lazy {
    materialIcon(
        name = "FilterList",
        pathData = "M10,18h4v-2h-4v2zM3,6v2h18V6H3zM6,13h12v-2H6v2z",
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
