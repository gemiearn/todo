package com.personal.todo.model

/**
 * 习惯打勾表的三条纯规则，单独放这里是为了能脱离数据库直接测：
 * 「点了几下」怎么变、什么时候算这一天完成、显示出来的量是多少。
 *
 * 习惯的量分两层：库里存的是**点了几下**（`count`），显示与完成判定用的是**折算出来的量**
 * ＝点数 × 单次基数（`count * step`）。单位只是显示用的文字，不参与计算。
 */
object HabitRules {

    /** 每天至少一单位，「0」的习惯没有意义。 */
    const val MinTarget = 1

    /**
     * 够到目标最少要几下手：目标 60、单次 15 → 4 下；除不尽时向上取整（目标 50、单次 15
     * → 4 下，第四下已经够数了）。
     */
    fun maxCount(target: Int, step: Int): Int {
        val top = target.coerceAtLeast(MinTarget)
        val each = step.coerceAtLeast(1)
        return (top + each - 1) / each
    }

    /**
     * 打卡一次之后的点数，夹在 `0..可点上限` 之间。
     *
     * 到目标就不再往上加：打卡的意义是「今天够不够」，不是记流水账，所以喝第 6 杯水
     * 也不会把 5/5 变成 6/5。
     */
    fun nextCount(current: Int, target: Int, step: Int, delta: Int): Int =
        (current + delta).coerceIn(0, maxCount(target, step))

    /** 这一天算不算完成了：折算出来的量到了目标。 */
    fun isComplete(count: Int, target: Int, step: Int): Boolean =
        count * step.coerceAtLeast(1) >= target.coerceAtLeast(MinTarget)

    /** 显示出来的量：点数 × 单次基数，夹到目标为止（与「到目标就不再往上加」一致）。 */
    fun shownAmount(count: Int, target: Int, step: Int): Int {
        val amount = count * step.coerceAtLeast(1)
        return amount.coerceAtMost(target.coerceAtLeast(MinTarget))
    }
}
