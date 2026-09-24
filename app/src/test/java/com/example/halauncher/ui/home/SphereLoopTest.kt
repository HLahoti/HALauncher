package com.example.halauncher.ui.home

import com.example.halauncher.config.SphereConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SphereLoopTest {
    @Test
    fun lastAppSitsJustAboveTheFirst() {
        // 8 apps, first at the front: the last one is one slot above it, not seven below.
        assertEquals(-1f, loopOffset(index = 7, rotation = 0f, count = 8), 1e-6f)
        assertEquals(1f, loopOffset(index = 1, rotation = 0f, count = 8), 1e-6f)
    }

    @Test
    fun turningPastTheLastWrapsToTheFirst() {
        // Rotation 7.6 is between the last app (7) and the first again (8 ≡ 0).
        assertEquals(0.4f, loopOffset(index = 0, rotation = 7.6f, count = 8), 1e-5f)
        assertEquals(-0.6f, loopOffset(index = 7, rotation = 7.6f, count = 8), 1e-5f)
    }

    @Test
    fun negativeRotationWrapsToo() {
        assertEquals(1f, loopOffset(index = 7, rotation = -2f, count = 8), 1e-6f)
    }

    @Test
    fun offsetsStayWithinHalfALap() {
        for (count in 1..8) for (index in 0 until count) for (step in -40..40) {
            val offset = loopOffset(index, step / 4f, count)
            assertTrue(offset >= -count / 2f && offset < count / 2f)
        }
    }

    @Test
    fun loopSeamIsAlwaysAtOrPastTheVisibleEdge() {
        for (count in 2..8) {
            // The seam is half a lap from the front.
            val seamDegrees = count / 2f * loopStepDegrees(count)
            assertTrue("seam at $seamDegrees° for $count pins", seamDegrees >= 90f)
        }
        assertEquals(SphereConfig.ANGLE_STEP_DEGREES, loopStepDegrees(8), 0f)
        assertEquals(45f, loopStepDegrees(4), 0f)
    }
}
