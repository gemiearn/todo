package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.todo.R
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Countdown
import com.personal.todo.model.DateLabel
import com.personal.todo.model.Priority
import com.personal.todo.model.Schedule
import com.personal.todo.model.TaskEventType
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Date pattern is a formatting detail, the surrounding labels live in strings.xml. */
private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val CardShape = RoundedCornerShape(16.dp)

/** Width of the coloured bar along the left edge of a task card. */
private val BarWidth = 3.dp

/**
 * 色条上下各内缩多少。原来是通顶贴边的矩形，看着像一张贴纸；内缩并带圆角之后
 * 它才像卡片自己的一个标记。
 */
private val BarInset = 13.dp

/** 勾选钮的直径。稿子⑤把它从 Material3 的方形 Checkbox 换成一个圆形描边钮。 */
private val CheckSize = 22.dp

/** 勾选钮的触控区：图标只画 22dp，外面这一圈是给手指点的。 */
private val CheckTouchSize = 32.dp

/** How often the "now" used by the date labels and countdowns is refreshed. */
private const val NowRefreshMillis = 30_000L

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TimestampFormatter)

/**
 * A stored time of day as `HH:mm`, the same shape the editor and the detail
 * screen write. Used by the calendar views that put a time next to a task.
 */
internal fun clockTimeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/**
 * The current moment, refreshed while the screen is shown, so the relative
 * dates ("今天", "明天") and the countdowns keep up with the clock instead of
 * freezing at the moment the list was first composed.
 */
@Composable
fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NowRefreshMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** 优先级的名字就是四象限的名字：Ⅰ 重要且紧急，Ⅱ 重要不紧急，Ⅲ 不重要但紧急，Ⅳ 不重要不紧急。 */
@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.IMPORTANT_URGENT -> R.string.quadrant_important_urgent
        Priority.IMPORTANT_NOT_URGENT -> R.string.quadrant_important_not_urgent
        Priority.NOT_IMPORTANT_URGENT -> R.string.quadrant_not_important_urgent
        Priority.NOT_IMPORTANT_NOT_URGENT -> R.string.quadrant_not_important_not_urgent
    }
)

/** 四象限的四个颜色：任务卡左边的色条、四象限页的角标都用它；随「色系」设置变。 */
@Composable
fun priorityColor(priority: Priority): Color {
    val quadrants = LocalTodoColors.current.quadrants
    return when (priority) {
        Priority.IMPORTANT_URGENT -> quadrants.importantUrgent
        Priority.IMPORTANT_NOT_URGENT -> quadrants.importantNotUrgent
        Priority.NOT_IMPORTANT_URGENT -> quadrants.notImportantUrgent
        Priority.NOT_IMPORTANT_NOT_URGENT -> quadrants.notImportantNotUrgent
    }
}

/**
 * 角标上的字用哪个颜色：把白字与近黑字（#1C1C1E）的对比度都算出来，挑高的那个。
 *
 * 原来写的是「亮度 > 0.55 就用深色字」——那个阈值离正确的分界点差得太远：
 * 琥珀 #F5A623 的亮度只有 0.47，会被判成「深底」配上白字，对比度只剩 2.02:1（应为白底
 * 近黑字的 8:1 级别）。马卡龙那套浅色又必须用深色字，所以这里不能猜，得按对比度算：
 * 白字与 #1C1C1E 的分界点在亮度 ≈ 0.20，超过它才是「该用深色字」的浅底。
 */
fun onPriorityColor(background: Color): Color {
    val ink = Color(0xFF1C1C1E)
    val luminance = background.luminance()
    val whiteContrast = 1.05f / (luminance + 0.05f)
    val inkContrast = (luminance + 0.05f) / (ink.luminance() + 0.05f)
    return if (inkContrast >= whiteContrast) ink else Color.White
}

@Composable
fun eventLabel(type: TaskEventType): String = stringResource(
    when (type) {
        TaskEventType.CREATED -> R.string.event_created
        TaskEventType.COMPLETED -> R.string.event_completed
        TaskEventType.REOPENED -> R.string.event_reopened
        TaskEventType.DELETED -> R.string.event_deleted
        TaskEventType.RESTORED -> R.string.event_restored
        TaskEventType.UNKNOWN -> R.string.event_unknown
    }
)

