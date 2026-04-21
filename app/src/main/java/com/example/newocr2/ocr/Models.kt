package com.example.newocr2.ocr

import android.graphics.Point

/** 문서 방향 판정 결과 */
enum class Decision {
    NORMAL,     // 정상 방향
    FLIPPED,    // 180° 뒤집힘
    UNCERTAIN,  // 판정 불가 (기여 라인 < 3 또는 점수 절댓값 ≤ 0.5)
}

/**
 * 단일 라인에서 추출한 베이스라인 구두점 평가 정보.
 *
 * @param lineText      라인 전체 텍스트
 * @param baselineChar  검출된 베이스라인 문자 (BASELINE_CHARS 중 하나)
 * @param localY        라인 박스 내 정규화된 Y 위치 (0 = 상단, 1 = 하단)
 * @param score         +1(정상), -1(뒤집힘), 0(판정 보류)
 * @param lineCorners   라인의 cornerPoints [TL, TR, BR, BL]
 * @param markX         구두점 마커 이미지 픽셀 X (오버레이용)
 * @param markY         구두점 마커 이미지 픽셀 Y (오버레이용)
 */
data class LineScore(
    val lineText: String,
    val baselineChar: Char,
    val localY: Float,
    val score: Int,
    val lineCorners: List<Point>,
    val markX: Float,
    val markY: Float,
)

/**
 * 문서 전체 방향 분석 결과.
 *
 * @param averageScore      기여 라인들의 점수 평균 (-1.0 ~ +1.0)
 * @param decision          최종 판정
 * @param contributingLines score != 0 인 라인 수
 * @param totalLines        전체 라인 수
 * @param perLine           라인별 평가 목록
 */
data class OrientationResult(
    val averageScore: Double,
    val decision: Decision,
    val contributingLines: Int,
    val totalLines: Int,
    val perLine: List<LineScore>,
)
