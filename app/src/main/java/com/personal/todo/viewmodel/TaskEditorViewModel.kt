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
import java.time.LocalDate
import java.time.LocalTime
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

    /**
     * Dates follow spec section 4.1: the planned date and the due date are
     * independent, and a due time only exists on a day - clearing the due date
     * therefore clears the time with it.
     */
    var plannedDate by mutableStateOf<LocalDate?>(null)
        private set

    /** The moment work is meant to start; it needs a planned date to hang on. */
    var plannedTime by mutableStateOf<LocalTime?>(null)
        private set

    var dueDate by mutableStateOf<LocalDate?>(null)
        private set

    var dueTime by mutableStateOf<LocalTime?>(null)
        private set

    /** The day the pickers open on and the day 今天 / 明天 are measured from. */
    val today: LocalDate = repository.today()

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
                plannedDate = task.plannedDate
                plannedTime = task.plannedTime
                dueDate = task.dueDate
                dueTime = task.dueTime
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

    /** Clearing the planned date takes the planned time with it: a time needs a day. */
    fun onPlannedDateChange(value: LocalDate?) {
        plannedDate = value
        if (value == null) plannedTime = null
    }

    fun onPlannedTimeChange(value: LocalTime?) {
        plannedTime = value
    }

    /** Clearing the due date takes the due time with it: a time needs a day. */
    fun onDueDateChange(value: LocalDate?) {
        dueDate = value
        if (value == null) dueTime = null
    }

    fun onDueTimeChange(value: LocalTime?) {
        dueTime = value
    }

    /**
     * Consumes the save result. A view model can outlive its screen (the same
     * route opened again gets a fresh one now, but this keeps the screen from
     * ever popping itself twice).
     */
    fun consumeSaved() {
        _saved.value = false
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
                    priority = priority,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime
                )
            } else {
                repository.updateTask(
                    taskId = taskId,
                    title = trimmedTitle,
                    notes = notes,
                    priority = priority,
                    listId = listId,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime
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
