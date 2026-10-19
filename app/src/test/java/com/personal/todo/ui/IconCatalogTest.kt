package com.personal.todo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图标目录的硬约束：300 个左右、按种类分组、不重复。 */
class IconCatalogTest {

    @Test
    fun thereAreAboutThreeHundredDistinctIconsGroupedByKind() {
        assertTrue("种类太少", IconCategory.entries.size >= 10)
        assertTrue(
            "图标数量应在 300 左右，实际 ${IconChoices.size}",
            IconChoices.size in 280..320
        )
        assertEquals("图标不能重复", IconChoices.size, IconChoices.toSet().size)

        IconCategory.entries.forEach { category ->
            assertTrue("${category.name} 的图标太少", category.icons.size >= 10)
            assertEquals(
                "${category.name} 内部不能重复",
                category.icons.size,
                category.icons.toSet().size
            )
            assertTrue("${category.name} 里有空白图标", category.icons.none { it.isBlank() })
        }
    }
}
