package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.TaskSeriesTotals
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import com.personal.todo.data.db.TaskFocusTotals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of one task detail screen: the task itself, its subtasks and its event
 * history. Subtasks and events are live queries; the task row is reloaded after
 * every action because those actions change its own state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val repository: TaskRepository,
    focusRepository: FocusRepository,
    private val taskId: Long
) : ViewModel() {

    /**
     * The task itself is a live query, not a snapshot: completing a task from
     * the list (or any other screen) is reflected here without a manual reload.
     */
    val task: StateFlow<Task?> = repository.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 这条任务的专注合计（累计时长 / 次数 / 番茄数），页面上的「专注小结」卡读它。
     * 专注记录在另一张表，所以这里单独订阅一条；做完一次专注，这张卡自己会更新。
     */
    val focusTotals: StateFlow<TaskFocusTotals> = focusRepository.observeTaskTotals(taskId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TaskFocusTotals(totalSeconds = 0, sessionCount = 0, pomodoroCount = 0)
        )

    /**
     * 重复任务**整条系列**的合计（完成几次、共专注多久）：只有规则源那一行有值，实例页与
     * 普通任务都是 null。依据就是 `repeatRule`——规则只长在源上，源没了整条系列也就没了。
     */
    val seriesTotals: StateFlow<TaskSeriesTotals?> = task
        .map { current -> current?.takeIf { it.repeatRule != null }?.id }
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            if (sourceId == null) {
                flowOf<TaskSeriesTotals?>(null)
            } else {
                repository.observeSeriesTotals(sourceId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 这一条现在能不能按「整条系列」删：属于某串、而且那串的规则源还活着。
     *
     * 详情页在**点删除的那一刻**问它（suspend，一次库查询）：先问清再决定是「弹框让人选」
     * 还是「直接删」，就不会出现「流还没查完 → 走成直接删掉 → 整条系列再也点不到」；
     * 源已经被删掉的残留实例也会如实返回 false，不会给出一个误导人的「整条系列」选项。
     */
    suspend fun canDeleteAsSeries(): Boolean = repository.isInLiveSeries(taskId)

    val subtasks: StateFlow<List<Task>> = repository.observeSubtasks(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<TaskEvent>> = repository.observeEvents(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 这条任务身上的标签，详情页在标题下画成小片。 */
    val tags: StateFlow<List<Tag>> = repository.observeTagsOf(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 拒绝添加子任务的次数，只增不改（显示完再归零）。
     *
     * 原来是一个 Boolean：第一次失败提示要挂大约 4 秒，这期间再失败一次，值没变、StateFlow
     * 不发射、`LaunchedEffect` 的 key 也没变——第二次失败**完全没有提示**，用户以为加上了。
     * 用次数就每次都变，提示一次都不会漏。
     */
    private val _subtaskAddFailures = MutableStateFlow(0)

    /** 加子任务被拒绝的次数；每次 +1，页面提示完调 [consumeSubtaskAddFailure] 归零。 */
    val subtaskAddFailures: StateFlow<Int> = _subtaskAddFailures

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

    /**
     * 删掉这一条所属的**整条重复系列**：规则源和它生成过的每一次一起进回收站，
     * 从今往后再也不会长出新的那一次（说明文档 4.5）。
     */
    fun deleteSeries() {
        viewModelScope.launch { repository.deleteSeries(taskId) }
    }

    fun restore() {
        viewModelScope.launch { repository.restoreTask(taskId) }
    }

    /**
     * 撤销刚才那次删除。[series] 为 true 表示刚删的是整条系列，恢复的也得是整条——
     * 只恢复一条的话，第二轮生成又会把后面的每一次补回来，「撤销」看着像没生效。
     */
    fun restore(series: Boolean) {
        viewModelScope.launch {
            if (series) repository.restoreSeries(taskId) else repository.restoreTask(taskId)
        }
    }

    fun restoreSubtask(subtaskId: Long) {
        viewModelScope.launch { repository.restoreTask(subtaskId) }
    }

    /**
     * Never throws to the caller: a refused add (deleted parent, second level,
     * ...) surfaces as [subtaskAddFailures] so the screen shows a message instead
     * of crashing.
     */
    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addSubtask(taskId, title) }
                .onFailure { _subtaskAddFailures.value += 1 }
        }
    }

    fun consumeSubtaskAddFailure() {
        _subtaskAddFailures.value = 0
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
        fun factory(repository: TaskRepository, focusRepository: FocusRepository, taskId: Long) =
            viewModelFactory {
                initializer { TaskDetailViewModel(repository, focusRepository, taskId) }
            }
    }
}
