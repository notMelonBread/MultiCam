package com.example.multicam.camera.model

import androidx.camera.core.CameraInfo
import androidx.camera.video.Quality

enum class CaptureMode {
    CONCURRENT_COMPOSITE,
    SINGLE_BACK
}

data class CameraCapabilityReport(
    val mode: CaptureMode,
    val requestedQuality: Quality,
    val backCameraInfo: CameraInfo,
    val frontCameraInfo: CameraInfo? = null,
    val logs: List<String> = emptyList(),
    val userMessage: String
)
