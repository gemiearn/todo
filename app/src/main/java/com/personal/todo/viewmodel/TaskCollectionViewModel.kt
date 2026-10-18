package com.personal.todo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.model.nearestOpenOccurrencePerSeries
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    /**
     * 日历网格下面那一条所用的日期。
     *
     * 它是**可变的**：日历翻月落定会把选中日期改成 1 号，原来壳层按
     * `collection-day-<日期>` 给每一天建一个新实例，`stateIn(emptyList())` 从空开始，
     * 于是每换一天都要「先空、再填」地重建一次（台账 N7 记的那个「切日期必然闪一次空态」，
     * 也是滑动落定那一下的重活）。现在 DayView 用固定 key 复用同一个实例，换日期只是**换一条
     * 订阅**；StateFlow 会把上一天那份留到新数据到达，不闪空、也不重建整块。
     */
    private val dayDate = MutableStateFlow((collection as? TaskCollection.DayView)?.date)

    /**
     * 打开态的列表里，一串重复任务只露**最近那一条还没做完的**（滴答清单也是这样）：
     * 库里的每一次都还是各自独立的行，视图只是把同串的收拢成一个。已完成的视图不过
     * 这道手，历次完成各看各的。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<Task>> = when (collection) {
        TaskCollection.Inbox -> repository.observeInboxTasks().nearestPerSeries()
        is TaskCollection.ListDetail ->
            repository.observeOpenTasksInList(collection.listId).nearestPerSeries()

        TaskCollection.Completed -> repository.observeCompletedTasks()
        TaskCollection.Trash -> repository.observeDeletedTasks()
        TaskCollection.Today -> repository.observeTodayTasks().nearestPerSeries()
        TaskCollection.Future -> repository.observeFutureTasks().nearestPerSeries()
        TaskCollection.Overdue -> repository.observeOverdueTasks().nearestPerSeries()
        is TaskCollection.DayView -> dayDate.filterNotNull().flatMapLatest { date ->
            repository.observeOpenTasksCovering(date).nearestPerSeries()
        }

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
    @OptIn(ExperimentalCoroutinesApi::class)
    val completedTasks: StateFlow<List<Task>> = when (collection) {
        TaskCollection.Inbox -> repository.observeCompletedInboxTasks()
        is TaskCollection.ListDetail -> repository.observeCompletedTasksInList(collection.listId)
        TaskCollection.Today -> repository.observeCompletedTasksToday()
        is TaskCollection.DayView -> dayDate.filterNotNull().flatMapLatest { date ->
            repository.observeCompletedTasksOn(date)
        }

        TaskCollection.Completed,
        TaskCollection.Trash,
        TaskCollection.Future,
        TaskCollection.Overdue,
        TaskCollection.None -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 日历网格下面换了一天：只切订阅，实例与已经画出来的那一份都留着。 */
    fun showDate(date: LocalDate) {
        if (dayDate.value != date) dayDate.value = date
    }

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

/** 一串重复任务只留最近那条未完成的；不重复的任务原样透传。 */
private fun Flow<List<Task>>.nearestPerSeries(): Flow<List<Task>> =
    map { nearestOpenOccurrencePerSeries(it) }
