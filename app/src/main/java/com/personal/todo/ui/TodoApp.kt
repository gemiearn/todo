package com.personal.todo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.todo.R
import com.personal.todo.data.CrashLog
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.FocusSettingsStore
import com.personal.todo.data.HabitRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.ThemeChoice
import com.personal.todo.data.ViewSettingsStore
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.TaskFilter
import com.personal.todo.model.TaskSort
import com.personal.todo.model.filterByRange
import com.personal.todo.model.filterTasks
import com.personal.todo.model.sortTasks
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.AppShellViewModel
import com.personal.todo.viewmodel.FocusViewModel
import com.personal.todo.viewmodel.HabitViewModel
import com.personal.todo.viewmodel.TagViewModel
import com.personal.todo.viewmodel.TaskCollection
import com.personal.todo.viewmodel.TaskCollectionViewModel
import com.personal.todo.viewmodel.TaskDetailViewModel
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- routes
//
// Phase 1 uses simple state based navigation (allowed by the spec) instead of
// pulling in a navigation library: the route is a string and a small stack is
// kept in saveable state, so rotation does not lose the current screen.
//
// The four bottom bar tabs are the roots of the app (see [HomeTab]); every
// other route below is pushed on top of the tab the user came from.

private const val ROUTE_INBOX = "inbox"
private const val ROUTE_COMPLETED = "completed"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_LIST = "list"
private const val ROUTE_FUTURE = "future"
private const val ROUTE_OVERDUE = "overdue"
private const val ROUTE_STATS = "stats"
private const val ROUTE_CRASH = "crash"
private const val ROUTE_TAGS = "tags"
private const val ROUTE_HABITS = "habits"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TASK = "task"
private const val ROUTE_EDIT = "edit"

private fun listRoute(listId: Long) = "$ROUTE_LIST/$listId"

private fun taskRoute(taskId: Long) = "$ROUTE_TASK/$taskId"

private fun editTaskRoute(taskId: Long) = "$ROUTE_EDIT/$taskId"

/**
 * The pushed-on-top stack of routes.
 *
 * `internal` on purpose: the smoke tests compose the shell directly, and a
 * screen that is only reachable through a chain of private calls can never be
 * composed in a test.
 */
internal class NavStack(private val routes: SnapshotStateList<String>) {
    /**
     * Bumped on every push. The view model store keeps the view model of a
     * previous visit, so a screen that is opened again must be given a fresh one:
     * the editor would otherwise come back still holding the "already saved" flag
     * and pop itself the moment it is composed.
     */
    private val visits = mutableIntStateOf(0)

    val visit: Int get() = visits.intValue

    val current: String get() = routes.last()

    fun push(route: String) {
        routes.add(route)
        visits.intValue++
    }

    fun pop() {
        if (routes.size > 1) routes.removeAt(routes.lastIndex)
    }

    /** Switches the root view without keeping the previous view in the back stack. */
    fun resetTo(route: String) {
        routes.clear()
        routes.add(route)
    }

    fun canPop(): Boolean = routes.size > 1
}

private val NavStackSaver: Saver<SnapshotStateList<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { mutableStateListOf<String>().apply { addAll(it) } }
)

@Composable
internal fun rememberNavStack(): NavStack {
    val routes = rememberSaveable(saver = NavStackSaver) { mutableStateListOf(HomeTab.Today.route) }
    return remember(routes) { NavStack(routes) }
}

// ---------------------------------------------------------------- app root

