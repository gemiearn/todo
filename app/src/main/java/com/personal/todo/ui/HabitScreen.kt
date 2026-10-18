package com.personal.todo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Habit
import com.personal.todo.model.HabitRules
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import com.personal.todo.viewmodel.HabitViewModel
import java.time.LocalDate

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
private val ProgressBarHeight = 6.dp

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
    onBack: () -> Unit
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()

    // null = 关着；editing = 改这个；deleting = 删这个之前先问一次。
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
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
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.habit_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted,
                    modifier = Modifier.padding(24.dp)
                )
            }
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
                            counts[habit.id]?.get(viewModel.today) ?: 0,
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
                    week = viewModel.week,
                    today = viewModel.today,
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
            onConfirm = { name, target, unit, step, icon ->
                viewModel.create(name, target, unit, step, icon)
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
            onConfirm = { name, target, unit, step, icon ->
                viewModel.update(habit.id, name, target, unit, step, icon)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { onIncrement(habit.id, today) },
                        onLongClick = { onDecrement(habit.id, today) }
                    )
                    .testTag(habitRowTag(habit.id))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HabitBadge(done)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        // 习惯自己的图标（选填）就放在名字前面。
                        text = habit.icon?.takeIf { it.isNotBlank() }?.let { "$it ${habit.name}" } ?: habit.name,
                        style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.habit_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.habit_summary_count, done, total),
                style = MaterialTheme.typography.titleMedium,
                color = if (allDone) colors.success else colors.accent
            )
        }
        Spacer(Modifier.height(8.dp))
        HabitProgressBar(
            progress = if (total == 0) 0f else done.toFloat() / total,
            color = if (allDone) colors.success else colors.accent
        )
    }
}

/** 一条细进度条：底是浅灰，按比例铺一层颜色。 */
@Composable
private fun HabitProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressBarHeight)
            .clip(RoundedCornerShape(ProgressBarHeight / 2))
            .background(colors.groupHeader)
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(color)
            )
        }
    }
}

/** 今天完成了没有：到目标就是实心勾，没到就是一个空圈。 */
@Composable
private fun HabitBadge(done: Boolean) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (done) colors.success else Color.Transparent)
            .then(
                if (done) Modifier else Modifier.border(1.5.dp, colors.dateMuted, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isToday) color.copy(alpha = TodayBandAlpha) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(habitDayCellTag(habitId, date))
            .padding(vertical = 6.dp),
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
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        done -> colors.success
                        count > 0 -> color.copy(alpha = 0.18f)
                        // 今天那一列已经有自己的底带了，方块不必再叠一层灰。
                        isToday -> Color.Transparent
                        else -> colors.groupHeader
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    done -> Color.White
                    count > 0 || isToday -> color
                    else -> colors.dateMuted
                }
            )
        }
    }
}

/**
 * 新建 / 编辑习惯：一个名字格子加一个「每天次数」的步进器。
 *
 * 用底部弹层而不是 AlertDialog，和「新建任务」那个弹层保持一致（它也是唯一在测试里被
 * 跑过的带输入框弹层）。保存按钮在弹层底部整行铺满，和参考里的新建任务一个样子。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditSheet(
    title: String,
    initialName: String,
    initialTarget: Int,
    initialUnit: String,
    initialStep: Int,
    initialIcon: String = NoIcon,
    onConfirm: (String, Int, String?, Int?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalTodoColors.current
    var name by remember { mutableStateOf(initialName) }
    var unit by remember { mutableStateOf(initialUnit) }
    var icon by remember { mutableStateOf(initialIcon) }
    var targetText by remember { mutableStateOf(initialTarget.toString()) }
    var step by remember { mutableIntStateOf(initialStep) }
    val target = targetText.toIntOrNull() ?: 0
    val unitShown = unit.ifBlank { Habit.DefaultUnit }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.habit_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(HabitNameFieldTag)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.icon_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted
            )
            Spacer(Modifier.height(6.dp))
            IconPickerRow(selected = icon, onPick = { icon = it })
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it.take(MaxUnitLength) },
                label = { Text(stringResource(R.string.habit_unit_label)) },
                placeholder = { Text(Habit.DefaultUnit) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(HabitUnitFieldTag)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Spacer(Modifier.height(14.dp))
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
                    modifier = Modifier.width(TargetFieldWidth).testTag(HabitTargetFieldTag)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = unitShown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.dateMuted
                )
            }

            Spacer(Modifier.height(14.dp))
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                StepperChip(label = "+") { step = (step + 1).coerceAtMost(MaxStep) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = unitShown,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(name, target, unit, step, icon) },
                enabled = name.isNotBlank() && target >= HabitRules.MinTarget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

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
