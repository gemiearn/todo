package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.HabitRepository
import com.personal.todo.data.db.Habit
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 习惯页的状态：全部习惯，加上「最近 7 天」每格里的次数。
 *
 * 打卡就是改那一天的次数（加一 / 减一），没有别的状态；「今天」不是随时间流动的值，
 * 而是页面打开那一刻的本地日期——习惯页是推入页，每次进来都是新的一份。
 */
class HabitViewModel(
    private val repository: HabitRepository
) : ViewModel() {

    /** 今天（本地日期）：格子里最右那一格，也是「点一下」默认打的那一天。 */
    val today: LocalDate = repository.today()

    /**
     * 「最近 7 天」这一行格子：最左是 6 天前、最右是今天。
     *
     * 只看最近 7 天而不是整月：这一页的定位是「今天做了没」，一周足够把忘打的几天补上，
     * 再往前铺就成了第二个日历，和「简单一张打勾表」的定位冲突。
     */
    val week: List<LocalDate> = (DaysWindow - 1 downTo 0).map { today.minusDays(it.toLong()) }

    // 页面走开就停，不多守 5 秒：这是一张本地表，重新进来只是一次很便宜的查询，
    // 而没有订阅者还挂着的查询会一直吊着——关库时它会把自己炸掉。
    val habits: StateFlow<List<Habit>> = repository.observeHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** habitId -> (日期 -> 次数)；没记录的那天就是 0 次。 */
    val counts: StateFlow<Map<Long, Map<LocalDate, Int>>> =
        repository.observeCheckIns(week.first(), week.last())
            .map { rows ->
                rows.groupBy { it.habitId }
                    .mapValues { (_, ofHabit) -> ofHabit.associate { it.date to it.count } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    fun create(
        name: String,
        dailyTarget: Int,
        unitLabel: String? = null,
        stepAmount: Int? = null,
        icon: String? = null,
        linkedTaskId: Long? = null,
        autoCheckInOnFocus: Boolean = false
    ) {
        viewModelScope.launch {
            repository.createHabit(name, dailyTarget, unitLabel, stepAmount, icon, linkedTaskId, autoCheckInOnFocus)
        }
    }

    fun update(
        habitId: Long,
        name: String,
        dailyTarget: Int,
        unitLabel: String? = null,
        stepAmount: Int? = null,
        icon: String? = null,
        linkedTaskId: Long? = null,
        autoCheckInOnFocus: Boolean = false
    ) {
        viewModelScope.launch {
            repository.updateHabit(
                habitId, name, dailyTarget, unitLabel, stepAmount, icon, linkedTaskId, autoCheckInOnFocus
            )
        }
    }

    fun delete(habitId: Long) {
        viewModelScope.launch { repository.deleteHabit(habitId) }
    }

    /** 点一下：这一天 +1。 */
    fun increment(habitId: Long, date: LocalDate) {
        viewModelScope.launch { repository.increment(habitId, date) }
    }

    /** 长按：这一天 −1。 */
    fun decrement(habitId: Long, date: LocalDate) {
        viewModelScope.launch { repository.decrement(habitId, date) }
    }

    companion object {
        /** 格子铺几天：最近 7 天。 */
        const val DaysWindow = 7

        fun factory(repository: HabitRepository) = viewModelFactory {
            initializer { HabitViewModel(repository) }
        }
    }
}
