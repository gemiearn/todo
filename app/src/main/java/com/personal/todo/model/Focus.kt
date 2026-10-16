package com.personal.todo.model

import com.personal.todo.data.db.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The three ways a session counts time (the modes the app offers):
 * 正向计时 counts up until the user ends it, 倒计时 counts down from a chosen
 * length, 番茄钟 is the classic 25 minute focus block.
 */
enum class FocusMode(val timed: Boolean) {
    STOPWATCH(false),
    COUNTDOWN(true),
    POMODORO(true),

    /** The four focus blocks after which a longer break is suggested. */
    ;

    companion object {
        /** 番茄钟 focus blocks in one cycle (spec section 5.1). */
        const val POMODORO_PER_CYCLE = 4
    }
}

/** How a finished session ended: it reached its end, or the user stopped it early. */
enum class FocusStatus { COMPLETED, INTERRUPTED }

/**
 * What the single active timer is counting right now: a focus block, or the break
 * that follows one (说明文档 5.1: 每轮专注之后有短休息，每几个番茄之后是长休息).
 *
 * A break is never written into the history: 休息时间不计入专注统计, so only a
 * [FOCUS] phase ends with a session row.
 */
enum class FocusPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK;

    val isBreak: Boolean get() = this != FOCUS
}

/**
 * The state of the single active session, as it is persisted between app runs
 * (spec section 5.2).
 *
 * The elapsed time of the current run is measured with a monotonic clock
 * ([lastResumeAtElapsed]); [accumulatedFocusSeconds] is what earlier runs of the
 * same session already banked. Pausing moves the current run into the bank and
 * stops the clock, so paused time is never counted.
 */
data class TimerState(
    val mode: FocusMode,
    val plannedSeconds: Int,
    val startedAt: Long,
    val lastResumeAtElapsed: Long,
    val accumulatedFocusSeconds: Int,
    val running: Boolean
)

/**
 * The timer arithmetic of spec section 5.2, kept free of Android types so it can
 * be tested directly.
 *
 * A negative span means the monotonic clock restarted (the device rebooted)
 * between two readings: that span cannot be trusted and is therefore not
 * counted, instead of producing a bogus negative or huge focus time.
 */
object FocusTiming {

    /** Focus seconds of the session so far. Paused time never counts. */
    fun focusSeconds(state: TimerState, nowElapsedMillis: Long): Int {
        val banked = if (state.accumulatedFocusSeconds > 0) state.accumulatedFocusSeconds else 0
        if (!state.running) return banked
        val sinceResume = nowElapsedMillis - state.lastResumeAtElapsed
        val running = if (sinceResume > 0) (sinceResume / 1000).toInt() else 0
        return banked + running
    }

    /** Seconds left for a timed mode, or null for 正向计时 which has no target. */
    fun remainingSeconds(state: TimerState, nowElapsedMillis: Long): Int? {
        if (!state.mode.timed) return null
        return (state.plannedSeconds - focusSeconds(state, nowElapsedMillis)).coerceAtLeast(0)
    }

    /** True once a timed session has counted down to zero. */
    fun reachedTarget(state: TimerState, nowElapsedMillis: Long): Boolean =
        state.mode.timed && focusSeconds(state, nowElapsedMillis) >= state.plannedSeconds

    /**
     * The focus seconds a session records when it ends. A timed session never
     * records more than its target: the extra time is not verified focus.
     */
    fun recordedFocusSeconds(state: TimerState, nowElapsedMillis: Long): Int {
        val focus = focusSeconds(state, nowElapsedMillis)
        return if (state.mode.timed) minOf(focus, state.plannedSeconds) else focus
    }
}

/** 每日 / 每周 focus totals, always derived from the sessions (spec section 5.3). */
data class FocusStats(
    val todayFocusSeconds: Int,
    val todayPomodoroCount: Int,
    val weekFocusSeconds: Int
)

/**
 * Sums the finished sessions of spec section 5.3.
 *
 * An interrupted session counts its real focus seconds towards the totals but
 * never towards the 番茄 count, and nothing is stored as a second, separately
 * maintained total that could drift away from the sessions.
 */
fun focusStatsOf(sessions: List<FocusSession>, today: LocalDate, zone: ZoneId): FocusStats {
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    var todaySeconds = 0
    var todayPomodoros = 0
    var weekSeconds = 0

    sessions.forEach { session ->
        val day = localDateOf(session.startedAt, zone)
        if (day == today) {
            todaySeconds += session.actualFocusSeconds
            if (session.mode == FocusMode.POMODORO && session.status == FocusStatus.COMPLETED) {
                todayPomodoros++
            }
        }
        if (!day.isBefore(weekStart) && !day.isAfter(today)) {
            weekSeconds += session.actualFocusSeconds
        }
    }

    return FocusStats(
        todayFocusSeconds = todaySeconds,
        todayPomodoroCount = todayPomodoros,
        weekFocusSeconds = weekSeconds
    )
}

