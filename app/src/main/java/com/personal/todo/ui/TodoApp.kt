package com.personal.todo.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.todo.R
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.CrashLog
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.DevSettingsStore
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.FocusSettingsStore
import com.personal.todo.data.HabitRepository
import com.personal.todo.data.LauncherIcon
import com.personal.todo.data.ListSummary
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.ThemeChoice
import com.personal.todo.data.ViewSettingsStore
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Habit
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.TaskFilter
import com.personal.todo.model.TaskSort
import com.personal.todo.model.filterByRange
import com.personal.todo.model.filterTasks
import com.personal.todo.model.sortTasks
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import com.personal.todo.viewmodel.AppShellViewModel
import com.personal.todo.viewmodel.FocusViewModel
import com.personal.todo.viewmodel.HabitViewModel
import com.personal.todo.viewmodel.TagViewModel
import com.personal.todo.viewmodel.TaskCollection
import com.personal.todo.viewmodel.TaskCollectionViewModel
import com.personal.todo.viewmodel.TaskDetailViewModel
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
private const val ROUTE_RECORDS = "records"
private const val ROUTE_CRASH = "crash"
private const val ROUTE_DEV = "dev"
private const val ROUTE_TAGS = "tags"
private const val ROUTE_HABITS = "habits"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TASK = "task"
private const val ROUTE_EDIT = "edit"

private fun listRoute(listId: Long) = "$ROUTE_LIST/$listId"