/** A relative date of [Schedule] as Chinese text; the wording lives in strings.xml. */
@Composable
fun dateLabelText(label: DateLabel): String? = when (label) {
    DateLabel.None -> null
    DateLabel.Today -> stringResource(R.string.date_today)
    DateLabel.Tomorrow -> stringResource(R.string.date_tomorrow)
    DateLabel.Yesterday -> stringResource(R.string.date_yesterday)
    DateLabel.Overdue -> stringResource(R.string.date_overdue)
    is DateLabel.Weekday -> stringResource(weekdayRes(label.dayOfWeek))
    is DateLabel.MonthDay -> stringResource(R.string.date_month_day, label.month, label.day)
    is DateLabel.OverdueDays -> stringResource(R.string.date_overdue_days, label.days)
}

@Composable
private fun countdownText(countdown: Countdown): String? = when (countdown) {
    Countdown.None -> null
    is Countdown.Minutes -> stringResource(R.string.countdown_minutes, countdown.minutes)
    is Countdown.Hours -> stringResource(R.string.countdown_hours, countdown.hours)
    is Countdown.Days -> stringResource(R.string.countdown_days, countdown.days)
}

private fun weekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_mon
    DayOfWeek.TUESDAY -> R.string.weekday_tue
    DayOfWeek.WEDNESDAY -> R.string.weekday_wed
    DayOfWeek.THURSDAY -> R.string.weekday_thu
    DayOfWeek.FRIDAY -> R.string.weekday_fri
    DayOfWeek.SATURDAY -> R.string.weekday_sat
    DayOfWeek.SUNDAY -> R.string.weekday_sun
}

/**
 * The date a task shows next to its title: the due date when it has one (with
 * its overdue state), otherwise the planned date, otherwise the day it was
 * finished. Returns the text together with the colour of that state, so the
 * card and the quadrant row draw the same date the same way.
 *
 * A task that has both a planned date and a due date is a span, not a moment: it
 * is on every day between the two, so the card names both ends. A date line that
 * only named the due end hid the fact that the task is already running.
 */
@Composable
internal fun taskDateLabel(task: Task, nowMillis: Long, zone: ZoneId): Pair<String?, Color> {
    val colors = LocalTodoColors.current
    val completed = task.completedAt != null
    val overdue = !completed &&
        Schedule.isOverdue(
            nowMillis = nowMillis,
            plannedDate = task.plannedDate,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            zone = zone
        )

    val today = Schedule.todayOf(nowMillis, zone)
    val plannedLabel = Schedule.dateLabel(task.plannedDate, today)
    // 有效逾期边界的标签：有截止日期时是它，没有截止日期时退到计划日期。
    val effectiveLabel = Schedule.overdueLabel(
        nowMillis = nowMillis,
        plannedDate = task.plannedDate,
        dueDate = task.dueDate,
        dueTime = task.dueTime,
        zone = zone
    )

    val text = when {
        task.plannedDate != null && task.dueDate != null -> {
            val start = dateLabelText(plannedLabel)
            val end = dateLabelText(effectiveLabel)
            if (start != null && end != null) stringResource(R.string.date_range, start, end) else end
        }

        // A finished task that never had a date still shows when it was finished.
        else -> dateLabelText(effectiveLabel) ?: task.completedAt?.let { completedAtText(it, zone) }
    }
    val color = when {
        completed -> colors.doneInk
        overdue -> colors.dateOverdue
        effectiveLabel == DateLabel.Today || plannedLabel == DateLabel.Today -> colors.dateToday
        else -> colors.dateMuted
    }
    return text to color
}

/**
 * One plain date as it reads on the editor and the detail screen: 今天 / 明天 /
 * 昨天 when it is close to the given day, otherwise the month and day.
 */
@Composable
internal fun calendarDateText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)
}

/**
 * One small chip for a tag: the tag's own colour as a solid fill with its name in
 * a contrasting ink. The same shape is used on the task card, in the detail
 * screen and in the tag management page.
 *
 * 原来是「15% 透明底 + 同色字」，颜色太淡，一条任务卡上几个标签几乎看不出区别；
 * 改成实心色底后每个标签都很显眼，字色用 [onPriorityColor] 按底色亮度挑深/白。
 */
