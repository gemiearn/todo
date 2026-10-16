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
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    /** 全部标签：编辑器「标签」那一组把它们列出来让用户勾。 */
    val allTags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 这条任务已经勾上的标签 id；新建任务默认一个都没有。 */
    var selectedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var title by mutableStateOf("")
        private set

    var notes by mutableStateOf("")
        private set

    /**
     * 优先级就是四象限：「重要」与「紧急」两个开关的组合。界面上是两个开关，存下去
     * 的仍是 [Priority] 那一个值，四象限页直接读它。
     */
    var priority by mutableStateOf(Priority.DEFAULT)
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

    /**
     * 重复规则。规则只留在这条任务上，算出来的每一次都是**独立任务**，所以这里只要
     * 一个动作：开 / 换 / 关。
     *
     * 规则需要一个起点，也就是开始日期；点重复时还没有日期就先落到今天，不会出现
     * 「有规则却没有起点」这种状态。
     */
    var repeatRule by mutableStateOf<RepeatRule?>(null)
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
                repeatRule = task.repeatRule
                // 标签是另一张表，单独取一次（first 拿到当前值就够，标签数很少）。
                selectedTagIds = repository.observeTagsOf(taskId).first().map { it.id }.toSet()
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

    /** 打开 / 关掉「重要」这一维，另一维保持不变。 */
    fun onImportantChange(value: Boolean) {
        priority = Priority.of(important = value, urgent = priority.urgent)
    }

    /** 打开 / 关掉「紧急」这一维，另一维保持不变。 */
    fun onUrgentChange(value: Boolean) {
        priority = Priority.of(important = priority.important, urgent = value)
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

    /** 开、换、关重复规则；没有开始日期就先落到今天，规则总要有个起点。 */
    fun onRepeatChange(value: RepeatRule?) {
        repeatRule = value
        if (value != null && plannedDate == null) plannedDate = today
    }

    /**
     * 在编辑器里就地新建一个标签，建完立刻勾在这条任务上。
     *
     * 省得为了打一个还不存在的标签退出到「更多 - 标签管理」再回来：标签管理页那套
     * 新建/改名/改色弹层在这里原样复用，走的是同一个 repository 入口。
     */
    fun createTag(name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tagId = repository.createTag(name, color)
            selectedTagIds = selectedTagIds + tagId
        }
    }

    /** 勾上 / 取消一个标签。标签是多值属性，两者互不影响。 */
    fun onToggleTag(tagId: Long) {
        selectedTagIds = if (tagId in selectedTagIds) {
            selectedTagIds - tagId
        } else {
            selectedTagIds + tagId
        }
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
            val savedTaskId = if (taskId == null) {
                repository.createTask(
                    title = trimmedTitle,
                    listId = listId,
                    notes = notes,
                    priority = priority,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    repeatRule = repeatRule
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
                    dueTime = dueTime,
                    repeatRule = repeatRule
                )
                taskId
            }
            // 标签存在另一张表：把这条任务的标签整个换成勾中的这一组。
            repository.setTaskTags(savedTaskId, selectedTagIds)
            // 带规则的这条任务刚存下，立刻把后面两周那几次也落成独立任务，用户一回到
            // 列表就能看见它们。
            if (repeatRule != null) repository.materializeRecurrences(today)
            _saved.value = true
        }
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long?, defaultListId: Long) = viewModelFactory {
            initializer { TaskEditorViewModel(repository, taskId, defaultListId) }
        }
    }
}
