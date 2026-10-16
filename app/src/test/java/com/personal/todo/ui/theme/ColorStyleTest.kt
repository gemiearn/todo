package com.personal.todo.ui.theme

import com.personal.todo.data.ColorStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 色系设置真的换了颜色 —— 这是它在数据层面的答案。
 *
 * 用户问过「切换色系真的有用吗」：界面上的差别只有在四象限才看得出来，所以这里把
 * 三套色系的四个象限色钉住——三套互不相同（切了一定有变化），每套里四个象限也互不
 * 相同（不会两个象限长得一样）。
 */
class ColorStyleTest {

    @Test
    fun everyColorStyleHasItsOwnQuadrantColours() {
        val palettes = ColorStyle.entries.map { it.quadrantColors() }

        assertEquals("三套色系必须互不相同", ColorStyle.entries.size, palettes.toSet().size)
    }

    @Test
    fun everyStyleKeepsItsFourQuadrantsDistinct() {
        ColorStyle.entries.forEach { style ->
            val palette = style.quadrantColors()
            val four = listOf(
                palette.importantUrgent,
                palette.importantNotUrgent,
                palette.notImportantUrgent,
                palette.notImportantNotUrgent
            )

            assertEquals("$style 这一套里四个象限色应该互不相同", 4, four.toSet().size)
        }
    }
}
