package com.example.newocr2.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * ML Kit Korean TextRecognizer 싱글톤 래퍼.
 *
 * lazy 대신 var로 관리하는 이유:
 * 카메라 앱 복귀 등으로 Activity가 재생성될 때 onDestroy()가 호출되어 close()되면,
 * lazy는 재초기화하지 않으므로 이후 recognize() 호출 시 "detector is already closed" 오류 발생.
 * close() 후 null로 리셋해 다음 호출 시 재생성되도록 한다.
 */
object OcrProcessor {

    private var recognizer: TextRecognizer? = null

    private fun getOrCreate(): TextRecognizer {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        return recognizer!!
    }

    /**
     * Bitmap을 ML Kit에 전달해 텍스트를 인식한다.
     * Bitmap은 EXIF 회전이 이미 적용된 상태여야 한다 ([com.example.newocr2.util.uriToBitmap] 참조).
     */
    suspend fun recognize(bitmap: Bitmap, rotation: Int = 0): Text =
        suspendCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, rotation)
            getOrCreate().process(inputImage)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /** 네이티브 리소스 해제. 앱이 실제로 종료될 때만 호출한다. */
    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
