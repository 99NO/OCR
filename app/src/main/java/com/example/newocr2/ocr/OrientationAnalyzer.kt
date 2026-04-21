package com.example.newocr2.ocr

import com.example.newocr2.util.computeLocalY
import com.google.mlkit.vision.text.Text

/**
 * ML Kit Text Recognition 결과를 분석해 문서의 방향(정상/뒤집힘/불확실)을 판정한다.
 *
 * ## 알고리즘 요약
 * 1. 각 라인의 elements를 순회하며 BASELINE_CHARS로 끝나는 element를 찾는다.
 * 2. 해당 element의 "읽기 방향 끝" 중심 = (TR + BR) / 2 를 마커 위치로 사용한다.
 * 3. computeLocalY로 마커의 라인 내 Y 위치를 계산한다.
 * 4. localY > 0.67 → +1 (정상), < 0.33 → -1 (뒤집힘), 그 외 → 0 (보류)
 * 5. score != 0인 라인 점수의 평균으로 최종 판정.
 *
 * 임계값(0.33 / 0.67 / 0.5 / 기여 라인 최솟값 3)은 ARCHITECTURE.md §5.4 참조.
 */
object OrientationAnalyzer {

    /**
     * 베이스라인 구두점 집합.
     * 이 문자들은 한/영 공통으로 글자 하단(베이스라인)에 위치한다.
     */
    val BASELINE_CHARS = setOf('.', ',', '?', '!', ':', ';', '。')

    fun analyze(text: Text): OrientationResult {
        val perLine = mutableListOf<LineScore>()
        var scoreSum = 0
        var contributing = 0
        var totalLines = 0

        for (block in text.textBlocks) {
            for (line in block.lines) {
                totalLines++
                val linePts = line.cornerPoints?.toList() ?: continue

                for (el in line.elements) {
                    val last = el.text.lastOrNull() ?: continue
                    if (last !in BASELINE_CHARS) continue
                    val ep = el.cornerPoints?.toList() ?: continue

                    // element의 "읽기 방향 끝" edge 중심 = (TR + BR) / 2
                    // cornerPoints 순서: [TL, TR, BR, BL]
                    val markX = (ep[1].x + ep[2].x) / 2f
                    val markY = (ep[1].y + ep[2].y) / 2f

                    val ly = computeLocalY(markX, markY, linePts)

                    // 0.33~0.67 구간은 노이즈로 처리 (Latin descender 고려)
                    val score = when {
                        ly > 0.67f -> +1
                        ly < 0.33f -> -1
                        else -> 0
                    }

                    if (score != 0) {
                        scoreSum += score
                        contributing++
                    }

                    perLine.add(
                        LineScore(
                            lineText = line.text,
                            baselineChar = last,
                            localY = ly,
                            score = score,
                            lineCorners = linePts,
                            markX = markX,
                            markY = markY,
                        )
                    )
                }
            }
        }

        val avg = if (contributing > 0) scoreSum.toDouble() / contributing else 0.0

        val decision = when {
            contributing < 3 -> Decision.UNCERTAIN
            avg > 0.5        -> Decision.NORMAL
            avg < -0.5       -> Decision.FLIPPED
            else             -> Decision.UNCERTAIN
        }

        return OrientationResult(
            averageScore = avg,
            decision = decision,
            contributingLines = contributing,
            totalLines = totalLines,
            perLine = perLine,
        )
    }
}
