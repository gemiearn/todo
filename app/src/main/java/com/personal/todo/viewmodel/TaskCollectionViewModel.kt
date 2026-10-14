package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which task collection a screen shows. Views are queries, never copies. */
sealed interface TaskCollection {
    data object Inbox : TaskCollection
    data class ListDetail(val listId: Long) : TaskCollection
    data object Completed : TaskCollection
    data object Trash : TaskCollection
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subtaskCounts: StateFlow<Map<Long, Int>> = repository.observeSubtaskCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