@Composable
fun TagChip(name: String, color: Int, modifier: Modifier = Modifier) {
    val chip = Color(color)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(chip)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = onPriorityColor(chip),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 统计入口的图标：三根高低不同的柱子。手画而不用图标库里的柱状图，是因为这个项目只
 * 依赖 material-icons-core，那里面没有图表类的图标。
 */
@Composable
fun StatsGlyph(tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(0.45f, 0.72f, 1f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}

/** A row of tag chips; [tags] is already ordered, only the first [max] are drawn. */
@Composable
fun TagChipRow(tags: List<Tag>, modifier: Modifier = Modifier, max: Int = 3) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(max).forEach { tag ->
            TagChip(name = tag.name, color = tag.color)
        }
    }
}

/**
 * 筛选面板里一个可勾选的胶囊：选中的填 [accent] 的浅色底并描边，没选中的是中性灰。
 * 它是筛选维度的最小单位，清单 / 优先级 / 标签三种维度共用。一行放不下时由调用方
 * 用横向滚动装下，不需要测量换行。
 */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else colors.groupHeader)
            .then(
                if (selected) Modifier.border(1.dp, accent, RoundedCornerShape(8.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * One task as the screenshots show it: a white rounded card with a coloured bar
 * on its left edge, a square checkbox, the title, and on the right the date the
 * task belongs to - blue when that date is today, grey otherwise, red once it is
 * overdue - with the remaining time below it while a due moment is still ahead.
 *
 * [onCheckedChange] null means "no checkbox" (the trash lists rows that cannot be
 * toggled), [actionLabel]/[onAction] add the single secondary action of the
 * current view (delete / restore / reopen).
 */
@Composable
fun TaskCard(
    task: Task,
    subtaskCount: Int,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    onClick: () -> Unit,
    nowMillis: Long,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tags: List<Tag> = emptyList(),
    /**
     * 卡片最右侧那个 ▶：「开始专注」。给了才画——回收站、已完成这类列表没有它。
     */
    onQuickFocus: (() -> Unit)? = null
) {
    val colors = LocalTodoColors.current
    val completed = task.completedAt != null

    val (dateText, dateColor) = taskDateLabel(task, nowMillis, zone)

    val countdown = if (completed) {
        Countdown.None
    } else {
        Schedule.countdownTo(nowMillis, task.dueDate, task.dueTime, zone)
    }
    val countdownLabel = countdownText(countdown)

    val barColor = priorityColor(task.priority)
    val titleColor = if (completed) colors.doneInk else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.5.dp,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(CardShape)
            .background(colors.card)
            .drawBehind {
                val inset = BarInset.toPx()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(0f, inset),
                    size = Size(BarWidth.toPx(), (size.height - inset * 2).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(BarWidth.toPx() / 2f)
                )
            }
            .clickable(onClick = onClick)
            // 色条现在只有 3dp，左边的内边距也收窄一点；上下各降 2dp，抵消勾选钮
            // 那圈 32dp 触控区带来的高度增长，卡片总高基本不变。
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onCheckedChange != null) {
            CircleCheck(
                checked = checked == true,
                onCheckedChange = onCheckedChange,
                fill = if (completed) colors.doneInk else colors.accent
            )
        } else {
            Spacer(Modifier.width(CheckTouchSize))
        }
        Spacer(Modifier.width(2.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (task.repeatRule != null || task.recurrenceSourceId != null) {
                    Spacer(Modifier.width(4.dp))
                    RepeatMark()
                }
            }
            if (subtaskCount > 0) {
                Text(
                    text = stringResource(R.string.task_subtask_count, subtaskCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                TagChipRow(tags = tags)
            }
        }

        if (dateText != null || countdownLabel != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (dateText != null) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = dateColor,
                        maxLines = 1
                    )
                }
                if (countdownLabel != null) {
                    Text(
                        text = countdownLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted,
                        maxLines = 1
                    )
                }
            }
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(actionLabel)
            }
        }

        // 「开始专注」：点一下把这条任务带进专注页。做成卡片上的一个小按钮、而不是再占一个
        // 滑动手势——卡片两个方向已经给了「完成」（右滑）和「删除」（左滑）。
        if (onQuickFocus != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onQuickFocus,
                modifier = Modifier.testTag(quickFocusTag(task.id))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.task_action_quick_focus),
                    tint = colors.accent
                )
            }
        }
    }
}

