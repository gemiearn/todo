package com.personal.todo.data

import androidx.room.withTransaction
import com.personal.todo.data.db.Habit
import com.personal.todo.data.db.HabitCheckIn
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.HabitRules
import com.personal.todo.model.Schedule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

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
    val zone: ZoneId = ZoneId.systemDefault()
) {

    private val habitDao = db.habitDao()

    /** 今天（本地日期），页面的「今天」列与打卡的默认落点。 */
    fun today(): LocalDate = Schedule.todayOf(now(), zone)

    fun observeHabits(): Flow<List<Habit>> = habitDao.observeHabits()

    /** [from]..[to] 这段日期里的打卡记录。 */
    fun observeCheckIns(from: LocalDate, to: LocalDate): Flow<List<HabitCheckIn>> =
        habitDao.observeCheckInsBetween(from, to)

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
