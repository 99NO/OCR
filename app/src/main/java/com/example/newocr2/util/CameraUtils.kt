package com.example.newocr2.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 시스템 카메라(TakePicture)에 넘길 임시 사진 저장 URI를 생성한다.
 *
 * FileProvider를 통해 content:// URI를 반환하므로 카메라 앱이 파일에 쓸 수 있다.
 * 사진은 앱 캐시 디렉터리의 images/ 폴더에 저장되며, 앱 제거 시 함께 삭제된다.
 */
fun createImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val imageFile = File(imagesDir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile,
    )
}
