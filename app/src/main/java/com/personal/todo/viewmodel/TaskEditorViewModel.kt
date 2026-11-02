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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    /**
     * 新建任务时攒下的子任务草稿（只有标题）。
     *
     * 父任务这一刻还没有 id，子任务没法提前挂上去，所以先在表单里记成一份标题清单，
     * 保存那一步由 `TaskRepository.saveTask` 在同一个事务里把它们建成真正的行。
     * 编辑已有任务时不走这条：那一页的子任务在详情页里加，改一条已有任务的子任务
     * 不该从「新建」的口子进来。
     */
    var subtaskDrafts by mutableStateOf<List<String>>(emptyList())
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

    /** 保存失败（写库中途出错）：留在编辑页把话说出来，不要先关页面再丢数据。 */
    var saveFailed by mutableStateOf(false)
        private set

    /**
     * 编辑已有任务时先读库、读完再让用户动表单。
     *
     * 读库是挂起的，而 `title` 初始为空、输入框立刻绑上去：打开编辑页后、读库返回前敲进去
     * 的字会被数据库回流整个覆盖掉；标签是第二次挂起读，回来得更晚，连用户新勾的标签也一并
     * 被替换。[loading] 为 true 时页面不画表单，这一段窗口就不存在了。
     */
    private val _loading = MutableStateFlow(taskId != null)
    val loading: StateFlow<Boolean> = _loading

    /**
     * 保存中。连点两次保存会建出两条一模一样的任务，第二次成功事件还会让路由连退两层；
     * 保存期间两个保存按钮都被禁用，[save] 自己也再挡一道。
     */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    /**
     * 编辑器里刚新建、可能还在落库的标签。保存前要把**它们全部**等回来，否则这次勾选会
     * 漏掉还没回填 id 的那几个。只记「最后一个 Job」是不够的：连着新建 A、B，若 A 比 B
     * 慢，等完 B 就保存，A 的 id 是保存之后才回来的，A 照样丢。
     */
    private val tagCreations = mutableListOf<Job>()

    /**
     * 当前这份表单是第几次打开。
     *
     * [startNewTask] 每调一次就换一个代号：上一次打开时发出去、还没回来的标签写入，
     * 回来时对不上号，就不许再把 id 塞进这张新表单（否则「上一次勾过的标签」会凭空
     * 出现在下一条任务上）。
     */
    private var session = 0

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        if (taskId == null) {
            _loading.value = false
        } else {
            viewModelScope.launch {
                try {
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
                } finally {
                    // 读不到这条任务也要放开：空表单也比把用户永久关在「加载中」里强。
                    _loading.value = false
                }
            }
        }
    }

    /**
     * 把这份表单重置成「新建一条任务」：每次打开新建弹层都先同步调它。
     *
     * 这个 view model 挂在 activity 的 store 上（`viewModel(key = "editor-sheet")`），
     * 关掉弹层、切页、推到任务详情都不会把它回收，所以下一次打开拿到的是**同一个实例**。
     * 「输入框里残留着上一次的标题」不是输入框没清，而是这份表单没清：标题、备注、日期、
     * 勾过的标签、攒下的子任务草稿，甚至上一次置上的 [saved] 标志全都还在（[saved] 还在
     * 的话，弹层一进来就会自己判定「已保存」而关掉）。
     *
     * 这里一次性清干净，同时换一个 [session] 代号：上一次打开时发出去、还没回来的标签
     * 写入对不上号，就不会把 id 塞进这张新表单。
     *
     * @param defaultListId 这次新建落在哪条清单（FAB 所在的视图决定）。
     */
    fun startNewTask(defaultListId: Long) {
        session++
        tagCreations.clear()
        _saved.value = false
        _saving.value = false
        title = ""
        notes = ""
        priority = Priority.DEFAULT
        listId = defaultListId
        plannedDate = null
        plannedTime = null
        dueDate = null
        dueTime = null
        repeatRule = null
        selectedTagIds = emptySet()
        subtaskDrafts = emptyList()
        titleHasError = false
        saveFailed = false
    }

    fun onTitleChange(value: String) {
        title = value
        if (titleHasError && value.isNotBlank()) titleHasError = false
    }

    fun onNotesChange(value: String) {
        notes = value
    }

    /** 往草稿里加一条子任务；空白标题直接丢掉，不占一行。 */
    fun onAddSubtask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        subtaskDrafts = subtaskDrafts + trimmed
    }

    /** 从草稿里去掉第 [index] 条（还没落库，纯表单动作）。 */
    fun onRemoveSubtask(index: Int) {
        if (index !in subtaskDrafts.indices) return
        subtaskDrafts = subtaskDrafts.filterIndexed { i, _ -> i != index }
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
        // 清掉已经跑完的，列表不随用得久而无界增长；剩下的都是「还没回填 id」的。
        tagCreations.removeAll { it.isCompleted }
        // 记下这一笔属于哪一次打开：中途 [startNewTask] 换了表单，回来就不认了。
        val createdFor = session
        tagCreations += viewModelScope.launch {
            val tagId = repository.createTag(name, color)
            if (createdFor == session) selectedTagIds = selectedTagIds + tagId
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
        // 还在读库、或上一次保存还没回来：直接挡住（按钮那边也被禁用了，这里是第二道）。
        if (_loading.value || _saving.value) return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            titleHasError = true
            return
        }
        saveFailed = false
        _saving.value = true
        // 这一笔属于哪一次打开：落库回来时如果弹层已经重新打开（[startNewTask] 换过代号），
        // 就不要再把「已保存」推给新表单——那会一进来就自己关掉。
        val savingFor = session
        viewModelScope.launch {
            try {
                // 刚在编辑器里新建的标签可能还在落库：**全部**等回来，否则这次勾选会漏掉。
                tagCreations.toList().forEach { it.join() }
                // 任务字段与标签关系收在一个事务里落库（repository.saveTask）：标签那一步
                // 失败时不再留下「任务写进去了、标签没写上」这种说不清的半完成状态。
                repository.saveTask(
                    taskId = taskId,
                    title = trimmedTitle,
                    listId = listId,
                    notes = notes,
                    priority = priority,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    repeatRule = repeatRule,
                    tagIds = selectedTagIds,
                    // 草稿与父任务同一个事务：要么都在，要么都不在。
                    subtaskTitles = subtaskDrafts
                )
                // 带规则的这条任务刚存下，立刻把后面那几次也落成独立任务，用户一回到
                // 列表就能看见它们。这一步幂等，且失败不影响已经存好的任务字段。
                if (repeatRule != null) repository.materializeRecurrences(today)
                // 只有全部写完才算保存成功：中途出错时留在编辑页，绝不先关页面。
                if (savingFor == session) _saved.value = true
            } catch (e: CancellationException) {
                // 页面销毁 / 作用域取消：这是取消，不是保存失败，原样抛出去继续传播。
                throw e
            } catch (e: Exception) {
                // 写库中途失败：留在编辑页把错误说出来（照 addSubtask 那处的做法，
                // 不让异常冒成崩溃，用户至少还能重试）。
                if (savingFor == session) saveFailed = true
            } finally {
                if (savingFor == session) _saving.value = false
            }
        }
    }

    companion object {
        fun factory(repository: TaskRepository, taskId: Long?, defaultListId: Long) = viewModelFactory {
            initializer { TaskEditorViewModel(repository, taskId, defaultListId) }
        }
    }
}
