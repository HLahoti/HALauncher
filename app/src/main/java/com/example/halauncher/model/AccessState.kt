package com.example.halauncher.model

/**
 * Where a permission the launcher uses (contacts, file access) stands. Setup, settings and search
 * all word things differently for a permission that was never asked for and one the user turned
 * down, so the two are kept apart rather than folded into a single "not granted".
 */
enum class AccessState {
    /** Granted: the source is searched. */
    GRANTED,

    /** Not granted, and never asked for: offering it is just an invitation. */
    NOT_REQUESTED,

    /** Asked for and turned down (or later turned off in system Settings). */
    DENIED;

    val isGranted: Boolean get() = this == GRANTED

    companion object {
        fun of(granted: Boolean, requested: Boolean): AccessState = when {
            granted -> GRANTED
            requested -> DENIED
            else -> NOT_REQUESTED
        }
    }
}
