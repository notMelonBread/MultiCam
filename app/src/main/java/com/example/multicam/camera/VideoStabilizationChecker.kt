package com.example.multicam.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

class VideoStabilizationChecker {
    @SuppressLint("UnsafeOptInUsageError")
    fun isSupported(cameraInfo: CameraInfo): Boolean {
        val modes = Camera2CameraInfo.from(cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        return isEisSupported(modes)
    }

    internal fun isEisSupported(availableModes: IntArray?): Boolean {
        return availableModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
    }
}
