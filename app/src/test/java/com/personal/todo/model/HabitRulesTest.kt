package com.personal.todo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 习惯打勾表的两条纯规则：次数怎么夹、什么时候算完成。
 *
 * 它们决定页面上那个数字怎么变，所以钉在数据库外面测——加减一次是两个页面（行与格子）
 * 共用的同一份逻辑。
 */
class HabitRulesTest {

    @Test
    fun theCountIsClampedBetweenZeroAndTheTarget() {
        assertEquals(1, HabitRules.nextCount(current = 0, target = 5, delta = 1))
        assertEquals(5, HabitRules.nextCount(current = 5, target = 5, delta = 1))
        assertEquals("减到 0 就停住，不会变成负数", 0, HabitRules.nextCount(current = 0, target = 5, delta = -1))
        assertEquals(1, HabitRules.nextCount(current = 2, target = 5, delta = -1))

        assertEquals("次数 1 的习惯就是打勾", 1, HabitRules.nextCount(current = 0, target = 1, delta = 1))
        assertEquals(0, HabitRules.nextCount(current = 1, target = 1, delta = -1))
        assertEquals("目标被写成 0 也不会算错", 0, HabitRules.nextCount(current = 0, target = 0, delta = -1))
    }

    @Test
    fun completeMeansTheCountReachedTheTarget() {
        assertTrue(HabitRules.isComplete(count = 5, target = 5))
        assertTrue("超过目标也算完成", HabitRules.isComplete(count = 6, target = 5))
        assertFalse(HabitRules.isComplete(count = 4, target = 5))
        assertTrue("目标是 0 时按 1 次算", HabitRules.isComplete(count = 1, target = 0))
    }
}
