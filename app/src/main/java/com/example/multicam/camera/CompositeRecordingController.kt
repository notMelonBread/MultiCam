package com.example.multicam.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.example.multicam.camera.model.RecordingState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompositeRecordingController {
    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var configuredQuality: Quality? = null

    fun ensureVideoCapture(quality: Quality): VideoCapture<Recorder> {
        if (videoCapture != null && configuredQuality == quality) {
            return videoCapture!!
        }
        stopRecording()
        val selector = QualitySelector.fromOrderedList(
            listOf(quality),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )
        recorder = Recorder.Builder()
            .setQualitySelector(selector)
            .build()
        configuredQuality = quality
        videoCapture = VideoCapture.withOutput(requireNotNull(recorder))
        return requireNotNull(videoCapture)
    }

    fun startRecording(
        context: Context,
        enableAudio: Boolean,
        onStateChanged: (RecordingState) -> Unit
    ) {
        val recorder = requireNotNull(recorder) { "Recorder is not configured." }
        if (activeRecording != null) {
            return
        }

        val pendingRecording = buildPendingRecording(context, recorder)
        val finalPendingRecording = if (enableAudio) {
            pendingRecording.withAudioEnabled()
        } else {
            pendingRecording
        }

        activeRecording = finalPendingRecording.start(
            ContextCompat.getMainExecutor(context)
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    onStateChanged(
                        RecordingState(
                            isRecording = true,
                            statusText = "録画中",
                            errorMessage = null
                        )
                    )
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    val errorMessage = if (event.hasError()) {
                        "録画に失敗しました: ${event.error}"
                    } else {
                        null
                    }
                    onStateChanged(
                        RecordingState(
                            isRecording = false,
                            statusText = if (errorMessage == null) "保存完了" else "録画停止",
                            lastSavedUri = event.outputResults.outputUri,
                            errorMessage = errorMessage
                        )
                    )
                }

                else -> Unit
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    fun release() {
        stopRecording()
        activeRecording = null
        recorder = null
        videoCapture = null
        configuredQuality = null
    }

    private fun buildPendingRecording(
        context: Context,
        recorder: Recorder
    ): PendingRecording {
        val fileName = FILE_NAME_FORMAT.format(Date())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + File.separator + "MultiCam"
                )
            }
            val options = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()
            recorder.prepareRecording(context, options)
        } else {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                "MultiCam"
            ).apply { mkdirs() }
            val file = File(directory, "$fileName.mp4")
            val options = FileOutputOptions.Builder(file).build()
            recorder.prepareRecording(context, options)
        }
    }

    companion object {
        private val FILE_NAME_FORMAT =
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
    }
}
