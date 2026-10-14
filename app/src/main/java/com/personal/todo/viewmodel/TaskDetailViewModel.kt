package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of one task detail screen: the task itself, its subtasks and its event
 * history. Subtasks and events are live queries; the task row is reloaded after
 * every action because those actions change its own state.
 */
class TaskDetailViewModel(
    private val repository: TaskRepository,
    private val taskId: Long
) : ViewModel() {

    /**
     * The task itself is a live query, not a snapshot: completing a task from
     * the list (or any other screen) is reflected here without a manual reload.
     */
    val task: StateFlow<Task?> = repository.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val subtasks: StateFlow<List<Task>> = repository.observeSubtasks(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<TaskEvent>> = repository.observeEvents(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _subtaskAddFailed = MutableStateFlow(false)

    /** True when the last "add subtask" attempt was refused by the repository. */
    val subtaskAddFailed: StateFlow<Boolean> = _subtaskAddFailed

    fun complete() {
        viewModelScope.launch { repository.completeTask(taskId) }
    }

    fun reopen() {
        viewModelScope.launch { repository.reopenTask(taskId) }
    }

    /** Soft deletes the task, and its subtasks with it. */
    fun delete() {
        viewModelScope.launch { repository.deleteTask(taskId) }
    }

    fun restore() {
        viewModelScope.launch { repository.restoreTask(taskId) }
    }

    fun restoreSubtask(subtaskId: Long) {
        viewModelScope.launch { repository.restoreTask(subtaskId) }
    }

    /**
     * Never throws to the caller: a refused add (deleted parent, second level,
     * ...) surfaces as [subtaskAddFailed] so the screen shows a message instead
     * of crashing.
     */
    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addSubtask(taskId, title) }
                .onFailure { _subtaskAddFailed.value = true }
        }
    }

    fun consumeSubtaskAddFailure() {
        _subtaskAddFailed.value = false
    }

    fun toggleSubtask(subtask: Task) {
        viewModelScope.launch {
            if (subtask.completedAt == null) {
                repository.completeTask(subtask.id)
            } else {
                repository.reopenTask(subtask.id)
            }
        }
    }

    fun deleteSubtask(subtaskId: Long) {
        viewModelScope.launch { repository.deleteTask(subtaskId) }
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long) = viewModelFactory {
            initializer { TaskDetailViewModel(repository, taskId) }
        }
    }
}
