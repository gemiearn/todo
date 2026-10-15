package com.personal.todo.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.todo.R
import com.personal.todo.data.CrashLog
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.TaskSort
import com.personal.todo.model.filterTasks
import com.personal.todo.model.sortTasks
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.AppShellViewModel
import com.personal.todo.viewmodel.FocusViewModel
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
private const val ROUTE_FOCUS = "focus"
private const val ROUTE_CRASH = "crash"
private const val ROUTE_TASK = "task"
private const val ROUTE_NEW = "new"
private const val ROUTE_EDIT = "edit"

private fun listRoute(listId: Long) = "$ROUTE_LIST/$listId"

private fun taskRoute(taskId: Long) = "$ROUTE_TASK/$taskId"

private fun newTaskRoute(listId: Long) = "$ROUTE_NEW/$listId"

private fun editTaskRoute(taskId: Long) = "$ROUTE_EDIT/$taskId"

/**
 * The pushed-on-top stack of routes.
 *
 * `internal` on purpose: the smoke tests compose the shell directly, and a
 * screen that is only reachable through a chain of private calls can never be
 * composed in a test.
 */
internal class NavStack(private val routes: SnapshotStateList<String>) {
    val current: String get() = routes.last()

    fun push(route: String) = routes.add(route)

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
fun TodoApp(repository: TaskRepository, focusRepository: FocusRepository) {
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

    when (kind) {
        ROUTE_TASK -> {
            val taskId = requireNotNull(argument) { "任务路由缺少任务 id: $route" }
            val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.factory(repository))
            val lists by shellViewModel.lists.collectAsStateWithLifecycle()
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

        ROUTE_NEW -> {
            val listId = requireNotNull(argument) { "新建任务路由缺少清单 id: $route" }
            val editorViewModel: TaskEditorViewModel = viewModel(
                key = "editor-$route",
                factory = TaskEditorViewModel.factory(repository, taskId = null, defaultListId = listId)
            )
            TaskEditorScreen(
                viewModel = editorViewModel,
                onSaved = { nav.pop() },
                onBack = { nav.pop() }
            )
        }

        ROUTE_EDIT -> {
            val taskId = requireNotNull(argument) { "编辑任务路由缺少任务 id: $route" }
            val editorViewModel: TaskEditorViewModel = viewModel(
                key = "editor-$route",
                factory = TaskEditorViewModel.factory(repository, taskId = taskId, defaultListId = 0L)
            )
            TaskEditorScreen(
                viewModel = editorViewModel,
                onSaved = { nav.pop() },
                onBack = { nav.pop() }
            )
        }

        ROUTE_FOCUS -> {
            val focusViewModel: FocusViewModel = viewModel(
                key = ROUTE_FOCUS,
                factory = FocusViewModel.factory(focusRepository, repository)
            )
            FocusScreen(viewModel = focusViewModel, onBack = { nav.pop() })
        }

        ROUTE_CRASH -> CrashLogScreen(onBack = { nav.pop() })

        else -> MainScreen(
            repository = repository,
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

    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.factory(repository))
    val lists by shellViewModel.lists.collectAsStateWithLifecycle()
    val openTaskCounts by shellViewModel.openTaskCounts.collectAsStateWithLifecycle()
    val taskCounts by shellViewModel.taskCounts.collectAsStateWithLifecycle()

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

    val visibleTasks = remember(tasks, searchQuery, sort) {
        sortTasks(filterTasks(tasks, searchQuery), sort)
    }
    val visibleCompletedTasks = remember(completedTasks, searchQuery, sort) {
        sortTasks(filterTasks(completedTasks, searchQuery), sort)
    }

    var showNewListDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TaskList?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskList?>(null) }
    var pendingTaskDelete by remember { mutableStateOf<Task?>(null) }

    val deletedMessage = stringResource(R.string.message_task_deleted)
    val completedMessage = stringResource(R.string.message_task_completed)
    val listDeletedMessage = stringResource(R.string.message_list_deleted)
    val undoLabel = stringResource(R.string.action_undo)

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
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
                            if (searchActive) {
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
                            if (searchActive) {
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
                            // While the search field is open it owns the whole bar.
                            if (!searchActive) {
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
                            if (!searchActive && currentList != null && !currentList.isInbox) {
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
                    FloatingActionButton(onClick = { nav.push(newTaskRoute(listId)) }) {
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
                        tasksInRange = { start, end -> repository.observeOpenTasksInRange(start, end) },
                        onSelectDate = onSelectDate,
                        onOpenLists = { scope.launch { drawerState.open() } }
                    )
                    TaskCollectionContent(
                        collection = collection,
                        tasks = visibleTasks,
                        completedTasks = visibleCompletedTasks,
                        subtaskCounts = subtaskCounts,
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

                HomeTab.Quadrant.route -> QuadrantScreen(
                    openTasks = remember { repository.observeAllOpenTasks() },
                    completedTasks = remember { repository.observeCompletedTasks() },
                    zone = repository.zone,
                    onOpenTask = onOpenTask,
                    onComplete = completeWithUndo,
                    onReopen = onReopen,
                    modifier = Modifier.padding(innerPadding)
                )

                HomeTab.More.route -> MoreScreen(
                    lists = lists,
                    openTaskCounts = openTaskCounts,
                    // 专注 and 崩溃日志 are screens of their own with a back
                    // button, so they are pushed on top; the other entries are
                    // root views.
                    onOpen = { target ->
                        if (target == ROUTE_FOCUS || target == ROUTE_CRASH) {
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
            label = stringResource(R.string.focus_title),
            onClick = { onOpen(ROUTE_FOCUS) }
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
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        )

        // 智能清单: the date driven views of spec section 4.1. They are queries,
        // not folders - nothing here moves a task or a date.
        SmartListEntry(
            labelRes = R.string.nav_today,
            route = HomeTab.Today.route,
            currentRoute = currentRoute,
            onSelect = onSelect
        )
        SmartListEntry(
            labelRes = R.string.nav_future,
            route = ROUTE_FUTURE,
            currentRoute = currentRoute,
            onSelect = onSelect
        )
        SmartListEntry(
            labelRes = R.string.nav_overdue,
            route = ROUTE_OVERDUE,
            currentRoute = currentRoute,
            onSelect = onSelect
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = ROUTE_INBOX) {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.list_inbox_name)) },
                    badge = { CountBadge(openTaskCounts[inboxId ?: 0L] ?: 0) },
                    selected = currentRoute == ROUTE_INBOX,
                    onClick = { onSelect(ROUTE_INBOX) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            items(lists.filter { !it.isInbox }, key = { it.id }) { list ->
                NavigationDrawerItem(
                    label = { Text(list.name) },
                    badge = { CountBadge(openTaskCounts[list.id] ?: 0) },
                    selected = currentRoute == listRoute(list.id),
                    onClick = { onSelect(listRoute(list.id)) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_completed)) },
            selected = currentRoute == ROUTE_COMPLETED,
            onClick = { onSelect(ROUTE_COMPLETED) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_trash)) },
            selected = currentRoute == ROUTE_TRASH,
            onClick = { onSelect(ROUTE_TRASH) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        TextButton(onClick = onNewList, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.action_new_list))
        }
    }
}

@Composable
private fun SmartListEntry(
    labelRes: Int,
    route: String,
    currentRoute: String,
    onSelect: (String) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(stringResource(labelRes)) },
        selected = currentRoute == route,
        onClick = { onSelect(route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun CountBadge(count: Int) {
    if (count > 0) {
        Text(stringResource(R.string.list_task_count, count))
    }
}

/** The label of one sort mode in the top bar menu. */
private fun sortLabelRes(sort: TaskSort): Int = when (sort) {
    TaskSort.DEFAULT -> R.string.sort_default
    TaskSort.PRIORITY -> R.string.sort_priority
    TaskSort.CREATED -> R.string.sort_created
}
