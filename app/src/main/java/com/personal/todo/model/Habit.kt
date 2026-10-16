package com.personal.todo.model

/**
 * 习惯打勾表的两条纯规则，单独放这里是为了能脱离数据库直接测：
 * 次数怎么加减、什么时候算这一天完成。
 */
object HabitRules {

    /**
     * 打卡一次之后的新次数，夹在 `0..目标` 之间。
     *
     * 到目标就不再往上加：打卡的意义是「今天够不够」，不是记流水账，所以喝第 6 杯水
     * 也不会把 5/5 变成 6/5。
     */
    fun nextCount(current: Int, target: Int, delta: Int): Int {
        val top = target.coerceAtLeast(MinTarget)
        return (current + delta).coerceIn(0, top)
    }

    /** 这一天算不算完成了：次数到了目标。 */
    fun isComplete(count: Int, target: Int): Boolean = count >= target.coerceAtLeast(MinTarget)

    /** 每天至少一次，「0 次」的习惯没有意义。 */
    const val MinTarget = 1
}
