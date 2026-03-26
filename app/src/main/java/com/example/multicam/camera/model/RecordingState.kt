package com.example.multicam.camera.model

import android.net.Uri

data class RecordingState(
    val isRecording: Boolean = false,
    val statusText: String = "待機中",
    val lastSavedUri: Uri? = null,
    val errorMessage: String? = null
)
