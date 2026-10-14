package com.personal.todo.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Form state for creating or editing one task (title, notes, priority, list).
 * Editing a completed task keeps it completed - reopening stays an explicit
 * user action.
 */
class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val taskId: Long?,
    defaultListId: Long
) : ViewModel() {

    val isNewTask: Boolean = taskId == null

    val lists: StateFlow<List<TaskList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
        private set

    var notes by mutableStateOf("")
        private set

    var priority by mutableStateOf(Priority.NORMAL)
        private set

    var listId by mutableLongStateOf(defaultListId)
        private set

    var titleHasError by mutableStateOf(false)
        private set

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = repository.getTask(taskId) ?: return@launch
                title = task.title
                notes = task.notes.orEmpty()
                priority = task.priority
                listId = task.listId
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value
        if (titleHasError && value.isNotBlank()) titleHasError = false
    }

    fun onNotesChange(value: String) {
        notes = value
    }

    fun onPriorityChange(value: Priority) {
        priority = value
    }

    fun onListChange(value: Long) {
        listId = value
    }

    fun save() {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            titleHasError = true
            return
        }
        viewModelScope.launch {
            if (taskId == null) {
                repository.createTask(
                    title = trimmedTitle,
                    listId = listId,
                    notes = notes,
                    priority = priority
                )
            } else {
                repository.updateTask(
                    taskId = taskId,
                    title = trimmedTitle,
                    notes = notes,
                    priority = priority,
                    listId = listId
                )
            }
            _saved.value = true
        }
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long?, defaultListId: Long) = viewModelFactory {
            initializer { TaskEditorViewModel(repository, taskId, defaultListId) }
        }
    }
}