/**
 * 重复任务的小标记：标题末尾一个循环符号，不占一整行。
 *
 * 规则源（`repeatRule != null`）与它生成的那一次（`recurrenceSourceId != null`）都算
 * 重复任务，用户在一长串任务里一眼能认出来。
 */
@Composable
internal fun RepeatMark(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.task_repeat_mark)
    Text(
        text = "🔁",
        fontSize = 12.sp,
        modifier = modifier.semantics { contentDescription = label }
    )
}

/**
 * 任务卡上的勾选钮：一个 22dp 的圆形描边钮，勾上就填成实心，外面留一圈 32dp 的触控区
 * （Material3 的方形 Checkbox 自带最小触控区，换成自画的方框就得自己补回来）。
 *
 * 用它替掉 Material3 的方形 Checkbox——稿子⑤里方形滑块跟这套圆角卡片不是一路。
 *
 * 用的是 `toggleable` 而不是 `clickable`，语义上仍然是一个可勾选的控件（读屏和
 * 冒烟测试里的 `isToggleable()` 都还认得它）。
 */
@Composable
private fun CircleCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    fill: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = modifier
            .size(CheckTouchSize)
            .clip(CircleShape)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(CheckSize)
                .clip(CircleShape)
                .background(if (checked) fill else Color.Transparent)
                .border(1.7.dp, if (checked) fill else colors.checkOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * The same card, plus the two swipe gestures of the screenshots: swiping right
 * finishes the task, swiping left deletes it. The card itself never disappears -
 * it springs back, and the action (with its undo) is handled by the caller.
 */
@Composable
fun SwipeableTaskCard(
    task: Task,
    subtaskCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    nowMillis: Long,
    zone: ZoneId,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    tags: List<Tag> = emptyList(),
    /** 卡片上的「开始专注」；给 null 就不画。 */
    onQuickFocus: (() -> Unit)? = null
) {
    val colors = LocalTodoColors.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeComplete()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never settle into a dismissed state: the row is not removed here,
            // it returns to its place while the task itself is already updated.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val background = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> colors.accent
                SwipeToDismissBoxValue.EndToStart -> colors.dateOverdue
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardShape)
                    .background(background),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Delete
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    ) {
        TaskCard(
            task = task,
            subtaskCount = subtaskCount,
            checked = checked,
            onCheckedChange = onCheckedChange,
            onClick = onClick,
            nowMillis = nowMillis,
            zone = zone,
            tags = tags,
            onQuickFocus = onQuickFocus
        )
    }
}

/** 任务卡上「开始专注」那个按钮的测试钩子。 */
internal fun quickFocusTag(taskId: Long): String = "quick-focus-$taskId"

/** The date a task was finished on, for a task that never had a date of its own. */
@Composable
private fun completedAtText(completedAt: Long, zone: ZoneId): String {
    val date = Schedule.todayOf(completedAt, zone)
    return stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)
}

