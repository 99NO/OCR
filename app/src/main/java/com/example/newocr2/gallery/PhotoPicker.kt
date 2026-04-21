package com.example.newocr2.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android 13+ PhotoPicker를 Compose에서 사용하기 위한 래퍼.
 *
 * 저장소 권한 없이 미디어를 선택할 수 있다.
 * 구버전(API < 33)에서는 ActivityResultContracts.PickVisualMedia가
 * 자동으로 기존 파일 선택 방식으로 폴백한다.
 *
 * @param onImagePicked 이미지 Uri 선택 완료 콜백. 취소 시 호출되지 않는다.
 * @return launch 함수 — 호출하면 PhotoPicker를 연다.
 */
@Composable
fun rememberPhotoPicker(onImagePicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = PickVisualMedia(),
        onResult = { uri -> uri?.let { onImagePicked(it) } },
    )
    return remember {
        { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    }
}
