package com.example.newocr2.navigation

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.newocr2.R
import com.example.newocr2.camera.CameraCaptureScreen
import com.example.newocr2.gallery.rememberPhotoPicker
import com.example.newocr2.ui.main.MainScreen
import com.example.newocr2.ui.result.ResultScreen
import com.example.newocr2.util.uriToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 앱 내 화면 경로 */
object Routes {
    const val MAIN   = "main"
    const val CAMERA = "camera"
    const val RESULT = "result"
}

/**
 * 앱 전체 Navigation 그래프.
 *
 * ## 화면 전환 흐름
 * ```
 * MAIN ──[카메라 버튼]──▶ CAMERA ──[촬영 완료]──▶ RESULT
 *      ──[갤러리 버튼]──▶ (PhotoPicker) ──────────▶ RESULT
 * ```
 *
 * ## Bitmap 전달 방식
 * [BitmapHolder]를 통해 메모리에서 전달한다.
 * NavArgs로 직렬화하면 Binder 트랜잭션 한도(1 MB)를 초과할 수 있으므로 사용하지 않는다.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // PhotoPicker — 이미지 선택 후 IO에서 Bitmap 변환 → ResultScreen 이동
    val openGallery = rememberPhotoPicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
            if (bitmap == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_image_load_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            BitmapHolder.bitmap = bitmap
            navController.navigate(Routes.RESULT)
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScreen(
                onCameraClick = { navController.navigate(Routes.CAMERA) },
                onGalleryClick = openGallery,
            )
        }

        composable(Routes.CAMERA) {
            CameraCaptureScreen(
                onImageCaptured = { bitmap ->
                    BitmapHolder.bitmap = bitmap
                    // 카메라 화면을 백스택에서 제거하고 결과 화면으로 이동
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.RESULT) {
            val bitmap = BitmapHolder.bitmap
            if (bitmap != null) {
                ResultScreen(bitmap = bitmap)
            } else {
                // BitmapHolder가 비어 있으면 메인으로 복귀
                navController.popBackStack()
            }
        }
    }
}

/**
 * 화면 간 Bitmap 전달을 위한 인메모리 홀더.
 *
 * 단순 데모 규모에서는 object 홀더로 충분.
 * 실제 앱이라면 공유 ViewModel을 권장.
 */
object BitmapHolder {
    var bitmap: Bitmap? = null
}
