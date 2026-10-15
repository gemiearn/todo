package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tag (标签): an attribute a task can carry.
 *
 * The official definition of the tool this app is modelled on draws a clear line
 * between the two ways of organising work: a *清单* is where a task belongs and
 * every task has exactly one, while a *标签* is a property a task can have
 * several of and which reaches across lists ("等回复", "打电话", "会议室").
 * That is why a tag is a table of its own instead of another field on the task:
 * one task has many tags, and a tag is not owned by a list.
 *
 * [color] is the ARGB colour of the chip on the task card. It is picked from a
 * palette when the tag is created and can be changed later.
 */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val color: Int,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * The many-to-many link between a task and a tag.
 *
 * A separate row per pair is what makes "several tags on one task" and "one tag
 * across many lists" both true at once. Both foreign keys use RESTRICT like the
 * rest of the schema - nothing is deleted by the database behind the app's back,
 * and deleting a tag removes its links explicitly, in one transaction.
 *
 * The primary key already indexes `taskId`, so only `tagId` needs its own index.
 */
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("tagId")]
)
data class TaskTag(
    val taskId: Long,
    val tagId: Long
)
