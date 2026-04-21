package com.example.newocr2.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.newocr2.R
import com.example.newocr2.util.downscaleIfNeeded
import com.example.newocr2.util.rotate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CameraX 기반 촬영 화면.
 *
 * ## 흐름
 * 1. CAMERA 권한 확인 → 없으면 요청
 * 2. 권한 거부 시 [PermissionDeniedContent] 표시 (엣지 케이스 §11)
 * 3. Preview + ImageCapture 바인딩
 * 4. 촬영 → ImageProxy.rotationDegrees로 회전 보정 → 다운스케일 → [onImageCaptured]
 *
 * ## 성능 (§12)
 * - 촬영 후 [downscaleIfNeeded]로 긴 변을 2048px 이하로 축소해 OCR 부하 감소
 * - `previewUseCase`, `imageCaptureUseCase`를 `remember`로 생성해 재구성 시 재할당 방지
 */
@Composable
fun CameraCaptureScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── 권한 상태 ──────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 권한 거부 화면
    if (permissionDenied && !hasCameraPermission) {
        PermissionDeniedContent(onBack = onBack)
        return
    }

    // 권한 결과 대기 중
    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // ── CameraX ────────────────────────────────────────────
    val previewUseCase = remember { Preview.Builder().build() }
    val imageCaptureUseCase = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            imageCaptureUseCase,
        )
    }

    // ── UI ────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 카메라 프리뷰
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { view -> previewUseCase.setSurfaceProvider(view.surfaceProvider) },
            modifier = Modifier.fillMaxSize(),
        )

        // 뒤로 버튼
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text(
                text = stringResource(R.string.btn_back),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // 촬영 실패 알림
        captureError?.let { msg ->
            Card(
                modifier = Modifier


                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, top = 60.dp, end = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 촬영 버튼 (원형)
        Button(
            onClick = {
                if (isCapturing) return@Button
                isCapturing = true
                captureError = null

                imageCaptureUseCase.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val degrees = image.imageInfo.rotationDegrees
                            val raw = image.toBitmap()
                            image.close()
                            // 기기 방향 보정 후 다운스케일 (§12)
                            val corrected = if (degrees != 0) raw.rotate(degrees.toFloat()) else raw
                            val scaled = corrected.downscaleIfNeeded()
                            isCapturing = false
                            onImageCaptured(scaled)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            captureError = "${context.getString(R.string.capture_error_prefix)}: ${exception.message}"
                            isCapturing = false
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(72.dp),
            shape = CircleShape,
            enabled = !isCapturing,
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.btn_capture),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ── 권한 거부 화면 (§11 엣지 케이스) ──────────────────────

@Composable
private fun PermissionDeniedContent(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.camera_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text(stringResource(R.string.btn_go_back))
            }
        }
    }
}
