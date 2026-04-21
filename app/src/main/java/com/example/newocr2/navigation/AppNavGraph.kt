package com.example.newocr2.navigation

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.newocr2.R
import com.example.newocr2.gallery.rememberPhotoPicker
import com.example.newocr2.ui.main.MainScreen
import com.example.newocr2.ui.result.ResultScreen
import com.example.newocr2.util.createImageUri
import com.example.newocr2.util.uriToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 앱 내 화면 경로 */
object Routes {
    const val MAIN   = "main"
    const val RESULT = "result"
}

/**
 * 앱 전체 Navigation 그래프.
 *
 * ## 화면 전환 흐름
 * ```
 * MAIN ──[카메라 버튼]──▶ 시스템 카메라 앱 ──[촬영 완료]──▶ RESULT
 *      ──[갤러리 버튼]──▶ PhotoPicker ──────────────────▶ RESULT
 * ```
 *
 * 시스템 카메라는 ActivityResultContracts.TakePicture()로 실행하며,
 * 결과 URI를 [uriToBitmap]으로 변환해 [BitmapHolder]에 저장한 뒤 RESULT로 이동한다.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 시스템 카메라가 사진을 저장할 URI (TakePicture 실행 직전에 생성)
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 시스템 기본 카메라 런처
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val uri = pendingPhotoUri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
            if (bitmap == null) {
                Toast.makeText(context, context.getString(R.string.error_image_load_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            BitmapHolder.bitmap = bitmap
            navController.navigate(Routes.RESULT)
        }
    }

    // 갤러리 (PhotoPicker)
    val openGallery = rememberPhotoPicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
            if (bitmap == null) {
                Toast.makeText(context, context.getString(R.string.error_image_load_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            BitmapHolder.bitmap = bitmap
            navController.navigate(Routes.RESULT)
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScreen(
                onCameraClick = {
                    val uri = createImageUri(context)
                    pendingPhotoUri = uri
                    cameraLauncher.launch(uri)
                },
                onGalleryClick = openGallery,
            )
        }

        composable(Routes.RESULT) {
            val bitmap = BitmapHolder.bitmap
            if (bitmap != null) {
                ResultScreen(bitmap = bitmap)
            } else {
                navController.popBackStack()
            }
        }
    }
}

/**
 * 화면 간 Bitmap 전달을 위한 인메모리 홀더.
 */
object BitmapHolder {
    var bitmap: Bitmap? = null
}
