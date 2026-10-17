package com.personal.todo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 习惯打勾表的纯规则：点数怎么夹、折算出来的量是多少、什么时候算完成。
 *
 * 它们决定页面上那两个数字怎么变，所以钉在数据库外面测——行上的「今天 45/60 分钟」与
 * 格子里的打勾状态都靠这几条。
 */
class HabitRulesTest {

    @Test
    fun theCountIsClampedBetweenZeroAndWhatItTakesToReachTheTarget() {
        assertEquals(1, HabitRules.nextCount(current = 0, target = 5, step = 1, delta = 1))
        assertEquals(5, HabitRules.nextCount(current = 5, target = 5, step = 1, delta = 1))
        assertEquals("减到 0 就停住，不会变成负数", 0, HabitRules.nextCount(current = 0, target = 5, step = 1, delta = -1))
        assertEquals(1, HabitRules.nextCount(current = 2, target = 5, step = 1, delta = -1))

        assertEquals("次数 1 的习惯就是打勾", 1, HabitRules.nextCount(current = 0, target = 1, step = 1, delta = 1))
        assertEquals(0, HabitRules.nextCount(current = 1, target = 1, step = 1, delta = -1))
        assertEquals("目标被写成 0 也不会算错", 0, HabitRules.nextCount(current = 0, target = 0, step = 1, delta = -1))
    }

    @Test
    fun aStepMakesFewerTapsCarryTheWholeTarget() {
        // 每天 60 分钟、每点一下 15 分钟 → 点 4 下够数，第 5 下不再往上加
        assertEquals(4, HabitRules.maxCount(target = 60, step = 15))
        assertEquals(4, HabitRules.nextCount(current = 3, target = 60, step = 15, delta = 1))
        assertEquals(4, HabitRules.nextCount(current = 4, target = 60, step = 15, delta = 1))
        assertEquals("除不尽时向上取整：50 ÷ 15 → 点 4 下就够", 4, HabitRules.maxCount(target = 50, step = 15))
        assertEquals("单次基数比目标还大，也是一下就够", 1, HabitRules.maxCount(target = 5, step = 30))
    }

    @Test
    fun theShownAmountIsCountTimesStepCappedAtTheTarget() {
        assertEquals(45, HabitRules.shownAmount(count = 3, target = 60, step = 15))
        assertEquals(60, HabitRules.shownAmount(count = 4, target = 60, step = 15))
        assertEquals("显示量夹到目标，不会出现 60/50 这种超过", 50, HabitRules.shownAmount(count = 4, target = 50, step = 15))
        assertEquals("单次 1 时显示量就是点数", 3, HabitRules.shownAmount(count = 3, target = 5, step = 1))
    }

    @Test
    fun completeMeansTheShownAmountReachedTheTarget() {
        assertTrue(HabitRules.isComplete(count = 5, target = 5, step = 1))
        assertTrue("超过目标也算完成", HabitRules.isComplete(count = 6, target = 5, step = 1))
        assertFalse(HabitRules.isComplete(count = 4, target = 5, step = 1))
        assertTrue("目标是 0 时按 1 次算", HabitRules.isComplete(count = 1, target = 0, step = 1))
        assertTrue("按单次基数折算：3 下 ×15＝45 已过 40", HabitRules.isComplete(count = 3, target = 40, step = 15))
        assertFalse(HabitRules.isComplete(count = 2, target = 40, step = 15))
    }
}
