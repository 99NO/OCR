package com.example.newocr2.ui.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.newocr2.ocr.LineScore

private val BOX_STROKE_DP = 2.dp
private val MARKER_RADIUS_DP = 4.5.dp

private val COLOR_NORMAL    = Color(0xFF2E7D32)   // 녹색 (+1)
private val COLOR_FLIPPED   = Color(0xFFC62828)   // 빨강 (-1)
private val COLOR_UNCERTAIN = Color(0xFF9E9E9E)   // 회색 ( 0)
private val COLOR_MARKER    = Color(0xFFE53935)   // 구두점 마커 (빨강 채움)

/**
 * ML Kit 인식 결과의 라인 박스와 구두점 마커를 이미지 위에 오버레이로 그린다.
 *
 * ## 좌표 변환
 * ML Kit이 반환하는 cornerPoints는 이미지 픽셀 좌표계다.
 * 이미지는 fillMaxWidth().aspectRatio(w/h)로 표시되므로
 * 스케일 팩터 = canvasWidth / imageWidth 를 x/y 모두에 적용한다.
 *
 * ## 그리기 내용
 * - 각 라인의 cornerPoints 4점을 잇는 사각형 (2dp stroke, 판정 색상)
 * - 베이스라인 구두점 위치에 작은 원 (반지름 4.5dp, 빨강 채움)
 *
 * @param lines      [LineScore] 목록 — cornerPoints, markX/Y, score 포함
 * @param imageWidth 원본 이미지 픽셀 너비 (스케일 계산용)
 */
@Composable
fun OverlayCanvas(
    lines: List<LineScore>,
    imageWidth: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val strokePx   = with(density) { BOX_STROKE_DP.toPx() }
    val markerPx   = with(density) { MARKER_RADIUS_DP.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (imageWidth == 0) return@Canvas
        val scale = size.width / imageWidth.toFloat()

        lines.forEach { line ->
            val boxColor = when (line.score) {
                1    -> COLOR_NORMAL
                -1   -> COLOR_FLIPPED
                else -> COLOR_UNCERTAIN
            }

            // ─── 라인 박스 (cornerPoints 4점 연결) ───
            if (line.lineCorners.size == 4) {
                drawLineBoundingBox(
                    corners = line.lineCorners.map { pt ->
                        Offset(pt.x * scale, pt.y * scale)
                    },
                    color = boxColor,
                    strokeWidth = strokePx,
                )
            }

            // ─── 구두점 마커 (채움 원) ───
            drawCircle(
                color = COLOR_MARKER,
                radius = markerPx,
                center = Offset(line.markX * scale, line.markY * scale),
            )
        }
    }
}

/** cornerPoints 4점을 순서대로 연결하는 사각형을 그린다. */
private fun DrawScope.drawLineBoundingBox(
    corners: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        lineTo(corners[1].x, corners[1].y)
        lineTo(corners[2].x, corners[2].y)
        lineTo(corners[3].x, corners[3].y)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}
