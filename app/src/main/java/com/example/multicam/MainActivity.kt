package com.example.multicam

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.multicam.camera.CameraCapabilityChecker
import com.example.multicam.camera.CompositeRecordingController
import com.example.multicam.camera.ConcurrentCameraController
import com.example.multicam.camera.model.CameraCapabilityReport
import com.example.multicam.camera.model.CaptureMode
import com.example.multicam.camera.model.RecordingState
import com.example.multicam.ui.screen.CameraScreen
import com.example.multicam.ui.screen.CameraScreenUiState
import com.example.multicam.ui.screen.PreviewViews
import com.example.multicam.ui.theme.MultiCamTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val capabilityChecker by lazy { CameraCapabilityChecker(this) }
    private val recordingController = CompositeRecordingController()

    private var previewViews: PreviewViews? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraController: ConcurrentCameraController? = null
    private var capabilityReport: CameraCapabilityReport? = null
    private var hasInitializedCamera by mutableStateOf(false)
    private var hasRequiredPermissions by mutableStateOf(false)
    private var capabilityMessage by mutableStateOf("権限待機中です。")
    private var recordingState by mutableStateOf(RecordingState())
    private var captureMode by mutableStateOf(CaptureMode.SINGLE_BACK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasRequiredPermissions = hasRequiredPermissions()

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { grantResults ->
                hasRequiredPermissions = grantResults[Manifest.permission.CAMERA] == true
                if (hasRequiredPermissions) {
                    initializeCameraIfPossible()
                } else {
                    capabilityMessage = "カメラ権限がないため開始できません。"
                }
            }

            MultiCamTheme {
                CameraScreen(
                    uiState = CameraScreenUiState(
                        hasRequiredPermissions = hasRequiredPermissions,
                        isInitialized = hasInitializedCamera,
                        capabilityMessage = capabilityMessage,
                        recordingState = recordingState,
                        captureMode = captureMode
                    ),
                    onPreviewViewsReady = { views ->
                        previewViews = views
                        initializeCameraIfPossible()
                    },
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    },
                    onRecordClick = ::toggleRecording
                )
            }
        }

        if (hasRequiredPermissions) {
            initializeCameraIfPossible()
        }
    }

    @Suppress("DEPRECATION")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rotation = windowManager.defaultDisplay.rotation
        cameraController?.updateTargetRotation(rotation)
        
        // 録画中でない場合は、画面の向きに合わせてレイアウト（左右・上下）を更新する
        if (!recordingState.isRecording) {
            val views = previewViews ?: return
            val provider = cameraProvider ?: return
            bindCamera(views, provider)
        }
    }

    override fun onPause() {
        super.onPause()
        if (recordingState.isRecording) {
            recordingController.stopRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController?.clear()
        recordingController.release()
    }

    private fun initializeCameraIfPossible() {
        val currentPreviewViews = previewViews ?: return
        if (!hasRequiredPermissions) {
            return
        }
        val provider = cameraProvider
        if (provider != null) {
            bindCamera(currentPreviewViews, provider)
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    cameraProvider = future.get()
                    cameraController = ConcurrentCameraController(requireNotNull(cameraProvider))
                    bindCamera(currentPreviewViews, requireNotNull(cameraProvider))
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Unable to obtain camera provider.", throwable)
                    capabilityMessage = "カメラプロバイダ初期化に失敗しました。"
                    recordingState = recordingState.copy(
                        errorMessage = throwable.message ?: "provider error"
                    )
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCamera(
        previewViews: PreviewViews,
        provider: ProcessCameraProvider
    ) {
        lifecycleScope.launch {
            try {
                val report = capabilityChecker.evaluate(provider)
                capabilityReport = report
                captureMode = report.mode
                capabilityMessage = report.userMessage
                
                val videoCapture = recordingController.ensureVideoCapture(report.requestedQuality)

                cameraController?.bind(
                    lifecycleOwner = this@MainActivity,
                    report = report,
                    compositePreviewView = previewViews.composite,
                    backPreviewView = previewViews.back,
                    videoCapture = videoCapture
                )
                hasInitializedCamera = true
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to initialize camera.", throwable)
                hasInitializedCamera = false
                capabilityMessage = "カメラ初期化に失敗しました。"
                recordingState = recordingState.copy(
                    errorMessage = throwable.message ?: "初期化エラー"
                )
            }
        }
    }

    private fun toggleRecording() {
        if (!hasInitializedCamera) {
            return
        }
        if (recordingState.isRecording) {
            recordingController.stopRecording()
            return
        }
        val enableAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!enableAudio && !capabilityMessage.contains("無音録画")) {
            capabilityMessage = "$capabilityMessage 音声権限がないため無音録画に切り替えます。"
        }
        recordingController.startRecording(
            context = this,
            enableAudio = enableAudio
        ) { state ->
            recordingState = state
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
