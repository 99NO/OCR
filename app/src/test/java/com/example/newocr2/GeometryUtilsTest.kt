package com.example.newocr2

import android.graphics.Point
import com.example.newocr2.util.computeLocalY
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)

/**
 * computeLocalY 유닛 테스트.
 *
 * corners 순서: [TL, TR, BR, BL] (이미지 좌표계, Y 아래로 증가)
 */
class GeometryUtilsTest {

    private val delta = 0.01f

    // ──────────────────────────────────────────────
    // 정방형(축 정렬) 박스 테스트
    // ──────────────────────────────────────────────

    /** 상단 edge 중심 → localY ≈ 0.0 */
    @Test
    fun `정방형 박스 상단 중심은 localY 0`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, 0f, corners)
        assertEquals(0f, result, delta)
    }

    /** 하단 edge 중심 → localY ≈ 1.0 */
    @Test
    fun `정방형 박스 하단 중심은 localY 1`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, 100f, corners)
        assertEquals(1f, result, delta)
    }

    /** 박스 정중앙 → localY ≈ 0.5 */
    @Test
    fun `정방형 박스 중앙은 localY 0점5`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, 50f, corners)
        assertEquals(0.5f, result, delta)
    }

    /** 박스 하단 75% 지점 → localY ≈ 0.75 */
    @Test
    fun `정방형 박스 75퍼센트 지점은 localY 0점75`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, 75f, corners)
        assertEquals(0.75f, result, delta)
    }

    // ──────────────────────────────────────────────
    // 기울어진 박스 테스트 (45° 시계 방향)
    // ──────────────────────────────────────────────

    /**
     * 45° 기울어진 박스:
     *   TL=(0,50), TR=(50,0), BR=(100,50), BL=(50,100)
     * 상단 edge 중심 = (25, 25), 하단 edge 중심 = (75, 75)
     */
    @Test
    fun `기울어진 박스 상단 중심은 localY 0`() {
        val corners = tiltedCorners()
        // 상단 edge 중심 = (TL+TR)/2 = (25, 25)
        val result = computeLocalY(25f, 25f, corners)
        assertEquals(0f, result, delta)
    }

    @Test
    fun `기울어진 박스 하단 중심은 localY 1`() {
        val corners = tiltedCorners()
        // 하단 edge 중심 = (BR+BL)/2 = (75, 75)
        val result = computeLocalY(75f, 75f, corners)
        assertEquals(1f, result, delta)
    }

    @Test
    fun `기울어진 박스 중앙은 localY 0점5`() {
        val corners = tiltedCorners()
        // 중앙 = ((25+75)/2, (25+75)/2) = (50, 50)
        val result = computeLocalY(50f, 50f, corners)
        assertEquals(0.5f, result, delta)
    }

    // ──────────────────────────────────────────────
    // 엣지 케이스
    // ──────────────────────────────────────────────

    /** 박스 높이가 0인 경우(수직 축 길이 = 0) → 안전값 0.5 반환 */
    @Test
    fun `박스 높이 0인 경우 안전값 0점5 반환`() {
        val corners = squareCorners(left = 0, top = 10, right = 100, bottom = 10) // 높이 0
        val result = computeLocalY(50f, 10f, corners)
        assertEquals(0.5f, result, delta)
    }

    /** 박스 밖(상단 위) 점 → 0.0으로 clamp */
    @Test
    fun `박스 위 점은 0으로 clamp`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, -50f, corners)
        assertEquals(0f, result, delta)
    }

    /** 박스 밖(하단 아래) 점 → 1.0으로 clamp */
    @Test
    fun `박스 아래 점은 1로 clamp`() {
        val corners = squareCorners(left = 0, top = 0, right = 100, bottom = 100)
        val result = computeLocalY(50f, 200f, corners)
        assertEquals(1f, result, delta)
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    /** 축 정렬 직사각형 corners [TL, TR, BR, BL] */
    private fun squareCorners(left: Int, top: Int, right: Int, bottom: Int): List<Point> = listOf(
        Point(left, top),   // TL
        Point(right, top),  // TR
        Point(right, bottom), // BR
        Point(left, bottom),  // BL
    )

    /** 45° 기울어진 마름모형 박스 [TL, TR, BR, BL] */
    private fun tiltedCorners(): List<Point> = listOf(
        Point(0, 50),   // TL
        Point(50, 0),   // TR
        Point(100, 50), // BR
        Point(50, 100), // BL
    )
}
