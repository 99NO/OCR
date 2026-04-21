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
 * @return ARGB_8888 Bitmap. 스트림 열기 실패 또는 디코딩 실패 시 null.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    val cr = context.contentResolver

    // 1단계: 원본 크기만 읽어 inSampleSize 계산
    // ※ inJustDecodeBounds=true 이면 decodeStream은 항상 null을 반환한다.
    //   openInputStream 성공 여부는 반드시 별도로 null 체크해야 한다.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val firstStream = cr.openInputStream(uri) ?: return null
    firstStream.use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (longSide > MAX_LONG_SIDE) {
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
    val secondStream = cr.openInputStream(uri) ?: return null
    return secondStream.use { BitmapFactory.decodeStream(it, null, opts) }
}

/**
 * Bitmap을 주어진 각도만큼 회전한다.
 */
fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * 긴 변이 [MAX_LONG_SIDE]를 초과하면 비율을 유지하며 다운스케일한다.
 */
fun Bitmap.downscaleIfNeeded(): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= MAX_LONG_SIDE) return this
    val scale = MAX_LONG_SIDE.toFloat() / longSide
    val newW = (width * scale).toInt()
    val newH = (height * scale).toInt()
    return Bitmap.createScaledBitmap(this, newW, newH, true)
}
