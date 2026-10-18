package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A task list (清单). The inbox is the built-in list that always exists and
 * can never be deleted (spec section 7.3).
 *
 * [icon] 是清单名字前面那个小图标（一个 emoji 字符）。空＝没有图标，所以升级前就有的
 * 清单读出来与原来一模一样，不用猜。
 */
@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String? = null,
    val isInbox: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