@Composable
fun TodoApp(
    repository: TaskRepository,
    focusRepository: FocusRepository,
    habitRepository: HabitRepository,
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    onThemeChoiceChange: (ThemeChoice) -> Unit = {},
    colorStyle: ColorStyle = ColorStyle.CLASSIC,
    onColorStyleChange: (ColorStyle) -> Unit = {}
) {
    // 冷启动先把重复任务后面两周那几次补上。生成器幂等（同一天不会生成两遍），所以
    // 每次进来跑一遍是安全的。
    LaunchedEffect(Unit) { repository.materializeRecurrences(repository.today()) }

    val nav = rememberNavStack()
    val route = nav.current
    val kind = route.substringBefore('/')
    val argument = route.substringAfter('/', "").toLongOrNull()

    // A crash that happened on a screen the app can no longer open would be
    // unreachable through the UI, so it is shown right here instead: the next
    // launch reports it, and the user can copy it out and dismiss it.
    val context = LocalContext.current
    var crashText by remember { mutableStateOf(CrashLog.read(context)) }
    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.crash_log_title)) },
            text = {
                SelectionContainer(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashLog.clear(context)
                        crashText = null
                    }
                ) {
                    Text(stringResource(R.string.crash_log_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { crashText = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // The day the calendar page is showing. It lives above the screens so
    // opening a task from that day and coming back lands on the same day again.
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(repository.today().toEpochDay()) }

    BackHandler(enabled = nav.canPop()) { nav.pop() }

    // 清单 names are needed by more than one pushed screen - the task detail screen
    // and the focus picker. The shell view model is therefore created once, here,
    // for every route: the same default key hands out the same instance the tab
    // screens create for themselves, so nothing is duplicated.
    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.factory(repository))
    val lists by shellViewModel.lists.collectAsStateWithLifecycle()

    // 标签管理是推入页。它的 view model 在这里无条件建一次（WhileSubscribed 的 flow
    // 没人订阅时不会查库），避免在 when 分支里条件创建把组合槽位错开。统计页按标签筛
    // 也用同一份标签表，所以在这里把它收下来。
    val tagViewModel: TagViewModel = viewModel(factory = TagViewModel.factory(repository))
    val allTags by tagViewModel.tags.collectAsStateWithLifecycle()

    // 习惯页也是推入页（从「更多」进），同样在这里无条件建一次 view model。
    val habitViewModel: HabitViewModel = viewModel(factory = HabitViewModel.factory(habitRepository))

    // 专注 is a root of the bottom bar (今天/日历/四象限/专注/更多), so its view model
    // and settings are created here - once, for every route. It is *not* a pushed
    // route: pushed, it would be a page of its own with no tab strip, and its back
    // arrow would have nothing to pop when it is the stack root.
    val focusSettings = remember(context) { FocusSettingsStore(context) }
    val focusViewModel: FocusViewModel = viewModel(
        key = HomeTab.Focus.route,
        factory = FocusViewModel.factory(focusRepository, repository, focusSettings)
    )

    when (kind) {
        ROUTE_TASK -> {
            val taskId = requireNotNull(argument) { "任务路由缺少任务 id: $route" }
            val detailViewModel: TaskDetailViewModel = viewModel(
                key = "detail-$taskId",
                factory = TaskDetailViewModel.factory(repository, taskId)
            )
            TaskDetailScreen(
                viewModel = detailViewModel,
                lists = lists,
                zone = repository.zone,
                onOpenTask = { nav.push(taskRoute(it)) },
                onEdit = { nav.push(editTaskRoute(taskId)) },
                onBack = { nav.pop() }
            )
        }

        ROUTE_EDIT -> {
            val taskId = requireNotNull(argument) { "编辑任务路由缺少任务 id: $route" }
            val editorViewModel: TaskEditorViewModel = viewModel(
                key = "editor-$route-${nav.visit}",
                factory = TaskEditorViewModel.factory(repository, taskId = taskId, defaultListId = 0L)
            )
            TaskEditorScreen(
                viewModel = editorViewModel,
                onSaved = { nav.pop() },
                onBack = { nav.pop() }
            )
        }

        ROUTE_STATS -> FocusStatsScreen(
            // 走专注页那个 view model 已经收集着的 sessions，不再额外开一条 Room 查询。
            sessions = focusViewModel.sessions,
            zone = focusRepository.zone,
            today = repository.today(),
            // 「按标签筛」的那一行胶囊就是这些标签名。
            tagNames = allTags.map { it.name },
            onBack = { nav.pop() }
        )

        ROUTE_CRASH -> CrashLogScreen(onBack = { nav.pop() })

        ROUTE_TAGS -> TagScreen(viewModel = tagViewModel, onBack = { nav.pop() })

        ROUTE_HABITS -> HabitScreen(viewModel = habitViewModel, onBack = { nav.pop() })

        ROUTE_SETTINGS -> SettingsScreen(
            themeChoice = themeChoice,
            onThemeChoiceChange = onThemeChoiceChange,
            colorStyle = colorStyle,
            onColorStyleChange = onColorStyleChange,
            // 番茄钟参数在专注页自己的卡片里，这里只是把它作为页签切过去。
            onOpenFocus = { nav.resetTo(HomeTab.Focus.route) },
            onOpenStats = { nav.push(ROUTE_STATS) },
            onOpenTags = { nav.push(ROUTE_TAGS) },
            onBack = { nav.pop() }
        )

        else -> MainScreen(
            repository = repository,
            focusViewModel = focusViewModel,
            nav = nav,
            route = route,
            kind = kind,
            argument = argument,
            selectedDate = LocalDate.ofEpochDay(selectedEpochDay),
            onSelectDate = { selectedEpochDay = it.toEpochDay() }
        )
    }
}

/** The view a route shows, or null for the routes that have a screen of their own. */
private fun collectionFor(
    kind: String,
    argument: Long?,
    selectedDate: LocalDate
): TaskCollection? = when (kind) {
    ROUTE_INBOX -> TaskCollection.Inbox
    ROUTE_LIST -> TaskCollection.ListDetail(requireNotNull(argument) { "清单路由缺少清单 id" })
    ROUTE_COMPLETED -> TaskCollection.Completed
    ROUTE_TRASH -> TaskCollection.Trash
    ROUTE_FUTURE -> TaskCollection.Future
    ROUTE_OVERDUE -> TaskCollection.Overdue
    HomeTab.Today.route -> TaskCollection.Today
    HomeTab.Calendar.route -> TaskCollection.DayView(selectedDate)
    else -> null
}

// ------------------------------------------------------------- main screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    repository: TaskRepository,
    focusViewModel: FocusViewModel,
    nav: NavStack,
    route: String,
    kind: String,
    argument: Long?,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 显示范围筛选与日历显示设置存 SharedPreferences：勾过的范围、日历的看法下次进来
    // 还在。列表顶栏和日历 ⋮ 各用一份独立的筛子，互不影响。
    val viewSettings = remember(context) { ViewSettingsStore(context) }
    var listFilter by remember { mutableStateOf(viewSettings.readListFilter()) }
    var calendarFilter by remember { mutableStateOf(viewSettings.readCalendarFilter()) }
    var calendarSettings by remember { mutableStateOf(viewSettings.readCalendarSettings()) }
    var filterPanelOpen by remember { mutableStateOf(false) }
    var calendarOptionsOpen by remember { mutableStateOf(false) }

    // Leaving from a root tab asks once first: a stray back press should not
    // throw the user straight out of the app.
    var exitConfirmOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = !nav.canPop()) { exitConfirmOpen = true }

    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.factory(repository))
    val lists by shellViewModel.lists.collectAsStateWithLifecycle()
    val openTaskCounts by shellViewModel.openTaskCounts.collectAsStateWithLifecycle()
    val taskCounts by shellViewModel.taskCounts.collectAsStateWithLifecycle()
    val tagsByTask by shellViewModel.tagsByTask.collectAsStateWithLifecycle()
    val allTags by shellViewModel.tags.collectAsStateWithLifecycle()
    // 标签筛选要按 taskId 查「它带了哪些标签」，这里把标签映射收成 id 集合。
    val tagIdsByTask = remember(tagsByTask) {
        tagsByTask.mapValues { (_, tags) -> tags.map { it.id }.toSet() }
    }

    // The day the app opened on. It decides which tasks are 今日 and which are
    // 逾期; both are read-only views, so nothing here ever moves a due date.
    val today = remember { repository.today() }

    // The view of this route. The calendar day is part of the key, so swiping to
    // another month loads that day instead of reusing the old query.
    //
    // Every composable call below runs for EVERY route, even on the routes that
    // have no collection of their own (更多 / 四象限). A @Composable call that is
    // taken on one route and skipped on another shifts the composition slot
    // table, and the next rememberSaveable then reads a slot that belongs to
    // another call - which is the crash the phone reported:
    // "LifecycleRegistry cannot be cast to SaveableHolder" in rememberSaveable.
    val collection = collectionFor(kind, argument, selectedDate) ?: TaskCollection.None
    val collectionKey = when (collection) {
        is TaskCollection.DayView -> "collection-day-${collection.date.toEpochDay()}"
        else -> "collection-$route"
    }
    val collectionViewModel: TaskCollectionViewModel =
        viewModel(key = collectionKey, factory = TaskCollectionViewModel.factory(repository, collection))
    val tasks by collectionViewModel.tasks.collectAsStateWithLifecycle()
    val completedTasks by collectionViewModel.completedTasks.collectAsStateWithLifecycle()
    val subtaskCounts by collectionViewModel.subtaskCounts.collectAsStateWithLifecycle()

    // 搜索与排序 (spec section 4.4). Both are pure views over the rows the
    // collection already returned, so the filter of the current view and the
    // soft delete rule keep applying - a search can never reveal a trashed task.
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(TaskSort.DEFAULT) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // 日历 shows time in four ways and can lay a single day out as a timeline.
    // Both are ways of looking at the same rows, so they are remembered here and
    // never touch a task.
    var calendarView by rememberSaveable { mutableStateOf(CalendarView.MONTH) }
    var timelineOn by rememberSaveable { mutableStateOf(false) }

    // The calendar reads its days through this. It is remembered rather than built
    // inline at the call: a lambda rebuilt on every recomposition is a new
    // `remember` key inside the grid, which restarts the query and blinks the task
    // dots away.
    // The calendar reads open *and* finished tasks and drops the finished ones
    // itself when 「显示已完成」 is off, so one query covers both settings.
    val calendarTasksInRange = remember(repository) {
        { start: LocalDate, end: LocalDate -> repository.observeTasksSpanning(start, end) }
    }

    // 显示范围筛选：列表顶栏和日历 ⋮ 各用一份筛子，各自只作用在自己那条路由上。
    // 维度之间「且」、维度内部「或」的口径都在 [filterByRange] 里。
    val onCalendarRoute = kind == HomeTab.Calendar.route
    val activeFilter = if (onCalendarRoute) calendarFilter else listFilter
    val rangeFiltered = remember(tasks, activeFilter, tagIdsByTask) {
        filterByRange(tasks, activeFilter) { tagIdsByTask[it].orEmpty() }
    }
    val rangeFilteredCompleted = remember(completedTasks, activeFilter, tagIdsByTask) {
        filterByRange(completedTasks, activeFilter) { tagIdsByTask[it].orEmpty() }
    }

    val visibleTasks = remember(rangeFiltered, searchQuery, sort) {
        sortTasks(filterTasks(rangeFiltered, searchQuery), sort)
    }
    val visibleCompletedTasks = remember(rangeFilteredCompleted, searchQuery, sort) {
        sortTasks(filterTasks(rangeFilteredCompleted, searchQuery), sort)
    }

    var showNewListDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TaskList?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskList?>(null) }
    var pendingTaskDelete by remember { mutableStateOf<Task?>(null) }

    // 新建任务走底部弹层（照「创建任务UI-2」），不再是一页推入的路由。每打开一次就
    // 换一个 view model key，免得复用上一次那个已经置上 saved 标志的实例、一进来就把
    // 自己弹回去。
    var newTaskSheetOpen by rememberSaveable { mutableStateOf(false) }
    var newTaskSheetVisit by rememberSaveable { mutableIntStateOf(0) }
    var newTaskSheetListId by rememberSaveable { mutableLongStateOf(0L) }
    val openNewTaskSheet: (Long) -> Unit = { listId ->
        newTaskSheetListId = listId
        newTaskSheetVisit++
        newTaskSheetOpen = true
    }

    val deletedMessage = stringResource(R.string.message_task_deleted)
    val completedMessage = stringResource(R.string.message_task_completed)
    val listDeletedMessage = stringResource(R.string.message_list_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val filterDescription = stringResource(R.string.filter_action)
    val statsDescription = stringResource(R.string.focus_stats_open)

    // Deleting a task always soft deletes it and offers undo. Parents that
    // still have subtasks ask once before, because the subtasks go to the trash
    // with them (spec section 4.3).
    val deleteTaskWithUndo: (Task) -> Unit = { task ->
        collectionViewModel?.delete(task.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) collectionViewModel?.restore(task.id)
        }
    }

    // Finishing a task by the checkbox or by a swipe offers undo as well, and
    // that undo is an explicit reopen - nothing reopens a task on its own.
    val completeWithUndo: (Task) -> Unit = { task ->
        collectionViewModel?.complete(task.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(completedMessage, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) collectionViewModel?.reopen(task.id)
        }
    }

    val onOpenTask: (Long) -> Unit = { taskId -> nav.push(taskRoute(taskId)) }
    val onReopen: (Task) -> Unit = { collectionViewModel?.reopen(it.id) }
    val onRestore: (Task) -> Unit = { collectionViewModel?.restore(it.id) }
    val requestDelete: (Task) -> Unit = { task ->
        if ((subtaskCounts[task.id] ?: 0) > 0) pendingTaskDelete = task else deleteTaskWithUndo(task)
    }

    // The inbox is pinned first, so only user lists can be reordered.
    val currentList = (collection as? TaskCollection.ListDetail)
        ?.let { detail -> lists.firstOrNull { it.id == detail.listId } }
    val userLists = lists.filter { !it.isInbox }
    val currentUserListIndex = currentList?.let { list -> userLists.indexOfFirst { it.id == list.id } } ?: -1

    val title = when (kind) {
        ROUTE_INBOX -> stringResource(R.string.list_inbox_name)
        ROUTE_LIST -> currentList?.name.orEmpty()
        ROUTE_COMPLETED -> stringResource(R.string.nav_completed)
        ROUTE_TRASH -> stringResource(R.string.nav_trash)
        ROUTE_FUTURE -> stringResource(R.string.nav_future)
        ROUTE_OVERDUE -> stringResource(R.string.nav_overdue)
        HomeTab.Today.route -> stringResource(R.string.nav_today)
        HomeTab.Quadrant.route -> stringResource(R.string.nav_quadrant)
        HomeTab.Focus.route -> stringResource(R.string.focus_title)
        else -> stringResource(R.string.nav_more)
    }
    val emptyTextRes = when (kind) {
        ROUTE_INBOX -> R.string.empty_inbox
        ROUTE_LIST -> R.string.empty_list
        ROUTE_COMPLETED -> R.string.empty_completed
        ROUTE_TRASH -> R.string.empty_trash
        ROUTE_FUTURE -> R.string.empty_future
        ROUTE_OVERDUE -> R.string.empty_overdue
        HomeTab.Today.route -> R.string.empty_today
        else -> R.string.empty_day
    }
    // While a search is running, "nothing here" means "nothing matched".
    val emptyText = if (searchQuery.isNotBlank()) {
        stringResource(R.string.empty_search)
    } else {
        stringResource(emptyTextRes)
    }

    // New tasks go to the inbox from the smart views, and to the list whose page
    // is open. The 更多 page has no list context, so it offers no floating button.
    val targetListId: Long? = when (kind) {
        ROUTE_INBOX -> shellViewModel.inboxId
        ROUTE_LIST -> argument
        HomeTab.Today.route, HomeTab.Calendar.route, HomeTab.Quadrant.route -> shellViewModel.inboxId
        else -> null
    }
    val selectedTab = HomeTab.entries.firstOrNull { it.route == kind }
    val showsCalendarHeader = kind == HomeTab.Calendar.route
    // 专注 is a page of its own: the shell gives it a title, but not the search /
    // sort / list actions that belong to the task screens.
    val showsFocusChrome = kind == HomeTab.Focus.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // 抽屉不铺满全屏：只占屏幕约六成，拉出来时原来的界面还露在外面一点，
            // 一眼看得出「这是一层盖上去的面板」，也留了半边随时能点回去。
            ModalDrawerSheet(
                modifier = Modifier.width(
                    LocalConfiguration.current.screenWidthDp.dp * DrawerWidthFraction
                )
            ) {
                ListsDrawer(
                    lists = lists,
                    openTaskCounts = openTaskCounts,
                    currentRoute = route,
                    onSelect = { target ->
                        nav.resetTo(target)
                        scope.launch { drawerState.close() }
                    },
                    onNewList = { showNewListDialog = true }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (!showsCalendarHeader) {
                    TopAppBar(
                        title = {
                            if (searchActive && !showsFocusChrome) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.search_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(title)
                            }
                        },
                        navigationIcon = {
                            if (searchActive && !showsFocusChrome) {
                                IconButton(
                                    onClick = {
                                        searchActive = false
                                        searchQuery = ""
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.action_close_search)
                                    )
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.action_open_lists)
                                    )
                                }
                            }
                        },
                        actions = {
                            // 专注页的统计入口放在右上角：页签根的顶栏是外壳画的，所以
                            // 由外壳在这里补一个图标。
                            if (!searchActive && showsFocusChrome) {
                                IconButton(
                                    onClick = { nav.push(ROUTE_STATS) },
                                    modifier = Modifier
                                        .testTag(FocusStatsEntryTag)
                                        .semantics { contentDescription = statsDescription }
                                ) {
                                    StatsGlyph(tint = LocalTodoColors.current.dateMuted)
                                }
                            }
                            // While the search field is open it owns the whole bar.
                            if (!searchActive && !showsFocusChrome) {
                                // 显示范围筛选：只在有任务列表的路由上出现；日历用自己的
                                // ⋮，更多 / 四象限没有列表可筛。
                                if (!showsCalendarHeader && collection != TaskCollection.None) {
                                    IconButton(
                                        onClick = { filterPanelOpen = true },
                                        modifier = Modifier.semantics {
                                            contentDescription = filterDescription
                                        }
                                    ) {
                                        FilterGlyph(tint = LocalTodoColors.current.dateMuted)
                                    }
                                }
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.action_search)
                                    )
                                }
                                Box {
                                    IconButton(onClick = { sortMenuOpen = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.action_sort)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuOpen,
                                        onDismissRequest = { sortMenuOpen = false }
                                    ) {
                                        TaskSort.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(sortLabelRes(mode))) },
                                                onClick = {
                                                    sort = mode
                                                    sortMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (!searchActive && !showsFocusChrome && currentList != null && !currentList.isInbox) {
                                if (currentUserListIndex > 0) {
                                    IconButton(onClick = { shellViewModel.moveList(currentList.id, -1) }) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up)
                                        )
                                    }
                                }
                                if (currentUserListIndex in 0 until userLists.lastIndex) {
                                    IconButton(onClick = { shellViewModel.moveList(currentList.id, 1) }) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down)
                                        )
                                    }
                                }
                                IconButton(onClick = { renameTarget = currentList }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.action_rename)
                                    )
                                }
                                IconButton(onClick = { deleteTarget = currentList }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete_list)
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                TodoBottomBar(
                    selected = selectedTab,
                    todayDayOfMonth = today.dayOfMonth,
                    onSelect = { nav.resetTo(it.route) }
                )
            },
            floatingActionButton = {
                val listId = targetListId
                if (listId != null) {
                    FloatingActionButton(onClick = { openNewTaskSheet(listId) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_new_task)
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            when (kind) {
                HomeTab.Calendar.route -> Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    CalendarScreen(
                        selectedDate = selectedDate,
                        today = today,
                        view = calendarView,
                        onSelectView = { calendarView = it },
                        timelineOn = timelineOn,
                        onToggleTimeline = { timelineOn = !timelineOn },
                        tasksInRange = calendarTasksInRange,
                        displaySettings = calendarSettings,
                        filter = calendarFilter,
                        tagIdsByTask = tagIdsByTask,
                        onOpenDisplayOptions = { calendarOptionsOpen = true },
                        onSelectDate = onSelectDate,
                        onOpenLists = { scope.launch { drawerState.open() } },
                        onOpenTask = onOpenTask,
                        // Only the month view leaves room under the grid for a day
                        // panel; the 周 / 三日 views carry their entries themselves.
                        modifier = if (calendarView == CalendarView.MONTH) Modifier else Modifier.weight(1f)
                    )
                    if (calendarView == CalendarView.MONTH) {
                        // The toggle picks how the selected day is laid out - the
                        // plain list, or the same tasks on a time axis.
                        if (timelineOn) {
                            DayTimelineScreen(
                                tasks = visibleTasks,
                                subtaskCounts = subtaskCounts,
                                zone = repository.zone,
                                onOpenTask = onOpenTask,
                                onComplete = completeWithUndo,
                                onReopen = onReopen,
                                onDelete = requestDelete,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            TaskCollectionContent(
                                collection = collection,
                                tasks = visibleTasks,
                                completedTasks = visibleCompletedTasks,
                                subtaskCounts = subtaskCounts,
                                tagsByTask = tagsByTask,
                                emptyText = emptyText,
                                zone = repository.zone,
                                onOpenTask = onOpenTask,
                                onComplete = completeWithUndo,
                                onReopen = onReopen,
                                onDelete = requestDelete,
                                onRestore = onRestore,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HomeTab.Focus.route -> FocusScreen(
                    viewModel = focusViewModel,
                    lists = lists,
                    // 每条任务身上的标签，开始专注时随 session 一起快照下来（统计页按标签筛）。
                    taskTags = tagsByTask,
                    // A tab root: the shell draws the top bar and the tab strip.
                    onBack = null,
                    modifier = Modifier.padding(innerPadding)
                )

                HomeTab.Quadrant.route -> QuadrantScreen(
                    openTasks = remember { repository.observeAllOpenTasks() },
                    completedTasks = remember { repository.observeCompletedTasks() },
                    zone = repository.zone,
                    // 四象限只读：决定优先级的地方，不给打勾也不给滑动完成。
                    onOpenTask = onOpenTask,
                    modifier = Modifier.padding(innerPadding)
                )

                HomeTab.More.route -> MoreScreen(
                    lists = lists,
                    openTaskCounts = openTaskCounts,
                    // 专注 and 崩溃日志 are screens of their own with a back
                    // button, so they are pushed on top; the other entries are
                    // root views.
                    onOpen = { target ->
                        // 崩溃日志 / 统计 / 标签管理 / 设置 are pushed pages with a
                        // back arrow: they open on top of where the user is. Every
                        // tab target resets the stack instead, which is what a
                        // bottom bar is expected to do.
                        if (target == ROUTE_CRASH || target == ROUTE_STATS ||
                            target == ROUTE_TAGS || target == ROUTE_HABITS ||
                            target == ROUTE_SETTINGS
                        ) {
                            nav.push(target)
                        } else {
                            nav.resetTo(target)
                        }
                    },
                    onNewList = { showNewListDialog = true },
                    modifier = Modifier.padding(innerPadding)
                )

                else -> TaskCollectionContent(
                    collection = collection,
                    tasks = visibleTasks,
                    completedTasks = visibleCompletedTasks,
                    subtaskCounts = subtaskCounts,
                    tagsByTask = tagsByTask,
                    emptyText = emptyText,
                    zone = repository.zone,
                    onOpenTask = onOpenTask,
                    onComplete = completeWithUndo,
                    onReopen = onReopen,
                    onDelete = requestDelete,
                    onRestore = onRestore,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (exitConfirmOpen) {
        AlertDialog(
            onDismissRequest = { exitConfirmOpen = false },
            title = { Text(stringResource(R.string.dialog_exit_title)) },
            text = { Text(stringResource(R.string.dialog_exit_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exitConfirmOpen = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text(stringResource(R.string.action_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { exitConfirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewListDialog) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_list_title),
            label = stringResource(R.string.dialog_list_name_label),
            initialValue = "",
            confirmLabel = stringResource(R.string.action_new_list),
            onConfirm = { name ->
                shellViewModel.createList(name)
                showNewListDialog = false
            },
            onDismiss = { showNewListDialog = false }
        )
    }

    renameTarget?.let { list ->
        TextInputDialog(
            title = stringResource(R.string.dialog_rename_list_title),
            label = stringResource(R.string.dialog_list_name_label),
            initialValue = list.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                shellViewModel.renameList(list.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { list ->
        val taskCount = taskCounts[list.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_list_title)) },
            text = { Text(stringResource(R.string.dialog_delete_list_message, list.name, taskCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        shellViewModel.deleteList(list.id)
                        deleteTarget = null
                        // The tasks of that list now live in the inbox.
                        if (route == listRoute(list.id)) nav.resetTo(ROUTE_INBOX)
                        scope.launch { snackbarHostState.showSnackbar(listDeletedMessage) }
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete_list_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingTaskDelete?.let { task ->
        DeleteTaskConfirmDialog(
            taskTitle = task.title,
            subtaskCount = subtaskCounts[task.id] ?: 0,
            onConfirm = {
                deleteTaskWithUndo(task)
                pendingTaskDelete = null
            },
            onDismiss = { pendingTaskDelete = null }
        )
    }

    if (newTaskSheetOpen) {
        val editorViewModel: TaskEditorViewModel = viewModel(
            key = "editor-sheet-$newTaskSheetVisit",
            factory = TaskEditorViewModel.factory(
                repository = repository,
                taskId = null,
                defaultListId = newTaskSheetListId
            )
        )
        NewTaskSheet(
            viewModel = editorViewModel,
            onSaved = { newTaskSheetOpen = false },
            onDismiss = { newTaskSheetOpen = false }
        )
    }

    // 列表顶栏的「显示范围」筛选面板。改一次就落盘一次，勾过的范围下次进来还在。
    if (filterPanelOpen) {
        FilterSheet(
            title = stringResource(R.string.filter_title),
            lists = lists,
            tags = allTags,
            filter = listFilter,
            onFilterChange = {
                listFilter = it
                viewSettings.writeListFilter(it)
            },
            onDismiss = { filterPanelOpen = false }
        )
    }

    // 日历 ⋮ 面板：同一个筛选面板 + 下面接一段「显示设置」。
    if (calendarOptionsOpen) {
        FilterSheet(
            title = stringResource(R.string.calendar_display_options),
            lists = lists,
            tags = allTags,
            filter = calendarFilter,
            onFilterChange = {
                calendarFilter = it
                viewSettings.writeCalendarFilter(it)
            },
            onDismiss = { calendarOptionsOpen = false },
            extra = {
                DisplaySettingsSection(
                    settings = calendarSettings,
                    onChange = {
                        calendarSettings = it
                        viewSettings.writeCalendarSettings(it)
                    }
                )
            }
        )
    }
}

// --------------------------------------------------------------- 更多 page

/**
 * The 更多 tab: everything that does not need a tab of its own. It lists the
 * finished tasks, the trash and every 清单 with its open task count, and it can
 * create a new 清单 - the same entries the drawer offers, for the tabs that do
 * not open the drawer.
 */
@Composable
internal fun MoreScreen(
    lists: List<TaskList>,
    openTaskCounts: Map<Long, Int>,
    onOpen: (String) -> Unit,
    onNewList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        MoreEntry(
            label = stringResource(R.string.more_entry_settings),
            onClick = { onOpen(ROUTE_SETTINGS) }
        )
        MoreEntry(
            label = stringResource(R.string.more_entry_focus),
            // Switching to the 专注 tab, not pushing it: pushing would put a page with
            // no tab strip on top of the stack and leave its back arrow with nothing
            // to pop.
            onClick = { onOpen(HomeTab.Focus.route) }
        )

        MoreEntry(
            label = stringResource(R.string.more_entry_stats),
            onClick = { onOpen(ROUTE_STATS) }
        )
        MoreEntry(
            label = stringResource(R.string.more_entry_tags),
            onClick = { onOpen(ROUTE_TAGS) }
        )
        MoreEntry(
            label = stringResource(R.string.more_entry_habits),
            onClick = { onOpen(ROUTE_HABITS) }
        )
        MoreEntry(
            label = stringResource(R.string.nav_completed),
            onClick = { onOpen(ROUTE_COMPLETED) }
        )
        MoreEntry(
            label = stringResource(R.string.nav_trash),
            onClick = { onOpen(ROUTE_TRASH) }
        )
        MoreEntry(
            label = stringResource(R.string.crash_log_title),
            onClick = { onOpen(ROUTE_CRASH) }
        )

        SectionHeader(stringResource(R.string.drawer_title))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lists, key = { it.id }) { list ->
                MoreEntry(
                    label = list.name,
                    trailing = openTaskCounts[list.id] ?: 0,
                    onClick = { onOpen(listRoute(list.id)) }
                )
            }
        }

        TextButton(onClick = onNewList, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.action_new_list))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun MoreEntry(label: String, trailing: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null && trailing > 0) {
            Text(
                text = trailing.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTodoColors.current.dateMuted
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalTodoColors.current.dateMuted
        )
    }
}

// ---------------------------------------------------------------- the drawer

/** 抽屉占屏幕宽度的比例：拉出来只盖住约六成，原来的界面还露在外面一点。 */
private const val DrawerWidthFraction = 0.6f

@Composable
private fun ListsDrawer(
    lists: List<TaskList>,
    openTaskCounts: Map<Long, Int>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    onNewList: () -> Unit
) {
    val inboxId = lists.firstOrNull { it.isInbox }?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // 智能清单: the date driven views of spec section 4.1. They are queries,
        // not folders - nothing here moves a task or a date.
        DrawerRow(
            label = stringResource(R.string.nav_today),
            selected = currentRoute == HomeTab.Today.route,
            onClick = { onSelect(HomeTab.Today.route) }
        )
        DrawerRow(
            label = stringResource(R.string.nav_future),
            selected = currentRoute == ROUTE_FUTURE,
            onClick = { onSelect(ROUTE_FUTURE) }
        )
        DrawerRow(
            label = stringResource(R.string.nav_overdue),
            selected = currentRoute == ROUTE_OVERDUE,
            onClick = { onSelect(ROUTE_OVERDUE) }
        )

        Spacer(Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = ROUTE_INBOX) {
                DrawerRow(
                    label = stringResource(R.string.list_inbox_name),
                    selected = currentRoute == ROUTE_INBOX,
                    badge = openTaskCounts[inboxId ?: 0L] ?: 0,
                    onClick = { onSelect(ROUTE_INBOX) }
                )
            }
            items(lists.filter { !it.isInbox }, key = { it.id }) { list ->
                DrawerRow(
                    label = list.name,
                    selected = currentRoute == listRoute(list.id),
                    badge = openTaskCounts[list.id] ?: 0,
                    onClick = { onSelect(listRoute(list.id)) }
                )
            }
        }

        DrawerRow(
            label = stringResource(R.string.nav_completed),
            selected = currentRoute == ROUTE_COMPLETED,
            onClick = { onSelect(ROUTE_COMPLETED) }
        )
        // 回收站 deliberately has no entry here: it lives in 更多, so the drawer
        // stays a plain list of lists (spec section 4.2 still requires the trash
        // itself, and nothing about it is removed).
        TextButton(onClick = onNewList, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.action_new_list))
        }
    }
}

/**
 * 抽屉里的一行。
 *
 * 不用 material3 的 NavigationDrawerItem：它自带 56dp 的最小高度和一整套竖向内边距，
 * 清单多了以后一屏只能看见七八行。这里换成精简行（整行约 36dp），配合半屏宽的抽屉，
 * 一屏能多看几行，也更接近用户要的「紧凑一点」。
 */
@Composable
private fun DrawerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int = 0
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge > 0) {
            Text(
                text = stringResource(R.string.list_task_count, badge),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted,
                maxLines = 1
            )
        }
    }
}

/** The label of one sort mode in the top bar menu. */
private fun sortLabelRes(sort: TaskSort): Int = when (sort) {
    TaskSort.DEFAULT -> R.string.sort_default
    TaskSort.PRIORITY -> R.string.sort_priority
    TaskSort.CREATED -> R.string.sort_created
}
