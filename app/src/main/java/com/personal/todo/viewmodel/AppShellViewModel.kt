package com.personal.todo.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TaskList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the app shell: the list of 清单 shown in the drawer, the live open
 * task count per list shown as a badge, the total task count used by the
 * delete dialog, and the list operations.
 */
class AppShellViewModel(private val repository: TaskRepository) : ViewModel() {

    val lists: StateFlow<List<TaskList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Open tasks per list - the badge next to each list in the drawer. */
    val openTaskCounts: StateFlow<Map<Long, Int>> = repository.observeOpenTaskCountsByList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Every not deleted task per list - how many a list delete would move. */
    val taskCounts: StateFlow<Map<Long, Int>> = repository.observeTaskCountsByList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Id of the built-in inbox, or null during the very first frame. */
    var inboxId: Long? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            inboxId = repository.ensureInbox()
        }
    }

    fun createList(name: String) {
        viewModelScope.launch { repository.createList(name) }
    }

    fun renameList(listId: Long, name: String) {
        viewModelScope.launch { repository.renameList(listId, name) }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch { repository.deleteListMovingTasksToInbox(listId) }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { AppShellViewModel(repository) }
        }
    }
}
