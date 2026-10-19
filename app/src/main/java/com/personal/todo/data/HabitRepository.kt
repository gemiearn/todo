package com.personal.todo.data

import androidx.room.withTransaction
import com.personal.todo.data.db.Habit
import com.personal.todo.data.db.HabitCheckIn
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.HabitRules
import com.personal.todo.model.Schedule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 「今天」那条流多久重新读一次日期：60 秒足够，跨过午夜后最多晚一分钟翻页。任务侧
 * [TaskRepository.observeToday] 用的是同一个数。
 */
private const val TodayRefreshMillis = 60_000L

/**
 * 习惯与打卡（需求文档 3）。
 *
 * 这一层只管「一天一次数」这一件事：加一次、减一次、删习惯。次数怎么夹、什么时候算完成
 * 是 [HabitRules] 的纯逻辑，所以那两条规则能脱离数据库直接测。
 *
 * 打卡记录永远按**本地日期**写（[today] 的算法和任务一样），不看现在的钟点。
 */
class HabitRepository(
    private val db: TodoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** [observeToday] 的重新读日期间隔；测试传 1 毫秒，这样跨天只等一次 tick。 */
    private val todayRefreshMillis: Long = TodayRefreshMillis
) {

    private val habitDao = db.habitDao()

    /** 今天（本地日期），页面的「今天」列与打卡的默认落点。 */
    fun today(): LocalDate = Schedule.todayOf(now(), zone)

    /**
     * 今天的日期，每 [todayRefreshMillis] 重新读一次。
     *
     * 习惯页的「最近 7 天」最右是今天，打卡的默认落点也是今天。进程活过午夜时这一格必须
     * 跟着翻页：页面一直开着不动，不能还停在「这一份建立的那一天」。日期没变时不重复发射。
     */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(todayRefreshMillis)
        }
    }.distinctUntilChanged()

    fun observeHabits(): Flow<List<Habit>> = habitDao.observeHabits()

    /** [from]..[to] 这段日期里的打卡记录。 */
    fun observeCheckIns(from: LocalDate, to: LocalDate): Flow<List<HabitCheckIn>> =
        habitDao.observeCheckInsBetween(from, to)

    /**
     * 今天还没完成的习惯条数：抽屉里「习惯」那一行的徽章用它。
     *
     * 「完成」按 [HabitRules.isComplete]（`次数 × 单次基数 ≥ 目标`）判，与习惯页那一格
     * 显示的「完成」同一个口径；今天没有打卡记录的习惯算没完成。窗口跟着 [observeToday]
     * 走，跨过午夜时这个数自己会翻页。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayPendingCount(): Flow<Int> =
        combine(observeHabits(), observeToday()) { habits, date -> habits to date }
            .flatMapLatest { (habits, date) ->
                habitDao.observeCheckInsBetween(date, date).map { rows ->
                    val counts = rows.associate { it.habitId to it.count }
                    habits.count { habit ->
                        !HabitRules.isComplete(counts[habit.id] ?: 0, habit.dailyTarget, habit.step)
                    }
                }
            }

    suspend fun createHabit(
        name: String,
        dailyTarget: Int,
        unitLabel: String? = null,
        stepAmount: Int? = null,
        icon: String? = null
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "习惯名不能为空" }
        val time = now()
        return habitDao.insert(
            Habit(
                name = trimmed,
                icon = cleanIcon(icon),
                dailyTarget = dailyTarget.coerceAtLeast(HabitRules.MinTarget),
                unitLabel = cleanUnit(unitLabel),
                stepAmount = cleanStep(stepAmount),
                // 新习惯排到最后，不插到用户排好的顺序中间。
                sortOrder = (habitDao.maxSortOrder() ?: -1) + 1,
                createdAt = time,
                updatedAt = time
            )
        )
    }

    /** 名字、单位、目标量、单次基数与图标走同一个入口：习惯就这几样可编辑的东西。 */
    suspend fun updateHabit(
        habitId: Long,
        name: String,
        dailyTarget: Int,
        unitLabel: String? = null,
        stepAmount: Int? = null,
        icon: String? = null
    ) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "习惯名不能为空" }
        val habit = habitDao.getById(habitId) ?: return
        habitDao.update(
            habit.copy(
                name = trimmed,
                icon = cleanIcon(icon),
                dailyTarget = dailyTarget.coerceAtLeast(HabitRules.MinTarget),
                unitLabel = cleanUnit(unitLabel),
                stepAmount = cleanStep(stepAmount),
                // v14 那两列（关联任务 / 专注自动打卡）已经不用了（用户 2026-10-19：习惯
                // 独立存在），顺手把老数据上残留的值清掉；列本身留着，不动库版本。
                linkedTaskId = null,
                autoCheckInOnFocus = false,
                updatedAt = now()
            )
        )
    }

    /** 删习惯时连它的打卡记录一起清掉（同一事务），不留悬空行。 */
    suspend fun deleteHabit(habitId: Long) = db.withTransaction {
        habitDao.clearCheckInsOf(habitId)
        habitDao.delete(habitId)
    }

    /** 点一下：这一天 +1（夹到目标为止）。 */
    suspend fun increment(habitId: Long, date: LocalDate) = changeCount(habitId, date, 1)

    /** 长按：这一天 −1；减到 0 就把这一行删掉（没记录＝没打卡）。 */
    suspend fun decrement(habitId: Long, date: LocalDate) = changeCount(habitId, date, -1)

    private suspend fun changeCount(habitId: Long, date: LocalDate, delta: Int) = db.withTransaction {
        val habit = habitDao.getById(habitId) ?: return@withTransaction
        val current = habitDao.getCheckIn(habitId, date)?.count ?: 0
        val next = HabitRules.nextCount(current, habit.dailyTarget, habit.step, delta)
        if (next < HabitRules.MinTarget) {
            habitDao.deleteCheckIn(habitId, date)
        } else {
            habitDao.upsertCheckIn(
                HabitCheckIn(habitId = habitId, date = date, count = next, updatedAt = now())
            )
        }
    }

    /** 空单位与 1 都存成 null：这样「次 / 1」的老习惯与显式写了「次」的习惯是同一种数据。 */
    private fun cleanUnit(unitLabel: String?): String? =
        unitLabel?.trim()?.takeIf { it.isNotEmpty() && it != Habit.DefaultUnit }

    private fun cleanStep(stepAmount: Int?): Int? =
        stepAmount?.takeIf { it > 1 }

    /** 空图标存成 null：这样「没有图标」只有一种表示，选「无」就是把这一列清空。 */
    private fun cleanIcon(icon: String?): String? = icon?.trim()?.takeIf { it.isNotEmpty() }
}