/** The local day an absolute instant belongs to, in the device's time zone. */
fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** 某一天的专注量，年度热力图的格子用它。 */
data class FocusDay(val date: LocalDate, val seconds: Int, val pomodoros: Int)

// ----------------------------------------------------------- 统计页的聚合
//
// 每一个数字都从 focus_sessions 现算。休息是计时器的另一个阶段、从不写成 session，
// 所以它天然不会出现在下面任何一个结果里——这不是漏算，是口径本来就没有它。

/** 专注详情那张卡的周期粒度：天 / 周 / 月。 */
enum class FocusPeriodUnit { DAY, WEEK, MONTH }

/** 专注趋势那张卡的粒度：周 / 月 / 年。 */
enum class FocusTrendUnit { WEEK, MONTH, YEAR }

/** 排行的口径：按任务，或者按任务当时所在的清单。 */
enum class FocusRankBy { TASK, LIST }

/**
 * 一段专注身上带的标签快照：开始时绑定的任务当时有的标签名。
 *
 * 存成一个用 Unit Separator（U+001F，正常标签名里不会出现的控制字符）连起来的字符串，
 * 而不是另开一张连线表——统计页只需要「这段专注当时带着哪些标签」这一个事实，名字连
 * 起来就够了，也天然跟着 session 一起写成快照：标签以后改名或删掉，都不会改写历史。
 *
 * `null` / 空串都表示「没有标签」：任务当时就没打标签，或者这段记录早于这个列存在。
 */
object FocusTagSnapshot {

    private const val Separator = "\u001F"

    /** 把标签名合成快照；没有有效名字时返回 null，不写一个空字符串。 */
    fun encode(names: List<String>): String? {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (cleaned.isEmpty()) null else cleaned.joinToString(Separator)
    }

    /** 快照里的标签名，按写进去的顺序。 */
    fun names(snapshot: String?): List<String> =
        snapshot?.split(Separator)?.filter { it.isNotEmpty() } ?: emptyList()

    /** 这条快照里有没有 [name] 这个标签；统计页按标签筛整页记录时用它。 */
    fun matches(snapshot: String?, name: String): Boolean = name in names(snapshot)
}

