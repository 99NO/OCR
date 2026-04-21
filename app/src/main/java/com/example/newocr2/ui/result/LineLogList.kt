package com.example.newocr2.ui.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.newocr2.ocr.LineScore

private const val LINE_TEXT_MAX_CHARS = 60

private val COLOR_NORMAL    = Color(0xFF2E7D32)
private val COLOR_FLIPPED   = Color(0xFFC62828)
private val COLOR_UNCERTAIN = Color(0xFF757575)

/**
 * 라인별 베이스라인 구두점 평가 로그를 세로 리스트로 표시한다.
 *
 * 각 행 형식:
 * ```
 * [+1]  localY=0.81  '.'  "모든 과정은 TensorFlow Lite 런타임에서 실행되며,"
 * [-1]  localY=0.18  '.'  ".다니습있 어되화자양로8TNI 주로 은델모"
 * [ 0]  localY=0.47  ','  "애매한 라인 예시"
 * ```
 *
 * - 점수 색상: +1 녹색 / -1 빨강 / 0 회색
 * - localY: 소수 둘째 자리
 * - 라인 텍스트: 60자 초과 시 말줄임
 *
 * 외부 스크롤(ResultScreen의 verticalScroll) 안에 배치되므로
 * 이 컴포저블 자체에는 스크롤을 추가하지 않는다.
 */
@Composable
fun LineLogList(
    lines: List<LineScore>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "라인별 평가 로그",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            if (lines.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "베이스라인 구두점이 검출된 라인이 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = COLOR_UNCERTAIN,
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))

            lines.forEachIndexed { index, line ->
                LineLogRow(line = line)
                if (index < lines.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun LineLogRow(line: LineScore) {
    val color = when (line.score) {
        1    -> COLOR_NORMAL
        -1   -> COLOR_FLIPPED
        else -> COLOR_UNCERTAIN
    }
    val scoreTag = when (line.score) {
        1    -> "[+1]"
        -1   -> "[-1]"
        else -> "[ 0]"
    }
    val preview = if (line.lineText.length > LINE_TEXT_MAX_CHARS)
        line.lineText.take(LINE_TEXT_MAX_CHARS) + "…"
    else
        line.lineText

    Row(modifier = Modifier.fillMaxWidth()) {
        // 고정 너비 메타 정보 (점수·localY·구두점 문자)
        Text(
            text = "$scoreTag  localY=${"%.2f".format(line.localY)}  '${line.baselineChar}'  ",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
        // 라인 텍스트 (말줄임)
        Text(
            text = "\"$preview\"",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}
