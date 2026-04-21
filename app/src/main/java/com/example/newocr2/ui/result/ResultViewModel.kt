package com.example.newocr2.ui.result

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newocr2.ocr.Decision
import com.example.newocr2.ocr.OcrProcessor
import com.example.newocr2.ocr.OrientationAnalyzer
import com.example.newocr2.ocr.OrientationResult
import com.example.newocr2.util.rotate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ResultScreen이 관찰하는 UI 상태 */
sealed interface ResultUiState {
    /** OCR 처리 중 */
    data object Loading : ResultUiState

    /** OCR 완료 */
    data class Success(
        val displayBitmap: Bitmap,   // 실제 표시할 이미지 (뒤집힘 시 회전된 bitmap)
        val result: OrientationResult,
        val wasFlipped: Boolean,     // 원본이 뒤집혀서 재처리했는지 여부
        val finalText: String,
    ) : ResultUiState

    /** OCR 실패 */
    data class Error(val message: String) : ResultUiState
}

/**
 * ResultScreen의 OCR 파이프라인 상태를 관리한다.
 *
 * ## 파이프라인
 * 1. OCR 1차 실행
 * 2. analyze() → FLIPPED이면 bitmap 180° 회전 후 OCR 2차 실행
 * 3. 최종 결과를 [uiState]로 방출
 *
 * process()는 최초 한 번만 호출되어야 하며, ViewModel이 살아있는 동안
 * 결과가 유지된다 (화면 회전 등 재구성 시 재실행 없음).
 */
class ResultViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    /** 이미 처리 시작했는지 추적 — 중복 실행 방지 (§12: 화면 재구성 시 재실행 없음) */
    private var started = false

    fun process(bitmap: Bitmap) {
        if (started) return
        started = true
        runPipeline(bitmap)
    }

    /** 오류 후 재시도 (§11: ErrorContent에서 호출) */
    fun retry(bitmap: Bitmap) {
        started = false
        process(bitmap)
    }

    private fun runPipeline(bitmap: Bitmap) {

        viewModelScope.launch {
            _uiState.value = ResultUiState.Loading
            runCatching {
                // §11: bitmap이 비어있거나 극저조도 → ML Kit이 텍스트 없음으로 반환 → UNCERTAIN 처리됨
                // OCR 1차
                val text1 = OcrProcessor.recognize(bitmap)
                val result1 = OrientationAnalyzer.analyze(text1)

                if (result1.decision == Decision.FLIPPED) {
                    // 뒤집힘 감지 → 180° 회전 후 재인식
                    val bitmap2 = withContext(Dispatchers.Default) { bitmap.rotate(180f) }
                    val text2 = OcrProcessor.recognize(bitmap2)
                    val result2 = OrientationAnalyzer.analyze(text2)
                    ResultUiState.Success(
                        displayBitmap = bitmap2,
                        result = result2,
                        wasFlipped = true,
                        finalText = text2.text,
                    )
                } else {
                    ResultUiState.Success(
                        displayBitmap = bitmap,
                        result = result1,
                        wasFlipped = false,
                        finalText = text1.text,
                    )
                }
            }.fold(
                onSuccess = { _uiState.value = it },
                onFailure = { _uiState.value = ResultUiState.Error(it.message ?: "알 수 없는 오류") },
            )
        }
    }
}
