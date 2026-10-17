package com.personal.todo.model

/**
 * The key task changes worth keeping in the history (spec section 7.3).
 *
 * Only meaningful state transitions are recorded - never individual keystrokes.
 * Events are append-only: reopening a task adds a REOPENED event and keeps the
 * earlier COMPLETED event.
 */
enum class TaskEventType {
    CREATED,
    COMPLETED,
    REOPENED,
    DELETED,
    RESTORED,

    /**
     * 库里存着一个认不出来的名字时落到这里（枚举按名字存，以后改名或删值都可能留下
     * 旧名字）。它只在读旧数据时出现，永远不会被写进库；显示成「未知操作」，
     * 而不是让整条任务详情直接崩掉。
     */
    UNKNOWN
}
