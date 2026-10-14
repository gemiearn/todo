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

    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task

    val subtasks: StateFlow<List<Task>> = repository.observeSubtasks(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<TaskEvent>> = repository.observeEvents(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        reload()
    }

    fun complete() {
        viewModelScope.launch {
            if (repository.completeTask(taskId)) reload()
        }
    }

    fun reopen() {
        viewModelScope.launch {
            if (repository.reopenTask(taskId)) reload()
        }
    }

    /** Soft deletes the task, and its subtasks with it. */
    fun delete() {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            reload()
        }
    }

    fun restore() {
        viewModelScope.launch {
            repository.restoreTask(taskId)
            reload()
        }
    }

    fun restoreSubtask(subtaskId: Long) {
        viewModelScope.launch { repository.restoreTask(subtaskId) }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addSubtask(taskId, title) }
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

    private fun reload() {
        viewModelScope.launch { _task.value = repository.getTask(taskId) }
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long) = viewModelFactory {
            initializer { TaskDetailViewModel(repository, taskId) }
        }
    }
}
