package com.example.multicam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import com.example.multicam.camera.model.CameraCapabilityReport
import com.example.multicam.camera.model.CaptureMode

class CameraCapabilityChecker(
    private val context: Context
) {
    private val stabilizationChecker = VideoStabilizationChecker()

    fun evaluate(cameraProvider: ProcessCameraProvider): CameraCapabilityReport {
        val logs = mutableListOf<String>()
        val backCameraInfo = findCameraInfo(
            cameraProvider = cameraProvider,
            lensFacing = CameraCharacteristics.LENS_FACING_BACK
        ) ?: error("背面カメラが見つかりません。")
        val frontCameraInfo = findCameraInfo(
            cameraProvider = cameraProvider,
            lensFacing = CameraCharacteristics.LENS_FACING_FRONT
        )
        val requestedQuality = findRequestedQuality(
            primary = backCameraInfo,
            secondary = frontCameraInfo
        )

        logs += "Stage 1 concurrent camera availability"
        val concurrentSupported = supportsFrontBackConcurrent(cameraProvider)
        if (!concurrentSupported) {
            logs += "Concurrent camera is not available. Falling back to single back camera."
            val stabilizationSupported = stabilizationChecker.isSupported(backCameraInfo)
            logs += "Back camera stabilization supported: $stabilizationSupported"
            return CameraCapabilityReport(
                mode = CaptureMode.SINGLE_BACK,
                requestedQuality = requestedQuality.backOnlyQuality,
                backCameraInfo = backCameraInfo,
                isStabilizationSupported = stabilizationSupported,
                userMessage = "同時カメラ非対応のため背面カメラのみで録画します。",
                logs = logs
            ).also(::logReport)
        }

        logs += "Stage 2 dual concurrent video composition availability"
        val compositionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT) &&
            frontCameraInfo != null &&
            requestedQuality.sharedQuality != null
        if (!compositionSupported) {
            val message = when {
                frontCameraInfo == null -> "前面カメラがないため背面カメラのみで録画します。"
                requestedQuality.sharedQuality == null ->
                    "前後カメラで共有できる録画品質がないため背面カメラのみで録画します。"
                else -> "合成録画条件を満たせないため背面カメラのみで録画します。"
            }
            logs += "Dual composition is unavailable. Falling back to single back camera."
            val stabilizationSupported = stabilizationChecker.isSupported(backCameraInfo)
            logs += "Back camera stabilization supported: $stabilizationSupported"
            return CameraCapabilityReport(
                mode = CaptureMode.SINGLE_BACK,
                requestedQuality = requestedQuality.backOnlyQuality,
                backCameraInfo = backCameraInfo,
                frontCameraInfo = frontCameraInfo,
                isStabilizationSupported = stabilizationSupported,
                userMessage = message,
                logs = logs
            ).also(::logReport)
        }

        logs += "Stage 3 requested quality availability"
        val sharedQuality = requestedQuality.sharedQuality
            ?: error("sharedQuality should be available in concurrent composite mode.")
        logs += "Concurrent composite recording selected with quality=$sharedQuality"
        val stabilizationSupported = stabilizationChecker.isSupported(backCameraInfo)
        logs += "Concurrent stabilization supported: $stabilizationSupported"
        return CameraCapabilityReport(
            mode = CaptureMode.CONCURRENT_COMPOSITE,
            requestedQuality = sharedQuality,
            backCameraInfo = backCameraInfo,
            frontCameraInfo = frontCameraInfo,
            isStabilizationSupported = stabilizationSupported,
            userMessage = "前後カメラの同時合成録画を使用します。",
            logs = logs
        ).also(::logReport)
    }

    private fun supportsFrontBackConcurrent(cameraProvider: ProcessCameraProvider): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
            return false
        }
        return cameraProvider.availableConcurrentCameraInfos.any { group ->
            val hasBack = group.any { lensFacingOf(it) == CameraCharacteristics.LENS_FACING_BACK }
            val hasFront = group.any { lensFacingOf(it) == CameraCharacteristics.LENS_FACING_FRONT }
            hasBack && hasFront
        }
    }

    private fun findCameraInfo(
        cameraProvider: ProcessCameraProvider,
        lensFacing: Int
    ): CameraInfo? {
        return cameraProvider.availableCameraInfos.firstOrNull { lensFacingOf(it) == lensFacing }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun lensFacingOf(cameraInfo: CameraInfo): Int? {
        return Camera2CameraInfo.from(cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
    }

    private fun findRequestedQuality(
        primary: CameraInfo,
        secondary: CameraInfo?
    ): RequestedQualityResult {
        val preferredQualities = listOf(Quality.FHD, Quality.HD, Quality.SD)
        val sdr = DynamicRange.SDR
        val primaryQualities = Recorder.getVideoCapabilities(primary).getSupportedQualities(sdr)
        val backOnlyQuality = preferredQualities.firstOrNull(primaryQualities::contains) ?: Quality.LOWEST
        val sharedQuality = secondary?.let {
            val secondaryQualities =
                Recorder.getVideoCapabilities(it).getSupportedQualities(sdr)
            preferredQualities.firstOrNull { quality ->
                primaryQualities.contains(quality) && secondaryQualities.contains(quality)
            }
        }
        return RequestedQualityResult(
            sharedQuality = sharedQuality,
            backOnlyQuality = backOnlyQuality
        )
    }

    private fun logReport(report: CameraCapabilityReport) {
        report.logs.forEach { Log.i(TAG, it) }
        Log.i(TAG, "Selected mode=${report.mode}, quality=${report.requestedQuality}")
    }

    private data class RequestedQualityResult(
        val sharedQuality: Quality?,
        val backOnlyQuality: Quality
    )

    companion object {
        private const val TAG = "CameraCapability"
    }
}
