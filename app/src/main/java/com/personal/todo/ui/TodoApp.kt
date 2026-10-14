package com.personal.todo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.todo.R
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.viewmodel.AppShellViewModel
import com.personal.todo.viewmodel.TaskCollection
import com.personal.todo.viewmodel.TaskCollectionViewModel
import com.personal.todo.viewmodel.TaskDetailViewModel
import com.personal.todo.viewmodel.TaskEditorViewModel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- routes
//
// Phase 1 uses simple state based navigation (allowed by the spec) instead of
// pulling in a navigation library: the route is a string and a small stack is
// kept in saveable state, so rotation does not lose the current screen.

private const val ROUTE_INBOX = "inbox"
private const val ROUTE_COMPLETED = "completed"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_LIST = "list"
private const val ROUTE_TASK = "task"
private const val ROUTE_NEW = "new"
private const val ROUTE_EDIT = "edit"

private fun listRoute(listId: Long) = "$ROUTE_LIST/$listId"

private fun taskRoute(taskId: Long) = "$ROUTE_TASK/$taskId"

private fun newTaskRoute(listId: Long) = "$ROUTE_NEW/$listId"

private fun editTaskRoute(taskId: Long) = "$ROUTE_EDIT/$taskId"

private class NavStack(private val routes: SnapshotStateList<String>) {
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
private fun rememberNavStack(): NavStack {
    val routes = rememberSaveable(saver = NavStackSaver) { mutableStateListOf(ROUTE_INBOX) }
    return remember(routes) { NavStack(routes) }
}

// ---------------------------------------------------------------- app root

@Composable
fun TodoApp(repository: TaskRepository) {
    val nav = rememberNavStack()
    val route = nav.current
    val kind = route.substringBefore('/')
    val argument = route.substringAfter('/', "").toLongOrNull()

    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.factory(repository))
    val lists by shellViewModel.lists.collectAsStateWithLifecycle()
    val openTaskCounts by shellViewModel.openTaskCounts.collectAsStateWithLifecycle()
    val taskCounts by shellViewModel.taskCounts.collectAsStateWithLifecycle()

    BackHandler(enabled = nav.canPop()) { nav.pop() }

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

        else -> {
            val collection = when (kind) {
                ROUTE_LIST -> TaskCollection.ListDetail(requireNotNull(argument) { "清单路由缺少清单 id: $route" })
                ROUTE_COMPLETED -> TaskCollection.Completed
                ROUTE_TRASH -> TaskCollection.Trash
                else -> TaskCollection.Inbox
            }
            val collectionViewModel: TaskCollectionViewModel = viewModel(
                key = "collection-$route",
                factory = TaskCollectionViewModel.factory(repository, collection)
            )
            val tasks by collectionViewModel.tasks.collectAsStateWithLifecycle()
            val subtaskCounts by collectionViewModel.subtaskCounts.collectAsStateWithLifecycle()

            MainScreen(
                nav = nav,
                route = route,
                shellViewModel = shellViewModel,
                lists = lists,
                openTaskCounts = openTaskCounts,
                taskCounts = taskCounts,
                collection = collection,
                tasks = tasks,
                subtaskCounts = subtaskCounts,
                onOpenTask = { nav.push(taskRoute(it)) },
                onToggleComplete = { task ->
                    if (task.completedAt == null) {
                        collectionViewModel.complete(task.id)
                    } else {
                        collectionViewModel.reopen(task.id)
                    }
                },
                onDelete = { collectionViewModel.delete(it.id) },
                onRestore = { collectionViewModel.restore(it.id) }
            )
        }
    }
}

// ------------------------------------------------------------- main screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    nav: NavStack,
    route: String,
    shellViewModel: AppShellViewModel,
    lists: List<TaskList>,
    openTaskCounts: Map<Long, Int>,
    taskCounts: Map<Long, Int>,
    collection: TaskCollection,
    tasks: List<Task>,
    subtaskCounts: Map<Long, Int>,
    onOpenTask: (Long) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onRestore: (Task) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewListDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TaskList?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskList?>(null) }

    val deletedMessage = stringResource(R.string.message_task_deleted)
    val listDeletedMessage = stringResource(R.string.message_list_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    val targetListId: Long? = when (collection) {
        TaskCollection.Inbox -> shellViewModel.inboxId
        is TaskCollection.ListDetail -> collection.listId
        else -> null
    }
    val currentList = (collection as? TaskCollection.ListDetail)
        ?.let { detail -> lists.firstOrNull { it.id == detail.listId } }

    val title = when (collection) {
        TaskCollection.Inbox -> stringResource(R.string.list_inbox_name)
        is TaskCollection.ListDetail -> currentList?.name.orEmpty()
        TaskCollection.Completed -> stringResource(R.string.nav_completed)
        TaskCollection.Trash -> stringResource(R.string.nav_trash)
    }
    val emptyText = when (collection) {
        TaskCollection.Inbox -> stringResource(R.string.empty_inbox)
        is TaskCollection.ListDetail -> stringResource(R.string.empty_list)
        TaskCollection.Completed -> stringResource(R.string.empty_completed)
        TaskCollection.Trash -> stringResource(R.string.empty_trash)
    }

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
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.action_open_lists)
                            )
                        }
                    },
                    actions = {
                        if (currentList != null && !currentList.isInbox) {
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
            },
            floatingActionButton = {
                if (targetListId != null) {
                    FloatingActionButton(onClick = { nav.push(newTaskRoute(targetListId)) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_new_task)
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            TaskCollectionContent(
                collection = collection,
                tasks = tasks,
                subtaskCounts = subtaskCounts,
                emptyText = emptyText,
                onOpenTask = onOpenTask,
                onToggleComplete = onToggleComplete,
                onDelete = { task ->
                    onDelete(task)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) onRestore(task)
                    }
                },
                onRestore = onRestore,
                modifier = Modifier.padding(innerPadding)
            )
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
}

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
private fun CountBadge(count: Int) {
    if (count > 0) {
        Text(stringResource(R.string.list_task_count, count))
    }
}
