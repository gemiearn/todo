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

    @TypeConverter
    fun eventTypeFromStored(value: String): TaskEventType = TaskEventType.valueOf(value)

    @TypeConverter
    fun focusModeToStored(mode: FocusMode): String = mode.name

    @TypeConverter
    fun focusModeFromStored(value: String): FocusMode = FocusMode.valueOf(value)

    @TypeConverter
    fun focusStatusToStored(status: FocusStatus): String = status.name

    @TypeConverter
    fun focusStatusFromStored(value: String): FocusStatus = FocusStatus.valueOf(value)

    @TypeConverter
    fun focusPhaseToStored(phase: FocusPhase): String = phase.name

    @TypeConverter
    fun focusPhaseFromStored(value: String): FocusPhase = FocusPhase.valueOf(value)

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