/** 一段可以左右步进的日期区间，含首尾。 */
data class FocusPeriod(val start: LocalDate, val end: LocalDate) {

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** 区间里的天数，至少 1。 */
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

/** [anchor] 所在的周期区间：周从周一开始，月是自然月。 */
fun focusPeriodOf(unit: FocusPeriodUnit, anchor: LocalDate): FocusPeriod = when (unit) {
    FocusPeriodUnit.DAY -> FocusPeriod(anchor, anchor)

    FocusPeriodUnit.WEEK -> {
        val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        FocusPeriod(start, start.plusDays((DaysPerWeek - 1).toLong()))
    }

    FocusPeriodUnit.MONTH -> FocusPeriod(
        start = anchor.withDayOfMonth(1),
        end = anchor.withDayOfMonth(anchor.lengthOfMonth())
    )
}

/** 往前后挪一个周期：[step] 是 -1 或 +1。 */
fun FocusPeriod.shift(unit: FocusPeriodUnit, step: Long): FocusPeriod = when (unit) {
    FocusPeriodUnit.DAY -> FocusPeriod(start.plusDays(step), end.plusDays(step))
    FocusPeriodUnit.WEEK -> FocusPeriod(start.plusWeeks(step), end.plusWeeks(step))
    FocusPeriodUnit.MONTH -> FocusPeriod(start.plusMonths(step), end.plusMonths(step))
}

/** 一个周期的专注汇总，甜甜圈图与顶部牌子用它。 */
data class FocusPeriodTotals(
    val completedSeconds: Int,
    val interruptedSeconds: Int,
    val pomodoros: Int
) {
    val seconds: Int get() = completedSeconds + interruptedSeconds

    /**
     * 专注达成率 0..1。中断的专注也是真实的专注时长，只是没达成目标，所以它进
     * 分母不进分子；一次都没专注时是 0，页面上显示成「暂无数据」。
     */
    val completionRate: Float
        get() = if (seconds <= 0) 0f else completedSeconds.toFloat() / seconds
}

/** [period] 这个区间的汇总。 */
fun focusPeriodTotals(
    sessions: List<FocusSession>,
    period: FocusPeriod,
    zone: ZoneId
): FocusPeriodTotals = totalsOf(sessions.filter { period.contains(localDateOf(it.startedAt, zone)) })

/** 全部历史的汇总，「累计」那张牌子用它。 */
fun focusAllTimeTotals(sessions: List<FocusSession>): FocusPeriodTotals = totalsOf(sessions)

/** 趋势图上的一根柱。[date] 是它代表的那一天；年视图里是该月的 1 号。 */
data class FocusTrendBar(val date: LocalDate, val seconds: Int)

/** [unit] 这一个周期里每根柱子代表的时间跨度，横轴从左到右。 */
fun focusTrendBars(
    sessions: List<FocusSession>,
    unit: FocusTrendUnit,
    anchor: LocalDate,
    zone: ZoneId
): List<FocusTrendBar> {
    val byDay = secondsByDay(sessions, zone)

    if (unit == FocusTrendUnit.YEAR) {
        return (1..MonthsPerYear).map { month ->
            val first = LocalDate.of(anchor.year, month, 1)
            var seconds = 0
            var day = first
            while (day.monthValue == month) {
                seconds += byDay[day] ?: 0
                day = day.plusDays(1)
            }
            FocusTrendBar(first, seconds)
        }
    }

    val period = focusPeriodOf(
        if (unit == FocusTrendUnit.WEEK) FocusPeriodUnit.WEEK else FocusPeriodUnit.MONTH,
        anchor
    )
    val bars = mutableListOf<FocusTrendBar>()
    var day = period.start
    while (!day.isAfter(period.end)) {
        bars += FocusTrendBar(day, byDay[day] ?: 0)
        day = day.plusDays(1)
    }
    return bars
}

/** 「每日平均」：周期里的总时长摊到每一天。 */
fun focusDailyAverage(totals: FocusPeriodTotals, period: FocusPeriod): Int =
    if (period.days <= 0) 0 else totals.seconds / period.days

/**
 * 排行里的一行。[label] 为空表示那条记录本来就没有关联任务（或者没有清单快照）。
 */
data class FocusRankEntry(val label: String?, val seconds: Int, val sessions: Int)

/** [period] 里的专注排行，按 [by] 归并，时长多的排前面，最多留 [limit] 条。 */
fun focusRanking(
    sessions: List<FocusSession>,
    period: FocusPeriod,
    zone: ZoneId,
    by: FocusRankBy,
    limit: Int = ReportTopEntries
): List<FocusRankEntry> {
    val entries = linkedMapOf<String, FocusRankEntry>()

    sessions.forEach { session ->
        if (!period.contains(localDateOf(session.startedAt, zone))) return@forEach

        // 同一个任务可能改过名，所以按 id 归并，名字取第一次见到的那一个。
        val key: String
        val label: String?
        when (by) {
            FocusRankBy.TASK -> {
                key = session.taskId?.let { "id:$it" } ?: "title:${session.taskTitleSnapshot.orEmpty()}"
                label = session.taskTitleSnapshot
            }

            FocusRankBy.LIST -> {
                key = "list:${session.listNameSnapshot.orEmpty()}"
                label = session.listNameSnapshot
            }
        }

        val seen = entries[key]
        entries[key] = FocusRankEntry(
            label = seen?.label ?: label,
            seconds = (seen?.seconds ?: 0) + session.actualFocusSeconds,
            sessions = (seen?.sessions ?: 0) + 1
        )
    }

    return entries.values.sortedByDescending { it.seconds }.take(limit)
}

/**
 * 时间线上的一段专注：分钟数从当天 0 点算起。跨天的 session 会被拆成两天各一段，
 * 这样每一列画的都是那一天自己的时间。
 */
data class FocusSpan(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val interrupted: Boolean
)

/** [period] 里每一段专注的时钟跨度，按开始时间排。 */
fun focusSpans(
    sessions: List<FocusSession>,
    period: FocusPeriod,
    zone: ZoneId
): List<FocusSpan> {
    val spans = mutableListOf<FocusSpan>()

    sessions.forEach { session ->
        if (session.endedAt <= session.startedAt) return@forEach

        var day = localDateOf(session.startedAt, zone)
        val lastDay = localDateOf(session.endedAt, zone)
        while (!day.isAfter(lastDay)) {
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val from = maxOf(session.startedAt, dayStart)
            val to = minOf(session.endedAt, dayEnd)
            if (to > from && period.contains(day)) {
                val startMinute = ((from - dayStart) / MillisPerMinute).toInt().coerceIn(0, MinutesPerDay - 1)
                val endMinute = ((to - dayStart + MillisPerMinute - 1) / MillisPerMinute).toInt()
                    .coerceIn(startMinute + 1, MinutesPerDay)
                spans += FocusSpan(
                    date = day,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    interrupted = session.status == FocusStatus.INTERRUPTED
                )
            }
            day = day.plusDays(1)
        }
    }

    return spans.sortedWith(compareBy({ it.date }, { it.startMinute }))
}

/** 0..23 点各专注了多少秒，最佳专注时间那张卡用它。 */
data class FocusHourBar(val hour: Int, val seconds: Int)

/**
 * 把每一段专注按它在时钟上占的比例摊进 24 个小时里：一段 09:00-11:00 的专注，
 * 09 点和 10 点各拿一半。这样长时间的一段也不会整个压在开始的那个小时上。
 */
fun focusHourBars(
    sessions: List<FocusSession>,
    period: FocusPeriod,
    zone: ZoneId
): List<FocusHourBar> {
    val byHour = IntArray(HoursPerDay)

    sessions.forEach { session ->
        if (!period.contains(localDateOf(session.startedAt, zone))) return@forEach
        val span = session.endedAt - session.startedAt
        if (span <= 0) return@forEach

        var cursor = session.startedAt
        while (cursor < session.endedAt) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            if (nextHour <= cursor) break
            val sliceEnd = minOf(nextHour, session.endedAt)
            byHour[zoned.hour] += (session.actualFocusSeconds.toLong() * (sliceEnd - cursor) / span).toInt()
            cursor = sliceEnd
        }
    }

