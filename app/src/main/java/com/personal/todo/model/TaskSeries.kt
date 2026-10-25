package com.personal.todo.model

import com.personal.todo.data.db.Task

/**
 * 一条重复任务在库里是**规则源 + 每次一条独立任务**（说明文档 4.5）。用户看到的是
 * 「一件事每天重复」，删的时候也该能这么想：只删今天这一次，还是把这件从此不再重复。
 *
 * 规则只长在源上（`repeatRule != null`），实例反过来用 `recurrenceSourceId` 指向源。
 * 所以「它属于哪一串」这件事只有这一处判定——界面上凡是需要「这是重复任务吗」「这条属于
 * 哪一串」都走这里，不再各自写一遍。
 */
object TaskSeries {

    /** 这一串重复任务的源 id；不重复的任务返回 null。 */
    fun sourceIdOf(task: Task): Long? = when {
        task.recurrenceSourceId != null -> task.recurrenceSourceId
        task.repeatRule != null -> task.id
        else -> null
    }

    /**
     * 这条任务是不是一串重复任务里的一次（含规则源自己）：任务卡上的 🔁、详情页的「重复
     * 任务」小签、以及删除时要不要问「整条系列」，问的都是它。
     */
    fun belongsToSeries(task: Task): Boolean = sourceIdOf(task) != null
}
