package com.example.multicam.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CaptureRequest
import android.util.Rational
import android.util.Log
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.multicam.camera.model.CameraCapabilityReport
import com.example.multicam.camera.model.CaptureMode

class ConcurrentCameraController(
    private val cameraProvider: ProcessCameraProvider
) {
    private var boundConcurrentCamera: ConcurrentCamera? = null
    private var backPreview: Preview? = null
    private var compositePreview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        report: CameraCapabilityReport,
        compositePreviewView: PreviewView,
        backPreviewView: PreviewView,
        videoCapture: VideoCapture<Recorder>
    ) {
        clear()
        this.videoCapture = videoCapture
        
        // 常に横向き（ROTATION_90）として扱う
        val targetRotation = Surface.ROTATION_90
        videoCapture.targetRotation = targetRotation

        when (report.mode) {
            CaptureMode.CONCURRENT_COMPOSITE -> bindComposite(
                lifecycleOwner = lifecycleOwner,
                compositePreviewView = compositePreviewView,
                videoCapture = videoCapture,
                targetRotation = targetRotation,
                enableStabilization = report.isStabilizationSupported
            )

            CaptureMode.SINGLE_BACK -> bindSingleBack(
                lifecycleOwner = lifecycleOwner,
                backPreviewView = backPreviewView,
                videoCapture = videoCapture,
                targetRotation = targetRotation,
                enableStabilization = report.isStabilizationSupported
            )
        }
    }

    fun updateTargetRotation(rotation: Int) {
        // 固定なので更新しない
    }

    fun clear() {
        cameraProvider.unbindAll()
        boundConcurrentCamera = null
        backPreview = null
        compositePreview = null
        videoCapture = null
    }

    private fun bindComposite(
        lifecycleOwner: LifecycleOwner,
        compositePreviewView: PreviewView,
        videoCapture: VideoCapture<Recorder>,
        targetRotation: Int,
        enableStabilization: Boolean
    ) {
        compositePreview = buildPreviewBuilder(enableStabilization)
            .setResolutionSelector(buildResolutionSelector())
            .build()
            .also {
                it.targetRotation = targetRotation
                it.surfaceProvider = compositePreviewView.surfaceProvider
            }

        // 常に横向き (16:9) のビューポートを設定
        val viewPort = ViewPort.Builder(Rational(16, 9), targetRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(requireNotNull(compositePreview))
            .addUseCase(videoCapture)
            .setViewPort(viewPort)
            .build()

        // 常に左右分割に固定
        val backConfig = ConcurrentCamera.SingleCameraConfig(
            CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup,
            backCompositionSettings(),
            lifecycleOwner
        )
        val frontConfig = ConcurrentCamera.SingleCameraConfig(
            CameraSelector.DEFAULT_FRONT_CAMERA,
            useCaseGroup,
            frontCompositionSettings(),
            lifecycleOwner
        )
        boundConcurrentCamera = cameraProvider.bindToLifecycle(listOf(backConfig, frontConfig))
        boundConcurrentCamera?.cameras?.forEach { applyZoom(it, enableStabilization) }
        Log.i(TAG, "Concurrent composite camera bound (Fixed Landscape).")
    }

    private fun bindSingleBack(
        lifecycleOwner: LifecycleOwner,
        backPreviewView: PreviewView,
        videoCapture: VideoCapture<Recorder>,
        targetRotation: Int,
        enableStabilization: Boolean
    ) {
        backPreview = buildPreviewBuilder(enableStabilization)
            .setResolutionSelector(buildResolutionSelector())
            .build()
            .also {
                it.targetRotation = targetRotation
                it.surfaceProvider = backPreviewView.surfaceProvider
            }

        val viewPort = ViewPort.Builder(Rational(16, 9), targetRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(requireNotNull(backPreview))
            .addUseCase(videoCapture)
            .setViewPort(viewPort)
            .build()

        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup
        )
        applyZoom(camera, enableStabilization)
        Log.i(TAG, "Single back camera bound.")
    }

    private fun applyZoom(camera: Camera, enableStabilization: Boolean) {
        if (enableStabilization) {
            // 手振れ補正対応時: 広角カメラ(1x)を使用。超広角はEIS非対応のため使わない
            camera.cameraControl.setZoomRatio(1.0f)
        } else {
            // 手振れ補正非対応時: 超広角で可能な限り広く映す
            camera.cameraControl.setLinearZoom(0.0f)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun buildPreviewBuilder(enableStabilization: Boolean): Preview.Builder {
        val builder = Preview.Builder()
        if (enableStabilization) {
            Camera2Interop.Extender(builder)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            Log.i(TAG, "Video stabilization enabled.")
        }
        return builder
    }

    private fun buildResolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    AspectRatio.RATIO_16_9,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()
    }

    private fun backCompositionSettings(): CompositionSettings {
        return CompositionSettings.Builder()
            .setOffset(-1f, 0f)
            .build()
    }

    private fun frontCompositionSettings(): CompositionSettings {
        return CompositionSettings.Builder()
            .setOffset(1f, 0f)
            .build()
    }

    companion object {
        private const val TAG = "ConcurrentCameraCtrl"
    }
}