    return (0 until HoursPerDay).map { FocusHourBar(hour = it, seconds = byHour[it]) }
}

/**
 * 整年的打卡格子：53 个周列（周一开始）× 7 天。
 *
 * 年初 / 年末不满一周的那几天也各占一个格子，只是不属于这一年，页面上画成空的，
 * 这样每一周都对齐在同一列里。
 */
fun focusYearWeeks(
    sessions: List<FocusSession>,
    year: Int,
    zone: ZoneId
): List<List<FocusDay>> {
    val byDay = secondsByDay(sessions, zone)
    val pomodorosByDay = mutableMapOf<LocalDate, Int>()
    sessions.forEach { session ->
        if (session.mode == FocusMode.POMODORO && session.status == FocusStatus.COMPLETED) {
            val day = localDateOf(session.startedAt, zone)
            pomodorosByDay[day] = (pomodorosByDay[day] ?: 0) + 1
        }
    }

    val first = LocalDate.of(year, 1, 1)
    val last = LocalDate.of(year, 12, 31)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val end = last.plusDays((DaysPerWeek - last.dayOfWeek.value).toLong())

    val cells = mutableListOf<FocusDay>()
    var day = start
    while (!day.isAfter(end)) {
        val inYear = day.year == year
        cells += FocusDay(
            date = day,
            seconds = if (inYear) byDay[day] ?: 0 else 0,
            pomodoros = if (inYear) pomodorosByDay[day] ?: 0 else 0
        )
        day = day.plusDays(1)
    }
    return cells.chunked(DaysPerWeek)
}

private fun totalsOf(sessions: List<FocusSession>): FocusPeriodTotals {
    var completed = 0
    var interrupted = 0
    var pomodoros = 0
    sessions.forEach { session ->
        if (session.status == FocusStatus.COMPLETED) {
            completed += session.actualFocusSeconds
            if (session.mode == FocusMode.POMODORO) pomodoros++
        } else {
            interrupted += session.actualFocusSeconds
        }
    }
    return FocusPeriodTotals(completed, interrupted, pomodoros)
}

private fun secondsByDay(sessions: List<FocusSession>, zone: ZoneId): Map<LocalDate, Int> {
    val seconds = mutableMapOf<LocalDate, Int>()
    sessions.forEach { session ->
        val day = localDateOf(session.startedAt, zone)
        seconds[day] = (seconds[day] ?: 0) + session.actualFocusSeconds
    }
    return seconds
}

internal const val DaysPerWeek = 7

private const val MonthsPerYear = 12
private const val HoursPerDay = 24
private const val MinutesPerDay = 24 * 60
private const val MillisPerMinute = 60_000L

/** 排行只留前几条，够看出时间花在哪了。 */
private const val ReportTopEntries = 5
