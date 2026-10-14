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
    RESTORED
}
