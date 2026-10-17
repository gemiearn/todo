package com.personal.todo.data.db

import androidx.room.TypeConverter
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusPhase
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatRule
import com.personal.todo.model.TaskEventType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Enums are stored by name on purpose: adding or reordering enum values later
 * must not silently change the meaning of already stored rows.
 *
 * 读回来时**不能**直接 `valueOf`：以后重命名或删掉一个常量，旧数据反序列化就会抛
 * `IllegalArgumentException` 把 App 打崩（[priorityFromStored] 走的 `Priority.fromStored`
 * 本来就有兜底，这里其余几个枚举也照同一套做：名字对不上就退回一个最保守的值）。
 *
 * Dates are stored as `YYYY-MM-DD` and times as `HH:mm` (spec section 4.1):
 * a pure date must never be converted to UTC first, otherwise a task could
 * slide to the neighbouring day when the device changes time zone.
 */
class Converters {

    @TypeConverter
    fun priorityToStored(priority: Priority): String = priority.name

    @TypeConverter
    fun priorityFromStored(value: String): Priority = Priority.fromStored(value)

    @TypeConverter
    fun eventTypeToStored(type: TaskEventType): String = type.name

    /** 认不出来的事件退回 [TaskEventType.UNKNOWN]，而不是让整个任务详情读不出来。 */
    @TypeConverter
    fun eventTypeFromStored(value: String): TaskEventType =
        TaskEventType.entries.firstOrNull { it.name == value } ?: TaskEventType.UNKNOWN

    @TypeConverter
    fun focusModeToStored(mode: FocusMode): String = mode.name

    /** 认不出来的模式退回默认的番茄钟。 */
    @TypeConverter
    fun focusModeFromStored(value: String): FocusMode =
        FocusMode.entries.firstOrNull { it.name == value } ?: FocusMode.POMODORO

    @TypeConverter
    fun focusStatusToStored(status: FocusStatus): String = status.name

    /** 认不出来的状态退回「中断」：不能把一段来源不明的记录算成完成的番茄。 */
    @TypeConverter
    fun focusStatusFromStored(value: String): FocusStatus =
        FocusStatus.entries.firstOrNull { it.name == value } ?: FocusStatus.INTERRUPTED

    @TypeConverter
    fun focusPhaseToStored(phase: FocusPhase): String = phase.name

    /** 认不出来的阶段退回「专注」：休息不写历史，退成休息反而会把它藏起来。 */
    @TypeConverter
    fun focusPhaseFromStored(value: String): FocusPhase =
        FocusPhase.entries.firstOrNull { it.name == value } ?: FocusPhase.FOCUS

    @TypeConverter
    fun repeatRuleToStored(rule: RepeatRule?): String? = rule?.toStored()

    @TypeConverter
    fun repeatRuleFromStored(value: String?): RepeatRule? = RepeatRule.fromStored(value)

    @TypeConverter
    fun localDateToStored(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun localDateFromStored(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToStored(time: LocalTime?): String? = time?.format(HourMinute)

    @TypeConverter
    fun localTimeFromStored(value: String?): LocalTime? = value?.let(LocalTime::parse)

    private companion object {
        val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
