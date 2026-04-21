package com.example.newocr2.util

import android.graphics.Point

/**
 * 라인 박스 안에서 주어진 점의 "로컬 Y" 좌표를 계산한다.
 *
 * localY = 0.0 → 라인 상단 edge, 1.0 → 라인 하단 edge.
 * 기울어진 박스에서도 정확하게 동작하도록 축 벡터 투영 방식을 사용.
 *
 * corners 순서: [TL, TR, BR, BL] (이미지 픽셀 좌표계)
 *
 * 임계값(0.33 / 0.67)은 ARCHITECTURE.md §5.4 참조.
 */
fun computeLocalY(x: Float, y: Float, corners: List<Point>): Float {
    require(corners.size == 4) { "corners 크기는 4여야 합니다." }

    // 상단 edge 중심 (TL, TR 평균)
    val topMidX = (corners[0].x + corners[1].x) / 2f
    val topMidY = (corners[0].y + corners[1].y) / 2f

    // 하단 edge 중심 (BR, BL 평균)
    val botMidX = (corners[2].x + corners[3].x) / 2f
    val botMidY = (corners[2].y + corners[3].y) / 2f

    // 상단→하단 축 벡터
    val ax = botMidX - topMidX
    val ay = botMidY - topMidY

    // 상단 중심에서 점 P까지의 벡터
    val px = x - topMidX
    val py = y - topMidY

    val axisSq = ax * ax + ay * ay
    if (axisSq == 0f) return 0.5f  // 박스가 축 방향으로 크기가 없을 때 안전값 반환

    // 축에 투영 후 정규화
    return ((px * ax + py * ay) / axisSq).coerceIn(0f, 1f)
}
