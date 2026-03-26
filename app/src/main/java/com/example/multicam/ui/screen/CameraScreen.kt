package com.example.multicam.ui.screen

import android.content.Intent
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.multicam.camera.model.CaptureMode
import com.example.multicam.camera.model.RecordingState
import com.example.multicam.ui.theme.MultiCamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CameraScreenUiState(
    val hasRequiredPermissions: Boolean,
    val isInitialized: Boolean,
    val capabilityMessage: String,
    val recordingState: RecordingState,
    val captureMode: CaptureMode
)

@Composable
fun CameraScreen(
    uiState: CameraScreenUiState,
    onPreviewViewsReady: (PreviewViews) -> Unit,
    onRequestPermissions: () -> Unit,
    onRecordClick: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val previewViews = remember {
        PreviewViews(
            composite = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            },
            back = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            },
            front = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        )
    }

    LaunchedEffect(previewViews) {
        onPreviewViewsReady(previewViews)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.captureMode == CaptureMode.CONCURRENT_COMPOSITE) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewViews.composite }
            )
        } else {
            SplitPreview(
                previewViews = previewViews,
                isLandscape = isLandscape,
                hasFrontCamera = false
            )
        }

        CameraOverlay(
            uiState = uiState,
            isLandscape = isLandscape,
            onRequestPermissions = onRequestPermissions,
            onRecordClick = onRecordClick
        )
    }
}


@Composable
private fun SplitPreview(
    previewViews: PreviewViews,
    isLandscape: Boolean,
    hasFrontCamera: Boolean
) {
    val frontFallbackText = if (!hasFrontCamera) "この端末では録画対象外です。" else null
    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            PreviewPane(modifier = Modifier.weight(1f), previewView = previewViews.back)
            PreviewPane(
                modifier = Modifier.weight(1f),
                previewView = previewViews.front,
                fallbackText = frontFallbackText
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            PreviewPane(modifier = Modifier.weight(1f), previewView = previewViews.back)
            PreviewPane(
                modifier = Modifier.weight(1f),
                previewView = previewViews.front,
                fallbackText = frontFallbackText
            )
        }
    }
}

@Composable
private fun PreviewPane(
    modifier: Modifier,
    previewView: PreviewView,
    fallbackText: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                clip = true
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )
        fallbackText?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}


@Composable
private fun LastVideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, uri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                value = retriever.getFrameAtTime(0)?.asImageBitmap()
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }
        }
    }

    Box(
        modifier = modifier
            .size(width = 80.dp, height = 50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "動画を再生",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun OverlayMessage(
    modifier: Modifier = Modifier,
    text: String?
) {
    text?.let {
        Surface(
            modifier = Modifier
                .then(modifier)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = it,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun RecordingIndicator(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Box(
            modifier = Modifier
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFFFF3B30), CircleShape)
            )
        }
    }
}

@Composable
private fun CameraOverlay(
    uiState: CameraScreenUiState,
    isLandscape: Boolean,
    onRequestPermissions: () -> Unit,
    onRecordClick: () -> Unit,
    showIndicator: Boolean = uiState.recordingState.isRecording
) {
    val context = LocalContext.current
    ConstraintLayout(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val (msgRef, indicatorRef, recordRef, thumbnailRef) = createRefs()

        OverlayMessage(
            modifier = Modifier.constrainAs(msgRef) {
                top.linkTo(parent.top, margin = 20.dp)
                centerHorizontallyTo(parent)
            },
            text = uiState.recordingState.errorMessage
                ?: if (!uiState.hasRequiredPermissions) "カメラ権限が必要です" else null
        )

        if (showIndicator) {
            RecordingIndicator(
                modifier = Modifier.constrainAs(indicatorRef) {
                    top.linkTo(parent.top, margin = 20.dp)
                    end.linkTo(parent.end, margin = 20.dp)
                }
            )
        }

        Box(
            modifier = Modifier.constrainAs(recordRef) {
                if (isLandscape) {
                    end.linkTo(parent.end, margin = 28.dp)
                    centerVerticallyTo(parent)
                } else {
                    bottom.linkTo(parent.bottom, margin = 28.dp)
                    centerHorizontallyTo(parent)
                }
            },
            contentAlignment = Alignment.Center
        ) {
            if (!uiState.hasRequiredPermissions) {
                Button(onClick = onRequestPermissions) {
                    Text("権限を許可")
                }
            } else {
                RecordButton(
                    enabled = uiState.isInitialized,
                    isRecording = uiState.recordingState.isRecording,
                    onClick = onRecordClick
                )
            }
        }

        uiState.recordingState.lastSavedUri?.let { uri ->
            LastVideoThumbnail(
                uri = uri,
                modifier = Modifier.constrainAs(thumbnailRef) {
                    top.linkTo(recordRef.bottom, margin = 32.dp)
                    centerHorizontallyTo(recordRef)
                }
            ) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun RecordButton(
    enabled: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .border(
                width = 5.dp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .padding(6.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(0xFFFF3B30), RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }
    }
}

data class PreviewViews(
    val composite: PreviewView,
    val back: PreviewView,
    val front: PreviewView
)

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    MultiCamTheme {
        CameraScreen(
            uiState = CameraScreenUiState(
                hasRequiredPermissions = true,
                isInitialized = true,
                capabilityMessage = "Ready",
                recordingState = RecordingState(),
                captureMode = CaptureMode.CONCURRENT_COMPOSITE
            ),
            onPreviewViewsReady = {},
            onRequestPermissions = {},
            onRecordClick = {}
        )
    }
}
