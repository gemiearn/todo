package com.personal.todo.model

/**
 * 一次专注里「真正在跑」的那几小段，存成 `开始-结束` 的毫秒对、用 `|` 连起来的一串。
 *
 * 为什么要单独记它：会话记录里原来只有「开始时刻、结束时刻、实际专注秒数」，统计页只能拿
 * 整段跨度去摊，于是**暂停的空档也会落进那几小时**。记下小段之后，「最佳专注时间」只摊
 * 真正在跑的部分，暂停期间一分一秒都不落。
 *
 * 与 [FocusTagSnapshot] 一样，这一列是**可空**的快照：老记录没有它，读取方据此退回旧口径，
 * 历史数据不会被改写。坏数据一律丢掉，绝不猜。
 */
object FocusSegments {

    private const val Parts = '|'
    private const val Range = '-'

    /**
     * 把 `[from]` 到 `[to]` 这一段接到已有的小段串后面。
     *
     * 长度为 0 或负数（时钟倒退、同一毫秒）的段直接丢掉：它既说明不了什么，也会让统计
     * 除零。
     */
    fun append(existing: String?, from: Long, to: Long): String? {
        if (to <= from) return existing
        val one = "$from$Range$to"
        return if (existing.isNullOrBlank()) one else "$existing$Parts$one"
    }

    /** 解开成若干半开区间 `[start, end)`；认不出来的部分直接跳过。 */
    fun decode(stored: String?): List<LongRange> {
        if (stored.isNullOrBlank()) return emptyList()
        return stored.split(Parts).mapNotNull { part ->
            val dash = part.indexOf(Range)
            if (dash <= 0) return@mapNotNull null
            val from = part.substring(0, dash).toLongOrNull() ?: return@mapNotNull null
            val to = part.substring(dash + 1).toLongOrNull() ?: return@mapNotNull null
            if (to > from) from..to else null
        }
    }
}
