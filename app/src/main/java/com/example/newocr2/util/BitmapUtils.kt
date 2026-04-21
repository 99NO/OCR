package com.example.newocr2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * 긴 변 최대 픽셀 수.
 * 2048 → 3072로 상향: 작은 글씨가 많은 문서에서 다운샘플 시 인식률이 떨어지는 문제 완화.
 */
const val MAX_LONG_SIDE = 3072

/**
 * Uri로부터 Bitmap을 디코딩한다.
 *
 * - 긴 변이 [MAX_LONG_SIDE]를 넘으면 2의 거듭제곱으로 다운샘플
 * - EXIF 회전 정보를 읽어 픽셀을 실제 방향으로 회전
 *
 * ※ BitmapFactory.Options.inJustDecodeBounds = true 이면 decodeStream은 항상 null을 반환한다.
 *   openInputStream null 체크는 반드시 use { } 체인 바깥에서 별도로 처리해야 한다.
 *
 * @return ARGB_8888 Bitmap. 스트림 열기 또는 디코딩 실패 시 null.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    val cr = context.contentResolver

    // 1단계: 원본 크기만 읽어 inSampleSize 계산
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val firstStream = cr.openInputStream(uri) ?: return null
    firstStream.use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (longSide > MAX_LONG_SIDE) {
        var s = 1
        while ((longSide / (s * 2)) > MAX_LONG_SIDE) s *= 2
        s
    } else 1

    // 2단계: 실제 디코딩
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val secondStream = cr.openInputStream(uri) ?: return null
    val bitmap = secondStream.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

    // 3단계: EXIF 회전 보정
    // 카메라 사진은 픽셀 데이터가 세로 방향 그대로지만 EXIF에 회전 값이 기록되어 있다.
    // BitmapFactory는 EXIF를 무시하므로 직접 회전해줘야 ML Kit에 올바른 방향으로 전달된다.
    val orientation = try {
        cr.openInputStream(uri)?.use { ExifInterface(it) }
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90  -> bitmap.rotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotate(270f)
        else                                 -> bitmap
    }
}

/** Bitmap을 주어진 각도(시계 방향)만큼 회전한다. */
fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/** 긴 변이 [MAX_LONG_SIDE]를 초과하면 비율 유지 다운스케일. */
fun Bitmap.downscaleIfNeeded(): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= MAX_LONG_SIDE) return this
    val scale = MAX_LONG_SIDE.toFloat() / longSide
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}
