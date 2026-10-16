package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Tag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the 标签管理页: every tag there is, plus the three things a tag can be
 * put through - created, renamed/recoloured, deleted. Deleting a tag never
 * touches a task: it only removes the mark.
 */
class TagViewModel(private val repository: TaskRepository) : ViewModel() {

    val tags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, color: Int) {
        viewModelScope.launch { repository.createTag(name, color) }
    }

    fun update(tagId: Long, name: String, color: Int) {
        viewModelScope.launch { repository.updateTag(tagId, name, color) }
    }

    fun delete(tagId: Long) {
        viewModelScope.launch { repository.deleteTag(tagId) }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { TagViewModel(repository) }
        }
    }
}
