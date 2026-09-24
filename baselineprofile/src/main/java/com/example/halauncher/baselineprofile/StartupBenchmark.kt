package com.example.halauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold startup and a search session, with and without the baseline profile, to show what it
 * saves. Run with `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun searchWithoutProfile() = search(CompilationMode.None())

    @Test
    fun searchWithProfile() = search(CompilationMode.Partial(BaselineProfileMode.Require))

    /** Puts another app in front, so the launcher can be killed and cold-started. */
    private fun MacrobenchmarkScope.leaveHome() {
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
    }

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        // Not pressHome(): this is the Home app, so that would start it before the cold start.
        setupBlock = { leaveHome() },
    ) {
        startActivityAndWait()
    }

    private fun search(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // Already started in setup; this measures the session, not a start.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            leaveHome()
            startActivityAndWait()
        },
    ) {
        useHomeScreen()
    }
}
