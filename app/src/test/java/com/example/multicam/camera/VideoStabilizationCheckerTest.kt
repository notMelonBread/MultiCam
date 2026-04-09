package com.example.multicam.camera

import android.hardware.camera2.CameraMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStabilizationCheckerTest {
    private val checker = VideoStabilizationChecker()

    @Test
    fun isEisSupported_returnsTrue_whenModeOnIsAvailable() {
        val modes = intArrayOf(
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
        )
        assertTrue(checker.isEisSupported(modes))
    }

    @Test
    fun isEisSupported_returnsFalse_whenOnlyOffModeIsAvailable() {
        val modes = intArrayOf(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        assertFalse(checker.isEisSupported(modes))
    }

    @Test
    fun isEisSupported_returnsFalse_whenModesIsNull() {
        assertFalse(checker.isEisSupported(null))
    }

    @Test
    fun isEisSupported_returnsFalse_whenModesIsEmpty() {
        assertFalse(checker.isEisSupported(intArrayOf()))
    }
}
