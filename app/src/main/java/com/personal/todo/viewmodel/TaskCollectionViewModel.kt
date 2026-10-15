package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which task collection a screen shows. Views are queries, never copies. */
sealed interface TaskCollection {
    data object Inbox : TaskCollection
    data class ListDetail(val listId: Long) : TaskCollection
    data object Completed : TaskCollection
    data object Trash : TaskCollection

    /** 今日: planned or due today (spec section 4.1). */
    data object Today : TaskCollection

    /** 未来: dated after today. */
    data object Future : TaskCollection

    /** 逾期: past the due moment - never postponed automatically. */
    data object Overdue : TaskCollection

    /** One day of the calendar; the month grid selects which one. */
    data class DayView(val date: LocalDate) : TaskCollection

    /**
     * No collection at all: the routes that only show the shell (更多) or their
     * own screen (四象限). It exists so the shell can create its view model
     * unconditionally - a conditionally created view model would shift the
     * composition slot table and crash the next rememberSaveable.
     */
    data object None : TaskCollection
}

class TaskCollectionViewModel(
    private val repository: TaskRepository,
    collection: TaskCollection
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = when (collection) {
        TaskCollection.Inbox -> repository.observeInboxTasks()
        is TaskCollection.ListDetail -> repository.observeOpenTasksInList(collection.listId)
        TaskCollection.Completed -> repository.observeCompletedTasks()
        TaskCollection.Trash -> repository.observeDeletedTasks()
        TaskCollection.Today -> repository.observeTodayTasks()
        TaskCollection.Future -> repository.observeFutureTasks()
        TaskCollection.Overdue -> repository.observeOverdueTasks()
        is TaskCollection.DayView -> repository.observeOpenTasksOn(collection.date)
        TaskCollection.None -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subtaskCounts: StateFlow<Map<Long, Int>> = repository.observeSubtaskCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Completed tasks of this view. A list keeps its finished tasks folded away
     * behind a "已完成 N" header instead of losing them to another screen; the
     * 已完成 and 回收站 views have nothing to fold, and the 未来 / 逾期 views
     * have no day of their own to fold from.
     */
    val completedTasks: StateFlow<List<Task>> = when (collection) {
        TaskCollection.Inbox -> repository.observeCompletedInboxTasks()
        is TaskCollection.ListDetail -> repository.observeCompletedTasksInList(collection.listId)
        TaskCollection.Today -> repository.observeCompletedTasksOn(repository.today())
        is TaskCollection.DayView -> repository.observeCompletedTasksOn(collection.date)
        TaskCollection.Completed,
        TaskCollection.Trash,
        TaskCollection.Future,
        TaskCollection.Overdue,
        TaskCollection.None -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun complete(taskId: Long) {
        viewModelScope.launch { repository.completeTask(taskId) }
    }

    fun reopen(taskId: Long) {
        viewModelScope.launch { repository.reopenTask(taskId) }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch { repository.deleteTask(taskId) }
    }

    fun restore(taskId: Long) {
        viewModelScope.launch { repository.restoreTask(taskId) }
    }

    companion object {
        fun factory(repository: TaskRepository, collection: TaskCollection) = viewModelFactory {
            initializer { TaskCollectionViewModel(repository, collection) }
        }
    }
}
