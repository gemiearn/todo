package com.personal.todo.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重复规则的算次：形状、相位，以及存进 `tasks.repeatRule` 的那个紧凑写法。
 *
 * [anchor] 是 2026-10-15，星期五——相位和「每周几」都围着它算。注意
 * [RepeatRule.nextOnOrAfter] 含 [after] 当天：那天本来就符合规则，就还是它。
 */
class RepeatRuleTest {

    private val anchor = LocalDate.of(2026, 10, 15)

    @Test
    fun everyDayIsEveryDay() {
        val rule = RepeatRule(RepeatKind.DAILY)
        assertEquals(anchor, rule.nextOnOrAfter(anchor, anchor))
        assertEquals(anchor.plusDays(1), rule.nextOnOrAfter(anchor.plusDays(1), anchor))
        assertEquals("两周里含头含尾一共 15 次", 15, rule.occurrencesIn(anchor, anchor.plusDays(14), anchor).size)
    }

    @Test
    fun everyOtherDayKeepsThePhaseOfTheAnchor() {
        val rule = RepeatRule(RepeatKind.EVERY_OTHER_DAY)
        assertEquals(anchor, rule.nextOnOrAfter(anchor, anchor))
        assertEquals(anchor.plusDays(2), rule.nextOnOrAfter(anchor.plusDays(1), anchor))
        assertEquals(anchor.plusDays(2), rule.nextOnOrAfter(anchor.plusDays(2), anchor))
        assertEquals(anchor.plusDays(4), rule.nextOnOrAfter(anchor.plusDays(3), anchor))
        // 中间那几天没打开 app，相位也不会错位
        assertEquals(anchor.plusDays(10), rule.nextOnOrAfter(anchor.plusDays(9), anchor))
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val rule = RepeatRule(RepeatKind.WEEKDAYS)
        // 锚点当天就是周五，本来就算一天
        assertEquals(anchor, rule.nextOnOrAfter(anchor, anchor))
        // 周日往后找 → 周一：周六周日都跳过
        assertEquals(LocalDate.of(2026, 10, 19), rule.nextOnOrAfter(LocalDate.of(2026, 10, 18), anchor))
    }

    @Test
    fun weekendsOnlyLandOnSaturdayAndSunday() {
        val rule = RepeatRule(RepeatKind.WEEKENDS)
        val saturday = LocalDate.of(2026, 10, 17)
        assertEquals(saturday, rule.nextOnOrAfter(anchor, anchor))
        assertEquals("周六当天就是周六", saturday, rule.nextOnOrAfter(saturday, anchor))
        assertEquals(saturday.plusDays(1), rule.nextOnOrAfter(saturday.plusDays(1), anchor))
        assertEquals("周一往后找 → 下一个周六", saturday.plusDays(7), rule.nextOnOrAfter(saturday.plusDays(2), anchor))
    }

    @Test
    fun weeklyLandsOnTheChosenDay() {
        val rule = RepeatRule(RepeatKind.WEEKLY, dayOfWeek = DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 19), rule.nextOnOrAfter(anchor, anchor))
        assertEquals("周一当天就是周一", LocalDate.of(2026, 10, 19), rule.nextOnOrAfter(LocalDate.of(2026, 10, 19), anchor))
        assertEquals(LocalDate.of(2026, 10, 26), rule.nextOnOrAfter(LocalDate.of(2026, 10, 20), anchor))
    }

    @Test
    fun monthlyFallsBackToTheLastDayOfAShortMonth() {
        val rule = RepeatRule(RepeatKind.MONTHLY, dayOfMonth = 31)
        assertEquals(LocalDate.of(2026, 10, 31), rule.nextOnOrAfter(anchor, anchor))
        // 11 月没有 31 号，落到 11 月 30 日——不跳过那个月
        assertEquals(LocalDate.of(2026, 11, 30), rule.nextOnOrAfter(LocalDate.of(2026, 11, 1), anchor))
        // 12 月有 31 号，就回到 31 号
        assertEquals(LocalDate.of(2026, 12, 31), rule.nextOnOrAfter(LocalDate.of(2026, 12, 1), anchor))
    }

    @Test
    fun beforeTheAnchorNothingIsProduced() {
        val rule = RepeatRule(RepeatKind.DAILY)
        val earlier = anchor.minusDays(10)
        assertEquals("起点之前不补", anchor, rule.nextOnOrAfter(earlier, anchor))
        assertTrue(rule.occurrencesIn(earlier, anchor.minusDays(1), anchor).isEmpty())
    }

    @Test
    fun theStoredFormRoundTrips() {
        val rules = listOf(
            RepeatRule(RepeatKind.DAILY),
            RepeatRule(RepeatKind.EVERY_OTHER_DAY),
            RepeatRule(RepeatKind.WEEKDAYS),
            RepeatRule(RepeatKind.WEEKENDS),
            RepeatRule(RepeatKind.WEEKLY, dayOfWeek = DayOfWeek.WEDNESDAY),
            RepeatRule(RepeatKind.MONTHLY, dayOfMonth = 15)
        )
        rules.forEach { rule -> assertEquals(rule, RepeatRule.fromStored(rule.toStored())) }

        assertEquals("DAILY", RepeatRule(RepeatKind.DAILY).toStored())
        assertEquals("WEEKLY:3", RepeatRule(RepeatKind.WEEKLY, dayOfWeek = DayOfWeek.WEDNESDAY).toStored())
        assertEquals("MONTHLY:15", RepeatRule(RepeatKind.MONTHLY, dayOfMonth = 15).toStored())

        assertNull(RepeatRule.fromStored(null))
        assertNull(RepeatRule.fromStored(""))
        assertNull("认不出来的写法当没有规则", RepeatRule.fromStored("NONSENSE"))
    }
}
