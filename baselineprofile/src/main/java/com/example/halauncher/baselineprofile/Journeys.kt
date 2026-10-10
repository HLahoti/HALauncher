package com.example.halauncher.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope

const val PACKAGE_NAME = "com.harshlahoti.halauncher"

/**
 * The launcher's everyday moments: swipe search in, type, clear, leave; turn the pinned sphere.
 * Shared by the profile generator (so this code is precompiled) and the benchmarks.
 */
fun MacrobenchmarkScope.useHomeScreen() {
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(2) {
        // Swipe up from the lower middle of the home screen to bring search in.
        device.swipe(width / 2, height * 4 / 5, width / 2, height * 2 / 5, 15)
        device.waitForIdle()
        for (key in listOf(KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_P)) {
            device.pressKeyCode(key)
            Thread.sleep(150)
        }
        // Past the pause after which the disk layer answers too.
        Thread.sleep(600)
        repeat(3) { device.pressKeyCode(KeyEvent.KEYCODE_DEL) }
        device.pressHome()
        device.waitForIdle()
    }
    // Turn the pinned-apps sphere on the right edge.
    device.swipe(width * 5 / 6, height / 2, width * 5 / 6, height * 2 / 3, 20)
    device.waitForIdle()
}
