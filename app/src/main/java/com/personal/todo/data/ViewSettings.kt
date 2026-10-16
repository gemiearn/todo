package com.personal.todo.data

import android.content.Context
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskFilter

/**
 * 日历的「显示设置」三项（说明文档 4.2）：是否显示已完成、是否显示周数、周一起还是
 * 周日起。它们只是看的方式，不是任务数据，所以存 SharedPreferences，不进数据库。
 *
 * [weekStartsMonday] 默认为 true——参考图里的月历就是周一起头的。
 */
data class CalendarSettings(
    val showCompleted: Boolean = false,
    val showWeekNumbers: Boolean = false,
    val weekStartsMonday: Boolean = true
)

/**
 * 显示范围筛选与日历显示设置的落盘口。筛选状态也存这里：用户勾过的范围下次进来还
 * 在，不必每次重设。列表顶栏和日历 ⋮ 用的是两份独立的筛子，互不影响。
 */
class ViewSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    fun readCalendarSettings(): CalendarSettings = CalendarSettings(
        showCompleted = prefs.getBoolean(KeyShowCompleted, false),
        showWeekNumbers = prefs.getBoolean(KeyShowWeekNumbers, false),
        weekStartsMonday = prefs.getBoolean(KeyWeekStartsMonday, true)
    )

    fun writeCalendarSettings(settings: CalendarSettings) {
        prefs.edit()
            .putBoolean(KeyShowCompleted, settings.showCompleted)
            .putBoolean(KeyShowWeekNumbers, settings.showWeekNumbers)
            .putBoolean(KeyWeekStartsMonday, settings.weekStartsMonday)
            .apply()
    }

    fun readListFilter(): TaskFilter = readFilter(KeyListFilter)

    fun writeListFilter(filter: TaskFilter) = writeFilter(KeyListFilter, filter)

    fun readCalendarFilter(): TaskFilter = readFilter(KeyCalendarFilter)

    fun writeCalendarFilter(filter: TaskFilter) = writeFilter(KeyCalendarFilter, filter)

    private fun readFilter(key: String): TaskFilter = TaskFilter(
        listIds = readIds(key, SuffixLists),
        priorities = readNames(key, SuffixPriorities)
            .mapNotNull { name -> Priority.entries.firstOrNull { it.name == name } }
            .toSet(),
        tagIds = readIds(key, SuffixTags)
    )

    private fun readIds(key: String, suffix: String): Set<Long> =
        prefs.getString(key + suffix, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            .orEmpty()

    private fun readNames(key: String, suffix: String): List<String> =
        prefs.getString(key + suffix, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun writeFilter(key: String, filter: TaskFilter) {
        prefs.edit()
            .putString(key + SuffixLists, filter.listIds.joinToString(","))
            .putString(key + SuffixPriorities, filter.priorities.joinToString(",") { it.name })
            .putString(key + SuffixTags, filter.tagIds.joinToString(","))
            .apply()
    }

    private companion object {
        const val Name = "view_settings"
        const val KeyShowCompleted = "calendar_show_completed"
        const val KeyShowWeekNumbers = "calendar_show_week_numbers"
        const val KeyWeekStartsMonday = "calendar_week_starts_monday"
        const val KeyListFilter = "list_filter"
        const val KeyCalendarFilter = "calendar_filter"
        const val SuffixLists = "_lists"
        const val SuffixPriorities = "_priorities"
        const val SuffixTags = "_tags"
    }
}
