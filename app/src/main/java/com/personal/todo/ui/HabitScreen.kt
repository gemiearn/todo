package com.personal.todo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Habit
import com.personal.todo.model.HabitRules
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import com.personal.todo.ui.theme.TodoColors
import com.personal.todo.viewmodel.HabitViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

/** The tag the smoke tests use to reach the page's habit list. */
internal const val HabitScreenTag = "habit-screen"

/** 右上角那个新建习惯的图标（它没有文字）。 */
internal const val HabitNewTag = "habit-new"

/** 新建 / 编辑弹层里的名字输入框。 */
internal const val HabitNameFieldTag = "habit-name"

/** 弹层里的单位输入框。 */
internal const val HabitUnitFieldTag = "habit-unit"

/** 弹层里的「每天目标」输入框。 */
internal const val HabitTargetFieldTag = "habit-target"

/** 一个习惯「今天」那一次打卡的区域：点一下 +1、长按 −1。 */
internal fun habitRowTag(habitId: Long): String = "habit-row-$habitId"

/** 最近 7 天里某一天的格子：点一下给那天 +1、长按 −1。 */
internal fun habitDayCellTag(habitId: Long, date: LocalDate): String =
    "habit-day-$habitId-${date.toEpochDay()}"

/** 「每次增加」的上限：再多就不像「一次」了。 */
private const val MaxStep = 999

/** 单位最多几个字（「毫升」两个字，留点余量）。 */
private const val MaxUnitLength = 4

/** 目标量最多几位数。 */
private const val MaxTargetDigits = 5

private val TargetFieldWidth = 110.dp

/** 进度条的高度：细一点，不要抢了格子的注意力。 */
private val ProgressBarHeight = 7.dp

/** 「今天」那一整列的淡蓝底带：要浅到还能看清格子里的颜色，又要一眼分得出来。 */
private const val TodayBandAlpha = 0.12f

/**
 * 每个习惯一个属于自己的颜色（用户 2026-10-18：「习惯页颜色单调」）。
 *
 * 按习惯 id 从 [TagPalette] 里取：同一个习惯永远同一个颜色，不随排序变。取色顺序先用
 * 蓝 / 紫 / 番茄红这些中性的，红与琥珀垫后，免得第一个习惯就顶着「紧急」的红。
 */
private val HabitColorOrder = listOf(3, 4, 5, 6, 2, 0, 1, 7)

private fun habitColorOf(habitId: Long): Color =
    TagPalette[HabitColorOrder[(habitId % HabitColorOrder.size).toInt()]]

