package com.example.newocr2.ui.result

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.newocr2.R
import com.example.newocr2.ocr.Decision
import com.example.newocr2.ocr.LineScore
import com.example.newocr2.ocr.OrientationResult

/**
 * 결과 화면.
 *
 * ## 레이아웃 구성
 * (A) 이미지 + OverlayCanvas
 * (B) 판정 요약 카드
 * (C) 라인별 평가 로그 (LineLogList)
 * (D) 최종 인식 텍스트
 *
 * ## 엣지 케이스 처리 (§11)
 * - OCR 실패 → ErrorContent (재시도 버튼 포함)
 * - 텍스트 없음 → UNCERTAIN + 안내 메시지
 * - 기여 라인 부족 → UNCERTAIN + 기여 라인 수 표시
 *
 * ## 성능 최적화 (§12)
 * - [ImageSection]에서 `remember(bitmap)`으로 ImageBitmap 변환을 캐시해
 *   recomposition 시 매번 변환하지 않도록 한다.
 * - ViewModel `started` 플래그로 화면 재구성 시 OCR 재실행 방지.
 */
@Composable
fun ResultScreen(
    bitmap: Bitmap,
    vm: ResultViewModel = viewModel(),
) {
    LaunchedEffect(bitmap) { vm.process(bitmap) }

    val uiState by vm.uiState.collectAsState()

    when (val state = uiState) {
        is ResultUiState.Loading -> LoadingContent()
        is ResultUiState.Error   -> ErrorContent(message = state.message, onRetry = {
            vm.retry(bitmap)
        })
        is ResultUiState.Success -> SuccessContent(state)
    }
}

// ─────────────────────────────────────────────────────────
// Loading / Error
// ─────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "텍스트 인식 중…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * OCR 실패 화면 (§11 엣지 케이스).
 * 재시도 버튼으로 ViewModel을 리셋하고 다시 파이프라인을 실행할 수 있다.
 */
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "오류: $message",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("다시 시도")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Success
// ─────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(state: ResultUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // (A) 이미지 + OverlayCanvas
        ImageSection(
            bitmap = state.displayBitmap,
            lines = state.result.perLine,
        )

        Spacer(Modifier.height(12.dp))

        // (B) 판정 요약 카드
        VerdictCard(
            result = state.result,
            wasFlipped = state.wasFlipped,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        // (C) 라인별 평가 로그
        LineLogList(
            lines = state.result.perLine,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        // (D) 최종 인식 텍스트
        FinalTextSection(
            text = state.finalText,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────
// (A) 이미지 영역
// ─────────────────────────────────────────────────────────

@Composable
private fun ImageSection(bitmap: Bitmap, lines: List<LineScore>) {
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    // §12: bitmap → ImageBitmap 변환을 remember로 캐시 (recomposition 시 재변환 방지)
    val imageBitmap: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        OverlayCanvas(
            lines = lines,
            imageWidth = bitmap.width,
        )
    }
}

// ─────────────────────────────────────────────────────────
// (B) 판정 요약 카드
// ─────────────────────────────────────────────────────────

@Composable
private fun VerdictCard(
    result: OrientationResult,
    wasFlipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, labelColor) = when (result.decision) {
        Decision.NORMAL    -> stringResource(R.string.label_normal)    to Color(0xFF2E7D32)
        Decision.FLIPPED   -> stringResource(R.string.label_flipped)   to Color(0xFFC62828)
        Decision.UNCERTAIN -> stringResource(R.string.label_uncertain)  to Color(0xFF757575)
    }

    // §11: UNCERTAIN 세부 원인 안내
    val uncertainHint = when {
        result.decision != Decision.UNCERTAIN -> null
        result.totalLines == 0               -> stringResource(R.string.label_uncertain_no_text)
        else -> stringResource(R.string.label_uncertain_insufficient, result.contributingLines)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = labelColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "집계 점수: ${"%.2f".format(result.averageScore)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.label_contributing_lines,
                    result.contributingLines,
                    result.totalLines,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            uncertainHint?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                )
            }
            if (wasFlipped) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.info_rerun),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// (D) 최종 인식 텍스트
// ─────────────────────────────────────────────────────────

@Composable
private fun FinalTextSection(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_final_text),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text.ifBlank { "(인식된 텍스트 없음)" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
