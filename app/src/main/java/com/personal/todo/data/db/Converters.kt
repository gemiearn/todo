package com.personal.todo.data.db

import androidx.room.TypeConverter
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType

/**
 * Enums are stored by name on purpose: adding or reordering enum values later
 * must not silently change the meaning of already stored rows.
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
}