/**
 * 习惯页：一张打勾表——每个习惯一行，加一条「最近 7 天」的格子。
 *
 * 它是一个独立页面（和标签管理、设置一样从「更多」推进来），不占页签、不进日历。
 * 每个习惯有「每天目标次数」（喝水 5 次 → 5）：
 * 点一下某一天的格子＝那天 +1，长按＝−1，到目标就不再往上加；次数为 1 的习惯就是普通的
 * 「今天做没做」。
 *
 * 这里的 [HabitViewModel.week] 最右是今天，所以格子行里不会出现未来的日子。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(
    viewModel: HabitViewModel,
    /** 顶栏那个「清单 / 习惯」开关：给了才会画（点「清单」＝退回去）。 */
    onSwitchToLists: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    // 「今天」与最近 7 天都来自流：页面一直开着跨过午夜时，它们会自己翻到新的一天。
    val today by viewModel.today.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()

    // null = 关着；editing = 改这个；deleting = 删这个之前先问一次。
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }
    val colors = LocalTodoColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.habit_title),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.habit_page_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalTodoColors.current.dateMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    // 清单 / 习惯 两格开关：在这一页高亮的是「习惯」，点「清单」退回去。
                    if (onSwitchToLists != null) {
                        ListsHabitsSwitch(
                            onLists = false,
                            onSelectLists = onSwitchToLists,
                            onSelectHabits = {}
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { creating = true },
                        modifier = Modifier.testTag(HabitNewTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.habit_new)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (habits.isEmpty()) {
            HabitEmptyState(
                onCreate = { creating = true },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(HabitScreenTag),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 今日概况：今天完成了几个习惯（稿子里的「不那么单调」从这里开始）。
            item(key = "habit-summary") {
                HabitSummaryCard(
                    done = habits.count { habit ->
                        HabitRules.isComplete(
                            counts[habit.id]?.get(today) ?: 0,
                            habit.dailyTarget,
                            habit.step
                        )
                    },
                    total = habits.size
                )
            }
            items(habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    week = week,
                    today = today,
                    countsOfHabit = counts[habit.id].orEmpty(),
                    onIncrement = viewModel::increment,
                    onDecrement = viewModel::decrement,
                    onEdit = { editing = habit },
                    onDelete = { deleting = habit }
                )
            }
        }
    }

    if (creating) {
        HabitEditSheet(
            title = stringResource(R.string.habit_new),
            initialName = "",
            initialTarget = HabitRules.MinTarget,
            initialUnit = "",
            initialStep = 1,
            initialIcon = NoIcon,
            accent = colors.accent,
            onConfirm = { draft ->
                viewModel.create(
                    name = draft.name,
                    dailyTarget = draft.dailyTarget,
                    unitLabel = draft.unitLabel,
                    stepAmount = draft.stepAmount,
                    icon = draft.icon
                )
                creating = false
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { habit ->
        HabitEditSheet(
            title = stringResource(R.string.habit_edit),
            initialName = habit.name,
            initialTarget = habit.dailyTarget,
            initialUnit = habit.unitLabel.orEmpty(),
            initialStep = habit.step,
            initialIcon = habit.icon.orEmpty(),
            accent = habitColorOf(habit.id),
            onConfirm = { draft ->
                viewModel.update(
                    habitId = habit.id,
                    name = draft.name,
                    dailyTarget = draft.dailyTarget,
                    unitLabel = draft.unitLabel,
                    stepAmount = draft.stepAmount,
                    icon = draft.icon
                )
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.habit_delete_title)) },
            text = { Text(stringResource(R.string.habit_delete_message, habit.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(habit.id)
                        deleting = null
                    }
                ) {
                    Text(stringResource(R.string.habit_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** 空态：一株小芽 + 一句很轻的引导 + 一个真的能点的「新建」按钮。 */
@Composable
private fun HabitEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTodoColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.18f),
                                colors.accent.copy(alpha = 0.04f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🌱", fontSize = 38.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.habit_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.habit_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.dateMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.habit_empty_action),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 一个习惯：上面是名字与「今天 n/m」，下面是最近 7 天的格子。
 *
 * 名字那一行本身就是今天的打卡入口（点＝+1、长按＝−1），格子行负责按下某一天补打。
 * 改名 / 改次数 / 删除收在右边的 ⋮ 里——它自己是一个按钮，不会把点击漏给上面那一行。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    habit: Habit,
    week: List<LocalDate>,
    today: LocalDate,
    countsOfHabit: Map<LocalDate, Int>,
    onIncrement: (Long, LocalDate) -> Unit,
    onDecrement: (Long, LocalDate) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val todayCount = countsOfHabit[today] ?: 0
    val done = HabitRules.isComplete(todayCount, habit.dailyTarget, habit.step)
    // 这个习惯自己的颜色：进度条、今天那一列、以及「打了一部分」的格子都用它。
    val habitColor = habitColorOf(habit.id)
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.card, lerp(colors.card, habitColor, 0.04f))
                )
            )
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onIncrement(habit.id, today) },
                        onLongClick = { onDecrement(habit.id, today) }
                    )
                    .testTag(habitRowTag(habit.id))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HabitBadge(
                    done = done,
                    icon = habit.icon,
                    name = habit.name,
                    color = habitColor
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.habit_today_progress,
                            HabitRules.shownAmount(todayCount, habit.dailyTarget, habit.step),
                            habit.dailyTarget,
                            habit.unit
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done) colors.success else colors.dateMuted
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.habit_more),
                        tint = colors.dateMuted
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habit_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habit_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        // 这个习惯今天的进度：一条细进度条，比只写「今天 45/60 分钟」更一眼看得出还差多少。
        HabitProgressBar(
            progress = HabitRules.shownAmount(todayCount, habit.dailyTarget, habit.step).toFloat() /
                habit.dailyTarget.coerceAtLeast(HabitRules.MinTarget),
            color = habitColor,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            week.forEach { date ->
                HabitDayCell(
                    habitId = habit.id,
                    date = date,
                    count = countsOfHabit[date] ?: 0,
                    target = habit.dailyTarget,
                    step = habit.step,
                    isToday = date == today,
                    color = habitColor,
                    onClick = { onIncrement(habit.id, date) },
                    onLongClick = { onDecrement(habit.id, date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 顶部那条今日概况：今天完成了几个习惯，下面一条细进度条。
 *
 * 「今天完成 n/m」是按「到目标的习惯数」算的，和每个习惯行上的绿勾是同一个口径。
 */
@Composable
private fun HabitSummaryCard(done: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = LocalTodoColors.current
    val allDone = total > 0 && done == total
    val accent = if (allDone) colors.success else colors.accent
    val progress = if (total == 0) 0f else done.toFloat() / total
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.10f),
                spotColor = accent.copy(alpha = 0.12f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.card, lerp(colors.card, accent, 0.10f))
                )
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.habit_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = done.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = " / $total",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.dateMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (allDone) {
                    stringResource(R.string.habit_summary_all_done)
                } else {
                    stringResource(R.string.habit_summary_remaining, (total - done).coerceAtLeast(0))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (allDone) colors.success else colors.dateMuted
            )
        }
        Box(
            modifier = Modifier.size(66.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                drawArc(
                    color = colors.groupHeader,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (progress > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
    }
}

/** 一条有呼吸感的细进度条：底是浅灰，按比例铺一层渐变，打卡时自己滑过去。 */
@Composable
private fun HabitProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420),
        label = "habit-progress"
    )
    val shape = RoundedCornerShape(ProgressBarHeight / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressBarHeight)
            .clip(shape)
            .background(colors.groupHeader.copy(alpha = 0.85f))
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.58f), color)
                        ),
                        shape
                    )
            )
        }
    }
}