/** Simple single line text dialog used for "new list" and "rename list". */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    initialIcon: String = NoIcon,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var icon by remember { mutableStateOf(initialIcon) }
    val colors = LocalTodoColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 图标格子换成一个有高度的网格后，弹层在横屏 / 小屏上可能装不下：整块可滚。
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Spacer(Modifier.height(6.dp))
                IconPicker(selected = icon, onPick = { icon = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value, icon) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private val IconChipSize = 40.dp
private val IconChipGap = 6.dp

/**
 * 按**种类**浏览图标：上面一排是「工作 / 学习 / 饮食……」页签，下面是这个种类的图标格子，
 * 第一个格子是「无」（清空）。同一个图标在清单和习惯里都能选。
 *
 * 只负责「选中了哪个」，落库时机由上层决定（清单弹层是点 ✓ 才写，习惯弹层同理）。
 *
 * 格子高度按当前种类的图标数算：每类 14 个 + 「无」= 15 格，常见宽度下 3 行，弹层不会
 * 被撑到屏幕外；页签本身横向滚动，15 个种类也不会挤成两行。
 */
@Composable
internal fun IconPicker(
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    preferredCategory: IconCategory = IconCategory.WORK
) {
    val initialCategory = IconCategory.entries.firstOrNull { selected in it.icons }
        ?: preferredCategory
    var category by remember { mutableStateOf(initialCategory) }
    val tabState = rememberLazyListState()
    // 打开时把当前图标所在的页签滚到能看见的位置（动物、节日这些排在后面的种类）。
    LaunchedEffect(initialCategory) {
        tabState.scrollToItem(initialCategory.ordinal)
    }
    val colors = LocalTodoColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = tabState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(IconCategory.entries, key = { it.name }) { option ->
                TextButton(
                    onClick = { category = option },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (category == option) colors.accent else colors.dateMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // 老版本存下来的图标可能不在新目录里（比如 ☕、🎧）：把它临时放进当前这一页，
        // 至少用户打开弹层能看见「现在用的是哪个」，而不是一片没选中。
        val legacySelected = selected.takeIf {
            it.isNotBlank() && category == initialCategory && it !in category.icons
        }
        val icons = if (legacySelected != null) {
            listOf(legacySelected) + category.icons
        } else {
            category.icons
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 一行能放几格：按实际宽度算，弹层 / 底部弹层宽度不同也不会留一条空边。
            val columns = ((maxWidth + IconChipGap) / (IconChipSize + IconChipGap))
                .toInt()
                .coerceAtLeast(1)
            val cells = icons.size + 1
            val rows = (cells + columns - 1) / columns
            val gridHeight = IconChipSize * rows + IconChipGap * (rows - 1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(IconChipGap),
                verticalArrangement = Arrangement.spacedBy(IconChipGap),
                userScrollEnabled = false
            ) {
                items(count = cells) { index ->
                    if (index == 0) {
                        IconChoiceChip(
                            text = stringResource(R.string.icon_none),
                            emoji = false,
                            selected = selected.isEmpty(),
                            onPick = { onPick(NoIcon) }
                        )
                    } else {
                        val icon = icons[index - 1]
                        IconChoiceChip(
                            text = icon,
                            emoji = true,
                            selected = selected == icon,
                            onPick = { onPick(icon) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconChoiceChip(
    text: String,
    emoji: Boolean,
    selected: Boolean,
    onPick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(IconChipSize)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.groupHeader)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            // emoji 走默认字形（自带颜色），「无」按小标签写。
            fontSize = if (emoji) 20.sp else 12.sp,
            style = if (emoji) LocalTextStyle.current else MaterialTheme.typography.labelSmall,
            color = if (emoji) Color.Unspecified else if (selected) colors.accent else colors.dateMuted,
            maxLines = 1
        )
    }
}

/** 「清单 / 习惯」两格分段开关：测试钩子。 */
internal const val ListsHabitsSwitchTag = "lists-habits-switch"

/**
 * 顶栏上那个「清单 / 习惯」两格分段开关（用户 2026-10-18 要的）。
 *
 * 两枚图标合成一个胶囊，选中的那一格用强调色底；点「习惯」进习惯页、点「清单」回到
 * 任务列表。习惯入口因此从左侧面板里搬到了这里（抽屉里那行与更多页那行都撤掉了）。
 */
@Composable
fun ListsHabitsSwitch(
    onLists: Boolean,
    onSelectLists: () -> Unit,
    onSelectHabits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.groupHeader)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwitchCell(
            icon = Icons.AutoMirrored.Filled.List,
            description = stringResource(R.string.switch_lists),
            selected = onLists,
            tag = "$ListsHabitsSwitchTag-lists",
            onClick = onSelectLists
        )
        SwitchCell(
            icon = Icons.Default.CheckCircle,
            description = stringResource(R.string.switch_habits),
            selected = !onLists,
            tag = "$ListsHabitsSwitchTag-habits",
            onClick = onSelectHabits
        )
    }
}

@Composable
private fun SwitchCell(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(SwitchCellSize)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.card else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.dateMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val SwitchCellSize = 28.dp

/**
 * Confirmation shown before deleting a parent task that still has subtasks:
 * the spec (section 4.3) requires the user to be told that the subtasks go to
 * the trash together with their parent. It is only shown when subtasks exist -
 * a plain task keeps the plain "delete and undo" flow of section 4.2.
 */
@Composable
fun DeleteTaskConfirmDialog(
    taskTitle: String,
    subtaskCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_task_title)) },
        text = { Text(stringResource(R.string.dialog_delete_task_message, taskTitle, subtaskCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.dialog_delete_task_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun ListPicker(
    lists: List<TaskList>,
    selectedListId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = lists.firstOrNull { it.id == selectedListId }?.name.orEmpty()

    Column(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        onSelect(list.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
