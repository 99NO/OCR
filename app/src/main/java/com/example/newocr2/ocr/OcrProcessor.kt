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
 * KoreanTextRecognizerOptions는 한/영 혼합 문서를 모두 처리한다.
 * Latin 전용 모델은 사용하지 않는다 (ARCHITECTURE.md §6 참조).
 *
 * 앱 전체에서 단일 recognizer 인스턴스를 공유하며,
 * 앱이 종료될 때 close()를 호출해 네이티브 리소스를 해제한다.
 * 생명주기 관리는 Application.onTerminate() 또는 ProcessLifecycleOwner에서 담당.
 */
object OcrProcessor {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /**
     * Bitmap을 ML Kit에 전달해 텍스트를 인식한다.
     *
     * @param bitmap   분석할 이미지. 회전 메타데이터가 없는 상태여야 한다.
     *                 (이미 올바른 방향으로 픽셀이 배치된 Bitmap을 넘길 것)
     * @param rotation 이미지 회전값 (도 단위). 일반적으로 0.
     * @return         ML Kit Text 인식 결과
     * @throws         Exception ML Kit 처리 실패 시
     */
    suspend fun recognize(bitmap: Bitmap, rotation: Int = 0): Text =
        suspendCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, rotation)
            recognizer.process(inputImage)
                .addOnSuccessListener { text -> cont.resume(text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    /**
     * 네이티브 리소스 해제.
     * Application 종료 시 호출한다.
     */
    fun close() {
        recognizer.close()
    }
}
