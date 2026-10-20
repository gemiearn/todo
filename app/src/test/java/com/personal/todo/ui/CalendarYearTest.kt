package com.personal.todo.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 年视图加了卡片留白之后，画和点必须仍然落在同一格上。
 *
 * 这里按 411×790 的正文尺寸算：三列 × 四行、卡片间距 6、外边距 4、卡内边距 6、
 * 月份名高 15。十月在第三行第一列，2026-10-01 是周四，所以 10 月 20 日落在第 4 周
 * 第 2 列（从周一开始）。
 */
class CalendarYearTest {

    private val width = 411f
    private val height = 790f
    private val gap = 6f
    private val outer = 4f
    private val padding = 6f
    private val labelHeight = 15f

    private fun pick(x: Float, y: Float): LocalDate? = yearDateAt(
        x = x,
        y = y,
        width = width,
        height = height,
        gap = gap,
        outer = outer,
        padding = padding,
        labelHeight = labelHeight,
        year = 2026,
        weekStartsMonday = true
    )

    @Test
    fun aTapOnOctoberTwentyReturnsThatDay() {
        // 十月卡片：left=4, top=595；格宽 16.9048、格高 27.3333。
        // 10 月 20 日：第 4 周（0 基 3）、第 2 列（0 基 1）。
        val x = 4f + padding + 1 * 16.9048f + 16.9048f / 2f
        val y = 595f + padding + labelHeight + 3 * 27.3333f + 27.3333f / 2f

        assertEquals(LocalDate.of(2026, 10, 20), pick(x, y))
    }

    @Test
    fun theGapBetweenTwoMonthCardsIsNotADate() {
        // 十月与十一月之间的缝：x 已经越过十月的卡片右边缘。
        val x = 4f + (411f - 8f - 12f) / 3f + gap / 2f
        assertNull(pick(x, 711f))
    }

    @Test
    fun theMonthLabelRowIsNotADate() {
        assertNull(pick(x = 10f, y = 600f))
    }

    @Test
    fun tappingTheOuterMarginDoesNothing() {
        assertNull(pick(x = 1f, y = 400f))
        assertNull(pick(x = 410f, y = 400f))
    }
}