/**
 * 习惯左侧那个圆形头像：到目标就是绿色勾，没到就是习惯自己的颜色 + 图标 / 首字。
 *
 * 它同时承担「身份」和「今天完成没有」两件事，比单独一个 24dp 勾更耐看，也让每个习惯
 * 在列表里一眼认得出。
 */
@Composable
private fun HabitBadge(
    done: Boolean,
    icon: String?,
    name: String,
    color: Color,
    size: Dp = 38.dp
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (done) colors.success else color.copy(alpha = 0.12f))
            .then(
                if (done) {
                    Modifier
                } else {
                    Modifier.border(1.dp, color.copy(alpha = 0.26f), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            done -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )

            !icon.isNullOrBlank() -> Text(
                text = icon,
                fontSize = (size.value * 0.44f).sp,
                maxLines = 1
            )

            else -> Text(
                text = name.take(1).ifBlank { "🌱" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * 最近 7 天里的一天：上面是星期几（今天那一列直接写「今天」），下面那个小方块里是那天的次数。
 *
 * 方块的颜色直接说清了状态：到目标＝实色（绿），有几次但没到＝浅色，一次没打＝灰底。
 * **今天那一整列**铺一条淡蓝底带、标签用实心强调色写上「今天」，一眼看得出今天在哪、
 * 要补哪一格。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitDayCell(
    habitId: Long,
    date: LocalDate,
    count: Int,
    target: Int,
    step: Int,
    isToday: Boolean,
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val done = HabitRules.isComplete(count, target, step)
    val cellShape = RoundedCornerShape(12.dp)
    val numberShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .clip(cellShape)
            .background(if (isToday) color.copy(alpha = TodayBandAlpha) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(habitDayCellTag(habitId, date))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isToday) {
                stringResource(R.string.date_today)
            } else {
                stringResource(weekdayNarrowRes(date.dayOfWeek))
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) color else colors.dateMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(numberShape)
                .background(
                    when {
                        done -> colors.success
                        count > 0 -> color.copy(alpha = 0.18f)
                        // 今天那一列已经有自己的底带了，方块不必再叠一层灰。
                        isToday -> Color.Transparent
                        else -> colors.groupHeader
                    }
                )
                .then(
                    if (isToday) {
                        Modifier.border(1.4.dp, color.copy(alpha = 0.55f), numberShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (done || count > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    done -> Color.White
                    count > 0 || isToday -> color
                    else -> colors.dateMuted
                }
            )
        }
    }
}

/** 弹层里那一份草稿：习惯可编辑的字段都在这里，点保存时整份交回。 */
private data class HabitDraft(
    val name: String,
    val dailyTarget: Int,
    val unitLabel: String?,
    val stepAmount: Int?,
    val icon: String
)

/**
 * 新建 / 编辑习惯：名字、图标、单位、每天目标、单次基数。
 *
 * 用底部弹层而不是 AlertDialog，和「新建任务」那个弹层保持一致（它也是唯一在测试里被
 * 跑过的带输入框弹层）。保存按钮在弹层底部整行铺满，和参考里的新建任务一个样子。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitEditSheet(
    title: String,
    initialName: String,
    initialTarget: Int,
    initialUnit: String,
    initialStep: Int,
    initialIcon: String = NoIcon,
    accent: Color,
    onConfirm: (HabitDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalTodoColors.current
    var name by remember { mutableStateOf(initialName) }
    var unit by remember { mutableStateOf(initialUnit) }
    var icon by remember { mutableStateOf(initialIcon) }
    var targetText by remember { mutableStateOf(initialTarget.toString()) }
    var step by remember { mutableIntStateOf(initialStep) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    val target = targetText.toIntOrNull() ?: 0
    val unitShown = unit.ifBlank { Habit.DefaultUnit }
    val previewName = name
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.dateMuted.copy(alpha = 0.35f))
        }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.habit_edit_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
            Spacer(Modifier.height(16.dp))

            // 先把「它会是什么样」画出来：图标、名字、每天目标。改任何一项这里立刻变。
            HabitPreviewCard(
                name = previewName,
                icon = icon,
                target = target,
                unit = unitShown,
                accent = accent
            )

            Spacer(Modifier.height(16.dp))
            EditSection {
                Text(
                    text = stringResource(R.string.habit_name_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    leadingIcon = {
                        Text(text = icon.ifBlank { "🌱" }, fontSize = 19.sp)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = habitFieldColors(colors),
                    modifier = Modifier.fillMaxWidth().testTag(HabitNameFieldTag)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { iconPickerOpen = !iconPickerOpen }) {
                        Text(
                            text = stringResource(
                                if (iconPickerOpen) {
                                    R.string.habit_icon_hide
                                } else {
                                    R.string.habit_icon_pick
                                }
                            )
                        )
                    }
                }
                AnimatedVisibility(
                    visible = iconPickerOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    IconPicker(
                        selected = icon,
                        onPick = { icon = it },
                        // 新建习惯默认落在「健康」这一页；之后想选什么种类都行。
                        preferredCategory = IconCategory.HEALTH
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            EditSection {
                Text(
                    text = stringResource(R.string.habit_target_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { typed ->
                            targetText = typed.filter { it.isDigit() }.take(MaxTargetDigits)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = habitFieldColors(colors),
                        modifier = Modifier.width(TargetFieldWidth).testTag(HabitTargetFieldTag)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = unitShown,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.dateMuted
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.habit_step_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperChip(label = "−") { step = (step - 1).coerceAtLeast(1) }
                    Text(
                        text = step.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    StepperChip(label = "+") { step = (step + 1).coerceAtMost(MaxStep) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = unitShown,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.habit_unit_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(MaxUnitLength) },
                    placeholder = { Text(Habit.DefaultUnit) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = habitFieldColors(colors),
                    modifier = Modifier.fillMaxWidth().testTag(HabitUnitFieldTag)
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.habit_unit_preset_repeat),
                        stringResource(R.string.habit_unit_preset_minute),
                        stringResource(R.string.habit_unit_preset_hour),
                        stringResource(R.string.habit_unit_preset_cup),
                        stringResource(R.string.habit_unit_preset_ml)
                    ).forEach { preset ->
                        UnitPresetChip(
                            label = preset,
                            selected = unit,
                            onPick = { unit = it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    onConfirm(
                        HabitDraft(
                            name = name,
                            dailyTarget = target,
                            unitLabel = unit,
                            stepAmount = step,
                            icon = icon
                        )
                    )
                },
                enabled = name.isNotBlank() && target >= HabitRules.MinTarget,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    // 习惯颜色可能是浅琥珀 / 浅绿，白字不一定读得清：按底色深浅挑字色。
                    contentColor = onPriorityColor(accent),
                    disabledContainerColor = colors.groupHeader,
                    disabledContentColor = colors.dateMuted
                )
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 弹层顶部的实时预览：改名字 / 图标 / 目标时，这里立刻跟着变。 */
@Composable
private fun HabitPreviewCard(
    name: String,
    icon: String,
    target: Int,
    unit: String,
    accent: Color
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(lerp(colors.card, accent, 0.12f), colors.card)
                )
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HabitBadge(
            done = false,
            icon = icon,
            name = name,
            color = accent,
            size = 42.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (name.isNotBlank()) {
                Text(
                    // 加书名号：预览里的名字和输入框里的名字不是同一个节点，读屏和测试
                    // 都不会把「正在输入的名字」误认成两个。
                    text = "「$name」",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.habit_preview_target, target, unit),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
        }
        Text(
            text = stringResource(R.string.habit_preview),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

/** 编辑弹层里的分组底：把字段收进一张圆角浅色块，别让整页都是零散的输入框。 */
@Composable
private fun EditSection(content: @Composable () -> Unit) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.groupHeader.copy(alpha = 0.45f))
            .padding(14.dp)
    ) {
        content()
    }
}

/** 输入框聚焦时用强调色，平时用分组底；和外面那张浅色块是一个层次。 */
@Composable
private fun habitFieldColors(colors: TodoColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = colors.accent,
    unfocusedBorderColor = colors.groupHeader,
    focusedContainerColor = colors.card,
    unfocusedContainerColor = colors.card,
    cursorColor = colors.accent
)

/**
 * 「−」/「+」那一个小方块：方底 + 点一下。
 *
 * 用 Box + clickable 而不是 material3 的 TextButton，只是为了和新建任务弹层里的快捷日期
 * 胶囊长得一样（方底小片），不是为了绕开什么——两者在真机上都是普通的点击。
 */
@Composable
private fun StepperChip(label: String, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.groupHeader)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 单位快捷选项：点一下就把单位填成它（填了还剩手写）。 */
@Composable
private fun UnitPresetChip(label: String, selected: String, onPick: (String) -> Unit) {
    val colors = LocalTodoColors.current
    val on = selected == label
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) colors.accent.copy(alpha = 0.14f) else colors.groupHeader)
            .clickable { onPick(label) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) colors.accent else MaterialTheme.colorScheme.onSurface
        )
    }
}
