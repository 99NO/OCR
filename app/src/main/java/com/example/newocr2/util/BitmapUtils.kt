package com.example.newocr2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

/** 긴 변 최대 픽셀 수. 이 이상이면 다운샘플한다. */
const val MAX_LONG_SIDE = 2048

/**
 * Uri로부터 Bitmap을 디코딩하고 긴 변이 [MAX_LONG_SIDE]를 넘으면 다운샘플한다.
 *
 * 스토리지 권한 없이 ContentResolver를 통해 읽으므로 PhotoPicker Uri에 적합하다.
 *
 * @return ARGB_8888 Bitmap. 실패 시 null.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    // 1단계: 원본 크기만 읽어 inSampleSize 계산
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    } ?: return null

    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (longSide > MAX_LONG_SIDE) {
        // 2의 거듭제곱으로 올림 (BitmapFactory 권장 방식)
        var s = 1
        while ((longSide / (s * 2)) > MAX_LONG_SIDE) s *= 2
        s
    } else {
        1
    }

    // 2단계: 실제 디코딩
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    }
}

/**
 * Bitmap을 주어진 각도만큼 회전한다.
 *
 * @param degrees 회전 각도 (시계 방향, 예: 180f)
 * @return 회전된 새 Bitmap
 */
fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * 긴 변이 [MAX_LONG_SIDE]를 초과하면 비율을 유지하며 다운스케일한다.
 *
 * 갤러리 이미지는 [uriToBitmap]에서 이미 처리되지만,
 * CameraX ImageCapture는 전체 해상도 Bitmap을 반환하므로 이 함수로 후처리한다.
 */
fun Bitmap.downscaleIfNeeded(): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= MAX_LONG_SIDE) return this
    val scale = MAX_LONG_SIDE.toFloat() / longSide
    val newW = (width * scale).toInt()
    val newH = (height * scale).toInt()
    return Bitmap.createScaledBitmap(this, newW, newH, true)
}