/** 清单页标题：清单有图标就把图标放在名字前面。 */
private fun listTitleWithIcon(list: TaskList): String =
    list.icon?.takeIf { it.isNotBlank() }?.let { "$it ${list.name}" } ?: list.name

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
    onColorStyleChange: (ColorStyle) -> Unit = {},
    backgroundStyle: BackgroundStyle = BackgroundStyle.PLAIN,
    onBackgroundStyleChange: (BackgroundStyle) -> Unit = {},
    launcherIcon: LauncherIcon = LauncherIcon.RING,
    onLauncherIconChange: (LauncherIcon) -> Unit = {}
) {
    // 冷启动先把重复任务后面两周那几次补上。生成器幂等（同一天不会生成两遍），所以
    // 每次进来跑一遍是安全的。跟着「今天」那条流跑：进程活过午夜时要再补一次，否则
    // 新那一天的实例谁也不会生成（原来只在启动时跑一遍）。
    LaunchedEffect(repository) {
        repository.observeToday().collect { repository.materializeRecurrences(it) }
    }

    val nav = rememberNavStack()
    val route = nav.current
    val kind = route.substringBefore('/')
    val argument = route.substringAfter('/', "").toLongOrNull()

    // 上一次的崩溃日志**默认不弹**（用户 2026-10-21：「崩溃日志不要直接显示吧」）。
    //
    // 它现在藏在「设置 → 开发者声明（连点五下）→ 开发者选项」里那个开关后面：只有开发者
    // 自己打开过，下一次启动才会把它弹出来自查；普通用户永远看不到这段吓人的文本。日志
    // 本身照旧由 [CrashLog] 记下来，也照旧能在开发者选项里查看和清除，没有丢东西。
    val context = LocalContext.current
    val devSettings = remember(context) { DevSettingsStore(context) }
    var developerUnlocked by remember { mutableStateOf(devSettings.readDeveloperUnlocked()) }
    var showCrashOnLaunch by remember { mutableStateOf(devSettings.readShowCrashOnLaunch()) }
    var crashText by remember {
        mutableStateOf(if (devSettings.readShowCrashOnLaunch()) CrashLog.read(context) else null)
    }
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

    // 专注记录页编辑「关联任务」时要按那条任务**现在**的标签记快照，所以这里也把标签连线
    // 收下来（与 MainScreen 用的是同一份，StateFlow 共享，不会多查一次库）。
    val tagsByTask by shellViewModel.tagsByTask.collectAsStateWithLifecycle()

    // 习惯页也是推入页（从「更多」进），同样在这里无条件建一次 view model。
    // 现在不需要按当天日期当 key 了：HabitViewModel 的「今天」是跟着钟走的流，跨天时
    // 最右那一格与打卡落点会自己翻页，一个实例长期活着即可。
    val habitViewModel: HabitViewModel = viewModel(
        key = "habits",
        factory = HabitViewModel.factory(habitRepository)
    )

    // 专注 is a root of the bottom bar (今天/日历/四象限/专注/更多), so its view model
    // and settings are created here - once, for every route. It is *not* a pushed
    // route: pushed, it would be a page of its own with no tab strip, and its back
    // arrow would have nothing to pop when it is the stack root.
    val focusSettings = remember(context) { FocusSettingsStore(context) }
    val focusViewModel: FocusViewModel = viewModel(
        key = HomeTab.Focus.route,
        factory = FocusViewModel.factory(
            focusRepository,
            repository,
            focusSettings,
            habitRepository
        )
    )

    // 抽屉里「习惯」那一行要报今天还剩几条没做；走习惯 view model 那条 StateFlow，
    // 不让界面直接收仓库的冷流（裸订阅会在界面销毁后还吊着，关库时炸在测试里）。
    val pendingHabitCount by habitViewModel.pendingTodayCount.collectAsStateWithLifecycle()
    // 专注抽屉的「自动打卡」要列习惯；同一份 StateFlow，不会多查一次库。
    val habitList by habitViewModel.habits.collectAsStateWithLifecycle()

    // 计时器是 App 级状态，落盘（checkpoint）不能挂在专注页上：切到别的页签后专注页就不在
    // 组合里了，那时 App 进后台没人把已经确认的专注时长写下去，进程被杀就丢那一段。挂在
    // 壳层——它对每条路由都在组合里。ON_STOP 只在整个 App 进后台时来，切页签不会误触发。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, focusViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) focusViewModel.checkpoint()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (kind) {
        ROUTE_TASK -> {
            val taskId = requireNotNull(argument) { "任务路由缺少任务 id: $route" }
            val detailViewModel: TaskDetailViewModel = viewModel(
                key = "detail-$taskId",
                factory = TaskDetailViewModel.factory(repository, focusRepository, taskId)
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
            // 「按标签筛」那一行胶囊就是这些标签；页面内部按 id 认人，改名后老记录仍归位。
            tags = allTags,
            onBack = { nav.pop() }
        )

        ROUTE_CRASH -> CrashLogScreen(onBack = { nav.pop() })

        // 开发者选项：设置页连点「开发者声明」五下才出现的入口，进去之后能改这一页的开关
        // （目前只有「启动时弹不弹崩溃日志」），也能把入口重新藏起来。
        ROUTE_DEV -> DevSettingsScreen(
            showCrashOnLaunch = showCrashOnLaunch,
            onShowCrashOnLaunchChange = {
                showCrashOnLaunch = it
                devSettings.writeShowCrashOnLaunch(it)
            },
            onOpenCrashLog = { nav.push(ROUTE_CRASH) },
            onHideEntry = {
                developerUnlocked = false
                devSettings.writeDeveloperUnlocked(false)
                nav.pop()
            },
            onBack = { nav.pop() }
        )

        ROUTE_RECORDS -> {
            // 记录页用专注页那份「还没做完的任务」清单当选择器（与专注页、习惯弹层同一个
            // 口径：一串重复任务只露最近那条），不再另开一条查询。
            val openTasks by focusViewModel.openTasks.collectAsStateWithLifecycle()
            FocusRecordsScreen(
                // 走专注页那个 view model 已经收集着的 sessions，不再额外开一条 Room 查询。
                sessions = focusViewModel.sessions,
                openTasks = openTasks,
                lists = lists,
                taskTags = tagsByTask,
                zone = focusRepository.zone,
                today = repository.today(),
                onUpdate = focusViewModel::updateSession,
                onAdd = focusViewModel::addManualSession,
                onDelete = focusViewModel::deleteSession,
                onBack = { nav.pop() }
            )
        }

        ROUTE_TAGS -> TagScreen(viewModel = tagViewModel, onBack = { nav.pop() })

        ROUTE_HABITS -> HabitScreen(
            viewModel = habitViewModel,
            // 顶栏那个「清单 / 习惯」开关点「清单」＝退回进来之前那一页。
            onSwitchToLists = { nav.pop() },
            onBack = { nav.pop() }
        )

        ROUTE_SETTINGS -> SettingsScreen(
            themeChoice = themeChoice,
            onThemeChoiceChange = onThemeChoiceChange,
            colorStyle = colorStyle,
            onColorStyleChange = onColorStyleChange,
            backgroundStyle = backgroundStyle,
            onBackgroundStyleChange = onBackgroundStyleChange,
            launcherIcon = launcherIcon,
            onLauncherIconChange = onLauncherIconChange,
            // 番茄钟参数在专注页自己的卡片里，这里只是把它作为页签切过去。
            onOpenFocus = { nav.resetTo(HomeTab.Focus.route) },
            onOpenStats = { nav.push(ROUTE_STATS) },
            onOpenTags = { nav.push(ROUTE_TAGS) },
            developerUnlocked = developerUnlocked,
            onDeveloperModeUnlocked = {
                developerUnlocked = true
                devSettings.writeDeveloperUnlocked(true)
            },
            onOpenDeveloperSettings = { nav.push(ROUTE_DEV) },
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
            onSelectDate = { selectedEpochDay = it.toEpochDay() },
            pendingHabitCount = pendingHabitCount,
            habits = habitList,
            autoCheckInHabitId = focusViewModel.settings.autoCheckInHabitId,
            onSelectAutoCheckIn = focusViewModel::selectAutoCheckInHabit
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
    onSelectDate: (LocalDate) -> Unit,
    /** 抽屉里「习惯」那行的徽章：今天还没完成的习惯条数。 */
    pendingHabitCount: Int = 0,
    /** 专注抽屉「自动打卡」列出的习惯。 */
    habits: List<Habit> = emptyList(),
    autoCheckInHabitId: Long? = null,
    onSelectAutoCheckIn: (Long?) -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 换页必须把左滑面板收回去，而且**不能只是「开始收」**：
    //  - 抽屉状态跨页留着（用户在某一页拉开它，切到四象限 / 更多看不出来，切回有抽屉的页
    //    就「自己开着」）；
    //  - `close()` 是 `animateTo`，而 MainScreen 里那个 scope 在换页时会被取消（去标签管理 /
    //    习惯 / 统计 / 记录这些推入页），动画收一半就没了，抽屉停在半开甚至整个开着回来。
    // 所以这里一步到位：`snapTo`（不播动画）+ 在**壳层的 scope** 上跑（壳层对每条路由都在
    // 组合里，绝不会被这次换页取消）。换个页不会有任何一帧看得见「上一页那个开着的面板」。
    LaunchedEffect(route) { drawerState.snapTo(DrawerValue.Closed) }

    // 显示范围筛选与日历显示设置存 SharedPreferences：勾过的范围、日历的看法下次进来
    // 还在。列表顶栏和日历 ⋮ 各用一份独立的筛子，互不影响。
    val viewSettings = remember(context) { ViewSettingsStore(context) }
    var listFilter by remember { mutableStateOf(viewSettings.readListFilter()) }
    var calendarFilter by remember { mutableStateOf(viewSettings.readCalendarFilter()) }

    // 落盘的筛子里可能留着已经被删掉的清单 / 标签 id——删清单、删标签都不会回头改这份
    // 偏好，而那种筛子永远匹配不到任务，列表会被永久筛成空白、界面上还看不出原因。
    // 这里从库里取一次「实际存在的 id」把死 id 剔掉；只读库、不动用户此刻在改的筛子。
    LaunchedEffect(repository) {
        val liveListIds = repository.observeLists().first().map { it.id }.toSet()
        val liveTagIds = repository.observeTags().first().map { it.id }.toSet()
        listFilter = viewSettings.readListFilter().retainingExisting(liveListIds, liveTagIds)
        calendarFilter = viewSettings.readCalendarFilter().retainingExisting(liveListIds, liveTagIds)
    }
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
    val overdueCount by shellViewModel.overdueCount.collectAsStateWithLifecycle()
    val taskCounts by shellViewModel.taskCounts.collectAsStateWithLifecycle()
    val tagsByTask by shellViewModel.tagsByTask.collectAsStateWithLifecycle()
    val allTags by shellViewModel.tags.collectAsStateWithLifecycle()
    // 标签筛选要按 taskId 查「它带了哪些标签」，这里把标签映射收成 id 集合。
    val tagIdsByTask = remember(tagsByTask) {
        tagsByTask.mapValues { (_, tags) -> tags.map { it.id }.toSet() }
    }

    // The day the app opened on. It decides which tasks are 今日 and which are
    // 逾期; both are read-only views, so nothing here ever moves a due date.
    // 跟着「今天」那条流走：进程活过午夜时，日历的今天标记、空态文案、单日面板的
    // 「今天」判断都要换到新的一天（原来只是建页面时取一次）。
    val todayFlow = remember(repository) { repository.observeToday() }
    val today by todayFlow.collectAsStateWithLifecycle(initialValue = repository.today())

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

    // 清单小结：只有收件箱与用户清单各一条（其它视图给 null，那一行就不画）。这条 remember
    // 对每条路由都会执行，不会让组合槽位错位；key 是 collection，换清单时换一条流。
    val listSummaryFlow: Flow<ListSummary?> = remember(collection) {
        when (collection) {
            TaskCollection.Inbox -> repository.observeInboxSummary()
            is TaskCollection.ListDetail -> repository.observeListSummary(collection.listId)
            else -> flowOf(null)
        }
    }
    val listSummary by listSummaryFlow.collectAsStateWithLifecycle(initialValue = null)

    // 任务卡上的「开始专注」：把这条任务挂到专注页、再切过去；到那里按「开始专注」才真的
    // 起表，所以不会点一下就产生半截会话。
    val quickFocus: (Task) -> Unit = { task ->
        focusViewModel.linkTask(task)
        nav.resetTo(HomeTab.Focus.route)
    }
    val collectionKey = when (collection) {
        // 日历下面那一条**按天变，但不按天重建**：换一天只换订阅，StateFlow 把上一天那份留到
        // 新数据到达（原来是 `collection-day-<日期>`，每天一个新实例、每次都从空态起步，
        // 翻月落定那一刻下面整块「先空、再填」——台账 N7 那条，也是滑动落定的重活）。
        is TaskCollection.DayView -> "collection-day"
        else -> "collection-$route"
    }
    val collectionViewModel: TaskCollectionViewModel =
        viewModel(key = collectionKey, factory = TaskCollectionViewModel.factory(repository, collection))
    val tasks by collectionViewModel.tasks.collectAsStateWithLifecycle()
    val completedTasks by collectionViewModel.completedTasks.collectAsStateWithLifecycle()
    val subtaskCounts by collectionViewModel.subtaskCounts.collectAsStateWithLifecycle()
    val subtasksByParent by collectionViewModel.subtasksByParent.collectAsStateWithLifecycle()

    // 列表页内联展开的子任务：哪张卡的子任务区开着。用 id 集合记（转屏保得住），换卡
    // 也自然复用；收起就只是把 id 拿掉，不打扰别的卡。
    val expandedSubtaskParents = rememberSaveable(
        saver = listSaver<SnapshotStateList<Long>, Long>(
            save = { ArrayList(it) },
            restore = { SnapshotStateList<Long>().apply { addAll(it) } }
        )
    ) { mutableStateListOf() }

    // 复用同一个实例时把「今天是哪一天」喂进去；这个 effect 对每条路由都会跑，但只有
    // DayView 有日期，其它路由 dayOfCollection 是 null，什么都不做。
    val dayOfCollection = (collection as? TaskCollection.DayView)?.date
    LaunchedEffect(dayOfCollection) {
        if (dayOfCollection != null) collectionViewModel.showDate(dayOfCollection)
    }

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

    // 每张卡的子任务展开区：行直接从 subtasksByParent 里拿，勾选走 collectionViewModel。
    // 整个 map 只在有子任务的卡上才画（TaskCard 内部按 subtaskCount 判断）。
    val subtaskSections = remember(subtasksByParent, expandedSubtaskParents.toSet()) {
        subtasksByParent.mapValues { (parentId, rows) ->
            SubtaskSection(
                expanded = parentId in expandedSubtaskParents,
                onToggle = {
                    if (parentId in expandedSubtaskParents) {
                        expandedSubtaskParents.remove(parentId)
                    } else {
                        expandedSubtaskParents.add(parentId)
                    }
                },
                rows = rows.map { row ->
                    SubtaskRow(
                        id = row.id,
                        title = row.title,
                        checked = row.completedAt != null,
                        onCheckedChange = { collectionViewModel.toggleSubtask(row) },
                        onOpen = { onOpenTask(row.id) }
                    )
                }
            )
        }
    }

    // The inbox is pinned first, so only user lists can be reordered.
    val currentList = (collection as? TaskCollection.ListDetail)
        ?.let { detail -> lists.firstOrNull { it.id == detail.listId } }
    val userLists = lists.filter { !it.isInbox }
    val currentUserListIndex = currentList?.let { list -> userLists.indexOfFirst { it.id == list.id } } ?: -1

    val title = when (kind) {
        ROUTE_INBOX -> stringResource(R.string.list_inbox_name)
        ROUTE_LIST -> currentList?.let(::listTitleWithIcon).orEmpty()
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
        else -> R.string.empty_calendar_day
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

    // 左滑面板按路由分三种：任务视图与日历＝清单抽屉；专注页＝一个**导航面板**（只有
    // 「专注记录」这种能跳过去的入口，不是把整页内容塞进来——用户 2026-10-19 纠正）；
    // 四象限与更多页 **不给**抽屉——那里没有「清单」这回事，左滑不出来才是对的。
    val drawerMode = when (kind) {
        HomeTab.Focus.route -> DrawerMode.FOCUS
        HomeTab.Quadrant.route, HomeTab.More.route -> DrawerMode.NONE
        else -> DrawerMode.LISTS
    }
    val drawerWidthFraction = DrawerWidthFraction

    // 「清单 / 习惯」两格开关：有任务列表的那些视图（收件箱 / 清单 / 今天 / 未来 / 逾期 /
    // 已完成 / 回收站）顶栏都有它，点「习惯」就进习惯页；日历自己有一套，不放这个。
    val showsListSwitch = collection != TaskCollection.None && kind != HomeTab.Calendar.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerMode != DrawerMode.NONE,
        drawerContent = {
            // 抽屉不铺满全屏：只占屏幕约六成，拉出来时原来的界面还露在外面一点，
            // 一眼看得出「这是一层盖上去的面板」，也留了半边随时能点回去。
            //
            // ⚠️ 这张面板**一直组合着**，只在里面按路由决定放什么内容：以前是「不给抽屉的页
            // （四象限 / 更多）连 ModalDrawerSheet 都不组合」，于是从那儿切到专注页时这张面板
            // 是**第一次被组合**——第一帧还没量出宽度，M3 就先把它画在偏移 0（也就是「开着」
            // 的样子），下一帧才收回去。用户 2026-10-19 报的「切到专注页会闪一下，似乎是抽屉」
            // 就是这一帧。面板常驻之后它只在同一个组合节点上换内容，不会再重新量一次。
            ModalDrawerSheet(
                modifier = Modifier.width(
                    LocalConfiguration.current.screenWidthDp.dp * drawerWidthFraction
                )
            ) {
                when (drawerMode) {
                    DrawerMode.NONE -> Unit // 空面板：手势已关、顶栏也没有菜单按钮，拉不出来
                    DrawerMode.LISTS -> ListsDrawer(
                        lists = lists,
                        openTaskCounts = openTaskCounts,
                        overdueCount = overdueCount,
                        pendingHabitCount = pendingHabitCount,
                        today = today,
                        currentRoute = route,
                        onSelect = { target ->
                            // 习惯是推入页（带返回箭头、退得回来），别的都是根视图——
                            // 像底部页签那样整栈切换。
                            if (target == ROUTE_HABITS) nav.push(target) else nav.resetTo(target)
                            scope.launch { drawerState.close() }
                        },
                        onOpenHabits = {
                            nav.push(ROUTE_HABITS)
                            scope.launch { drawerState.close() }
                        },
                        onNewList = { showNewListDialog = true }
                    )

                    // 专注页的面板只放**入口**：一行「专注记录」，点它推进记录页（逐条
                    // 记录可以在那里改关联任务与时长、也能删）。原来把整页统计塞进来，
                    // 拉出来就顶掉半屏、也看不出能跳到哪，用户 2026-10-19 让改掉。
                    DrawerMode.FOCUS -> FocusDrawer(
                        // 抽屉里那块「这一天」饼图要读专注记录；走专注页那份 StateFlow
                        // （与统计页、记录页同一份），不另开一条 Room 查询。
                        sessions = focusViewModel.sessions,
                        zone = focusViewModel.zone,
                        today = today,
                        habits = habits,
                        autoCheckInHabitId = autoCheckInHabitId,
                        onSelectAutoCheckIn = onSelectAutoCheckIn,
                        onOpenRecords = {
                            nav.push(ROUTE_RECORDS)
                            scope.launch { drawerState.close() }
                        },
                        onOpenHabits = {
                            nav.push(ROUTE_HABITS)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            // 页面底与窗口底用同一个颜色（见 [todoCanvasColor]）：背景风格掺色后，
            // 原来 Scaffold 还画未掺色的 scheme 底，窗口 / 页面之间会差一层颜色。
            containerColor = LocalTodoColors.current.canvas,
            topBar = {
                if (!showsCalendarHeader) {
                    // 搜索态下整条顶栏让给搜索框 + 「取消」（用户 2026-10-21 要的
                    // 「搜索的样式、框」）：不再把输入框塞进 TopAppBar 的标题槽里，那会
                    // 带着描边和 Material 的大内边距，看着像一张没对齐的表单。
                    if (searchActive && !showsFocusChrome) {
                        SearchTopBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onCancel = {
                                searchActive = false
                                searchQuery = ""
                            },
                            hint = stringResource(R.string.search_hint)
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = {
                                if (drawerMode != DrawerMode.NONE) {
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
                                if (showsFocusChrome) {
                                    IconButton(
                                        onClick = { nav.push(ROUTE_STATS) },
                                        modifier = Modifier
                                            .testTag(FocusStatsEntryTag)
                                            .semantics { contentDescription = statsDescription }
                                    ) {
                                        StatsGlyph(tint = LocalTodoColors.current.dateMuted)
                                    }
                                }
                                if (!showsFocusChrome) {
                                    // 清单 / 习惯 两格开关：就在「筛选」前面（用户指定的位置）。
                                    if (showsListSwitch) {
                                        ListsHabitsSwitch(
                                            onLists = true,
                                            // 已经在清单这一侧了，点它不需要做任何事。
                                            onSelectLists = {},
                                            onSelectHabits = { nav.push(ROUTE_HABITS) }
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    // 显示范围筛选：一枚能看清「筛了什么」的胶囊。日历自己有
                                    // 一条筛选条，更多 / 四象限没有列表可筛，都不出现。
                                    if (collection != TaskCollection.None) {
                                        FilterPill(
                                            activeCount = activeFilter.selectedCount(),
                                            onClick = { filterPanelOpen = true }
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    IconButton(onClick = { searchActive = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.action_search)
                                        )
                                    }
                                    // 排序与「这个清单」的几个动作收进同一个 ⋮：原来清单页上
                                    // 一步排开八个图标，标题被挤得只剩几个字（2026-10-21 收拢）。
                                    val menuItems = mutableListOf<TodoMenuItem>()
                                    TaskSort.entries.forEach { mode ->
                                        menuItems += TodoMenuItem(
                                            label = stringResource(sortLabelRes(mode)),
                                            icon = when (mode) {
                                                TaskSort.DEFAULT -> "🗂️"
                                                TaskSort.PRIORITY -> "🎯"
                                                TaskSort.CREATED -> "🕒"
                                            },
                                            selected = sort == mode,
                                            onClick = { sort = mode }
                                        )
                                    }
                                    if (currentList != null && !currentList.isInbox) {
                                        val list = currentList
                                        if (currentUserListIndex > 0) {
                                            menuItems += TodoMenuItem(
                                                label = stringResource(R.string.action_move_up),
                                                icon = "⬆️",
                                                onClick = { shellViewModel.moveList(list.id, -1) }
                                            )
                                        }
                                        if (currentUserListIndex in 0 until userLists.lastIndex) {
                                            menuItems += TodoMenuItem(
                                                label = stringResource(R.string.action_move_down),
                                                icon = "⬇️",
                                                onClick = { shellViewModel.moveList(list.id, 1) }
                                            )
                                        }
                                        menuItems += TodoMenuItem(
                                            label = stringResource(R.string.action_rename),
                                            icon = "✏️",
                                            onClick = { renameTarget = list }
                                        )
                                        menuItems += TodoMenuItem(
                                            label = stringResource(R.string.action_delete_list),
                                            icon = "🗑️",
                                            danger = true,
                                            onClick = { deleteTarget = list }
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { sortMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.action_sort)
                                            )
                                        }
                                        TodoDropdownMenu(
                                            expanded = sortMenuOpen,
                                            onDismissRequest = { sortMenuOpen = false },
                                            items = menuItems
                                        )
                                    }
                                }
                            }
                        )
                    }
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
                    // 「+ 新建任务」：一枚带字的胶囊，而不是孤零零一个圆加号（用户
                    // 2026-10-21 要的「加号增加任务的 UI 和布局」）。字让第一次用的人
                    // 也知道它是什么；投影压得比默认低一点，压在列表上不抢戏。
                    val newTaskLabel = stringResource(R.string.action_new_task)
                    ExtendedFloatingActionButton(
                        onClick = { openNewTaskSheet(listId) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = newTaskLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        // 字在左、图标在左，语义上仍叫「新建任务」：读屏与冒烟测试
                        // （按 contentDescription 找它）都不用改。
                        modifier = Modifier.semantics { contentDescription = newTaskLabel },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = LocalTodoColors.current.accent,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 5.dp,
                            pressedElevation = 1.dp
                        )
                    )
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
                        lists = lists,
                        tags = allTags,
                        onOpenDisplayOptions = { calendarOptionsOpen = true },
                        onSelectDate = onSelectDate,
                        onOpenLists = { scope.launch { drawerState.open() } },
                        onOpenTask = onOpenTask,
                        // Only the month view leaves room under the grid for a day
                        // panel; the 周 / 三日 views carry their entries themselves.
                        modifier = if (calendarView == CalendarView.MONTH) Modifier else Modifier.weight(1f)
                    )
                    if (calendarView == CalendarView.MONTH) {
                        // 分界线（用户 2026-10-18 要求）：网格和下面日程区之间来一条细线。
                        // 没有它，上面那块日历和下面的任务糊成一片，看不出这是两个区。
                        HorizontalDivider(
                            color = LocalTodoColors.current.dateMuted.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        // 稿子④：日程区顶部先给一条「哪一天 · 几条」的标题，下面那些卡片
                        // 就不再是凭空出现的了。
                        CalendarDayHeader(date = selectedDate, count = visibleTasks.size)
                        // 稿子⑥：这一天确实没有安排（搜索里没有匹配不算）时，给一个像样的
                        // 空态，而不是一片留白。
                        val dayPanelEmpty =
                            visibleTasks.isEmpty() && visibleCompletedTasks.isEmpty()
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
                        } else if (dayPanelEmpty && searchQuery.isBlank()) {
                            CalendarDayEmptyState(
                                isToday = selectedDate == today,
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
                                modifier = Modifier.weight(1f),
                                onQuickFocus = quickFocus,
                                subtaskSections = subtaskSections
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
                    openTasks = remember { repository.observeQuadrantTasks() },
                    completedTasks = remember { repository.observeCompletedTasks() },
                    // 四象限只读、也不显示日期、也不收已经过期的任务：决定优先级的地方，
                    // 不给打勾也不给滑动完成。
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
                    modifier = Modifier.padding(innerPadding),
                    // 收件箱与用户清单在列表最上面给一条小结，其它视图这里是 null。
                    summary = listSummary,
                    onQuickFocus = quickFocus,
                    subtaskSections = subtaskSections,
                    // 首页（今天）最上面那条「还剩几件」要用它。
                    today = today
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
            onConfirm = { name, icon ->
                shellViewModel.createList(name, icon)
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
            initialIcon = list.icon.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name, icon ->
                shellViewModel.renameList(list.id, name, icon)
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
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val cardShape = RoundedCornerShape(18.dp)
    Column(modifier = modifier.fillMaxSize()) {
        // 功能入口：一张圆角卡片把「设置 / 专注 / 统计 / 标签」装在一起，行首加一个小
        // 图标、行间一条淡分隔线——与清单抽屉同一个视觉语言，不再是裸的一列字。
        MoreCard(shape = cardShape) {
            MoreEntry(
                icon = "⚙️",
                label = stringResource(R.string.more_entry_settings),
                onClick = { onOpen(ROUTE_SETTINGS) }
            )
            MoreDivider()
            MoreEntry(
                icon = "⏱️",
                label = stringResource(R.string.more_entry_focus),
                // Switching to the 专注 tab, not pushing it: pushing would put a page with
                // no tab strip on top of the stack and leave its back arrow with nothing
                // to pop.
                onClick = { onOpen(HomeTab.Focus.route) }
            )
            MoreDivider()
            MoreEntry(
                icon = "📊",
                label = stringResource(R.string.more_entry_stats),
                onClick = { onOpen(ROUTE_STATS) }
            )
            MoreDivider()
            MoreEntry(
                icon = "🏷️",
                label = stringResource(R.string.more_entry_tags),
                onClick = { onOpen(ROUTE_TAGS) }
            )
        }

        // 任务视图：已完成 / 回收站 一组。
        MoreCard(shape = cardShape, modifier = Modifier.padding(top = 10.dp)) {
            MoreEntry(
                icon = "✅",
                label = stringResource(R.string.nav_completed),
                onClick = { onOpen(ROUTE_COMPLETED) }
            )
            MoreDivider()
            MoreEntry(
                icon = "🗑️",
                label = stringResource(R.string.nav_trash),
                onClick = { onOpen(ROUTE_TRASH) }
            )
            MoreDivider()
            MoreEntry(
                icon = "🐞",
                label = stringResource(R.string.crash_log_title),
                onClick = { onOpen(ROUTE_CRASH) }
            )
        }

        SectionHeader(stringResource(R.string.drawer_title))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                MoreCard(shape = cardShape) {
                    lists.forEachIndexed { index, list ->
                        if (index > 0) MoreDivider()
                        MoreEntry(
                            icon = list.icon?.takeIf { it.isNotBlank() } ?: "📋",
                            label = list.name,
                            trailing = openTaskCounts[list.id] ?: 0,
                            onClick = { onOpen(listRoute(list.id)) }
                        )
                    }
                }
            }
        }
    }
}

/** 更多页里装一组入口的圆角卡片：卡片底 + 轻投影，与任务卡同一个层次。 */
@Composable
private fun MoreCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(
                elevation = 1.5.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.07f)
            )
            .clip(shape)
            .background(colors.card),
        content = content
    )
}

/** 更多卡片里两条入口之间的淡分隔线，缩进到图标之后。 */
@Composable
private fun MoreDivider() {
    HorizontalDivider(
        color = LocalTodoColors.current.dateMuted.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 54.dp)
    )
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
private fun MoreEntry(
    icon: String,
    label: String,
    trailing: Int? = null,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 行首的 32dp 图标位：每个功能一个主题色圆底（见 [entryIconTint]），
        // 一眼认出「这一行是去哪的」，也与任务卡色条同一套取色语言。
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(entryIconTint(icon).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 15.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null && trailing > 0) {
            Text(
                text = trailing.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.dateMuted
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.dateMuted
        )
    }
}

// ---------------------------------------------------------------- the drawer

/**
 * 抽屉占屏幕宽度的比例。原来只有六成，清单名稍长就被裁；按抽屉设计稿一度加宽到 84%，
 * 但用户 2026-10-19 觉得拉出来这块太占屏，要求缩回**当前宽度的约六成**（84% × 0.6 ≈ 50%）。
 */
private const val DrawerWidthFraction = 0.5f

/** 左滑面板按路由分三种：清单抽屉 / 专注页的导航面板 / 不给面板。 */
private enum class DrawerMode { LISTS, FOCUS, NONE }

/**
 * 抽屉里清单色点的取色顺序（值是 [TagPalette] 的下标）：先用蓝 / 紫 / 番茄红这些比较中性
 * 的，红和琥珀垫后，免得第一条清单就顶着「紧急」的红点。按清单 id 取，稳定、不随排序变。
 */
private val DrawerListDotOrder = listOf(3, 4, 5, 6, 2, 0, 1, 7)

/** 清单色点的直径。 */
private val DrawerDotSize = 8.dp

/** 一条清单的颜色点：同一个清单永远是同一个颜色。 */
private fun listDotColor(listId: Long): Color =
    TagPalette[DrawerListDotOrder[(listId % DrawerListDotOrder.size).toInt()]]

@Composable
private fun ListsDrawer(
    lists: List<TaskList>,
    openTaskCounts: Map<Long, Int>,
    overdueCount: Int,
    /** 今天还没完成的习惯条数：那一行的徽章。 */
    pendingHabitCount: Int,
    today: LocalDate,
    currentRoute: String,
    onSelect: (String) -> Unit,
    onOpenHabits: () -> Unit,
    onNewList: () -> Unit
) {
    val colors = LocalTodoColors.current
    val inboxId = lists.firstOrNull { it.isInbox }?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.accent.copy(alpha = 0.05f), Color.Transparent)
                )
            )
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📋", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.date_month_day,
                        today.monthValue,
                        today.dayOfMonth
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }
        }

        // 智能清单: the date driven views of spec section 4.1. They are queries,
        // not folders - nothing here moves a task or a date. 「逾期」那一行带红色徽章，
        // 一眼看得出积压了多少。
        SectionHeader(stringResource(R.string.drawer_section_smart))
        DrawerRow(
            label = stringResource(R.string.nav_today),
            selected = currentRoute == HomeTab.Today.route,
            icon = "☀️",
            onClick = { onSelect(HomeTab.Today.route) }
        )
        DrawerRow(
            label = stringResource(R.string.nav_future),
            selected = currentRoute == ROUTE_FUTURE,
            icon = "🌤️",
            onClick = { onSelect(ROUTE_FUTURE) }
        )
        DrawerRow(
            label = stringResource(R.string.nav_overdue),
            selected = currentRoute == ROUTE_OVERDUE,
            badge = overdueCount,
            icon = "⚠️",
            danger = true,
            onClick = { onSelect(ROUTE_OVERDUE) }
        )

        // 我的清单：收件箱 + 用户清单，每条前面一个属于自己的颜色点。
        SectionHeader(stringResource(R.string.drawer_section_lists))
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = ROUTE_INBOX) {
                DrawerRow(
                    label = stringResource(R.string.list_inbox_name),
                    selected = currentRoute == ROUTE_INBOX,
                    badge = openTaskCounts[inboxId ?: 0L] ?: 0,
                    icon = "📥",
                    dot = inboxId?.let(::listDotColor),
                    onClick = { onSelect(ROUTE_INBOX) }
                )
            }
            items(lists.filter { !it.isInbox }, key = { it.id }) { list ->
                DrawerRow(
                    label = list.name,
                    selected = currentRoute == listRoute(list.id),
                    badge = openTaskCounts[list.id] ?: 0,
                    // 有图标就把那个颜色点换成图标：它俩都是「这是哪条清单」的记号，
                    // 一起画在 36dp 的行里太挤。
                    icon = list.icon,
                    dot = listDotColor(list.id),
                    onClick = { onSelect(listRoute(list.id)) }
                )
            }
        }

        // 分隔线以下这几样不随清单滚动：清单再长，「已完成 / 回收站 / 习惯 / 新建清单」
        // 也一直在手边。
        HorizontalDivider(
            color = colors.dateMuted.copy(alpha = 0.2f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        DrawerRow(
            label = stringResource(R.string.nav_completed),
            selected = currentRoute == ROUTE_COMPLETED,
            icon = "✅",
            onClick = { onSelect(ROUTE_COMPLETED) }
        )
        // 回收站也放进来（用户 2026-10-19）：「更多」页那条照旧保留，这里多一个手边的入口。
        DrawerRow(
            label = stringResource(R.string.nav_trash),
            selected = currentRoute == ROUTE_TRASH,
            icon = "🗑️",
            onClick = { onSelect(ROUTE_TRASH) }
        )
        // 习惯那一行带徽章：今天还剩几条没打（全打完了就不显示徽章）。
        DrawerRow(
            label = stringResource(R.string.switch_habits),
            selected = false,
            badge = pendingHabitCount,
            icon = "🌱",
            onClick = onOpenHabits
        )
        TextButton(
            onClick = onNewList,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_new_list))
        }
    }
}

/**
 * 专注页左滑出来的那块面板：一个**导航面板**，只放能跳过去的入口（用户 2026-10-19）。
 *
 * 2026-10-21 又加了一块：面板顶部先给一张「这一天」的甜甜圈——按任务切开的专注占比，
 * 上面那对箭头能翻到前一天 / 后一天（用户要的「饼图之类的东西，可以查看某天的专注」）。
 * 它只画图，不改任何记录；要改记录仍然走下面那一行「专注记录」。
 */
@Composable
private fun FocusDrawer(
    sessions: Flow<List<FocusSession>>,
    zone: ZoneId,
    today: LocalDate,
    habits: List<Habit>,
    autoCheckInHabitId: Long?,
    onSelectAutoCheckIn: (Long?) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenHabits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    // 与专注页 / 统计页 / 记录页同一份 StateFlow，不另开一条查询。
    val allSessions by sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⏱️", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.focus_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.focus_drawer_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }
        }

        SectionHeader(stringResource(R.string.focus_drawer_section_records))
        // 「这一天」的专注占比（饼图）：只在抽屉里看，翻日期不动任何记录。
        FocusDayDonutCard(
            sessions = allSessions,
            today = today,
            zone = zone,
            onOpenRecords = onOpenRecords
        )
        Spacer(Modifier.height(4.dp))
        DrawerRow(
            label = stringResource(R.string.focus_history_title),
            selected = false,
            icon = "🕘",
            onClick = onOpenRecords
        )

        HorizontalDivider(
            color = colors.dateMuted.copy(alpha = 0.2f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        SectionHeader(stringResource(R.string.focus_drawer_section_auto))
        Text(
            text = stringResource(R.string.focus_drawer_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(4.dp))

        if (habits.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.focus_drawer_no_habits),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
                TextButton(onClick = onOpenHabits) {
                    Text(stringResource(R.string.focus_drawer_open_habits))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item(key = "auto-none") {
                    FocusHabitChoice(
                        label = stringResource(R.string.focus_drawer_auto_none),
                        icon = "🚫",
                        selected = autoCheckInHabitId == null,
                        onClick = { onSelectAutoCheckIn(null) }
                    )
                }
                items(habits, key = { it.id }) { habit ->
                    FocusHabitChoice(
                        label = habit.name,
                        icon = habit.icon,
                        selected = autoCheckInHabitId == habit.id,
                        onClick = { onSelectAutoCheckIn(habit.id) }
                    )
                }
            }
        }
    }
}

/** 专注抽屉里的一条习惯选择：图标 + 名字 + 选中勾，和清单行同一个密度。 */
@Composable
private fun FocusHabitChoice(
    label: String,
    icon: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = icon?.takeIf { it.isNotBlank() } ?: "•",
                fontSize = 15.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 抽屉里的一行。
 *
 * 不用 material3 的 NavigationDrawerItem：它自带 56dp 的最小高度和一整套竖向内边距，
 * 清单多了以后一屏只能看见七八行。这里换成精简行（整行约 36dp），配合加宽后的抽屉，
 * 一屏能多看几行，也更接近用户要的「紧凑一点」。
 */
@Composable
private fun DrawerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int = 0,
    icon: String? = null,
    dot: Color? = null,
    danger: Boolean = false
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选中行左侧一条短竖线：比整行淡底更容易一眼看到「我现在在哪」。
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) colors.accent else Color.Transparent)
        )
        Spacer(Modifier.width(7.dp))
        if (!icon.isNullOrBlank()) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 17.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
        } else if (dot != null) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(DrawerDotSize)
                        .clip(CircleShape)
                        .background(dot)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge > 0) {
            DrawerCountPill(count = badge, danger = danger)
        }
    }
}

/**
 * 抽屉里的计数：一个圆角药丸徽章。清单是淡蓝的，逾期是淡红的（红警）——原来是
 * 「3 个未完成任务」那样一整句淡灰文字，既占地方又读不出轻重。
 */
@Composable
private fun DrawerCountPill(count: Int, danger: Boolean) {
    val colors = LocalTodoColors.current
    val tint = if (danger) colors.dateOverdue else colors.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1
        )
    }
}

/** The label of one sort mode in the top bar menu. */
private fun sortLabelRes(sort: TaskSort): Int = when (sort) {
    TaskSort.DEFAULT -> R.string.sort_default
    TaskSort.PRIORITY -> R.string.sort_priority
    TaskSort.CREATED -> R.string.sort_created
}
