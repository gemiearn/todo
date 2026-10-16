package com.personal.todo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.CalendarSettings
import com.personal.todo.data.db.Task
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskFilter
import com.personal.todo.model.filterByRange
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The four ways the 日历 page can lay out time, as the user's reference (视图.jpg)
 * asks for: a year of mini months, one month, one week or three days. 月 is the
 * default, and the 周 / 三日 views are "a column per day with its entries" - the
 * tasks have no duration, so drawing them as hour sized blocks would be inventing
 * a length they do not have.
 */
enum class CalendarView {
    YEAR,
    MONTH,
    WEEK,
    THREE_DAY
}

/** Tags the smoke tests use to drive the calendar, whose buttons carry no text. */
internal const val CalendarTimelineTag = "calendar-timeline"
internal const val CalendarViewSwitchTag = "calendar-view-switch"
internal const val CalendarDisplayOptionsTag = "calendar-display-options"
internal const val CalendarStepPreviousTag = "calendar-step-previous"
internal const val CalendarStepNextTag = "calendar-step-next"

internal fun calendarBodyTag(view: CalendarView): String = "calendar-body-${view.name.lowercase()}"

internal fun calendarViewOptionTag(view: CalendarView): String = "calendar-view-option-${view.name.lowercase()}"

/** The header of one day column of the 周 / 三日 views. */
internal fun calendarDayColumnTag(date: LocalDate): String = "calendar-day-column-${date.toEpochDay()}"

/** One task dot of one day of the month grid: the smoke tests read the span off it. */
internal fun dayDotTag(date: LocalDate, taskId: Long): String = "dot-${date.toEpochDay()}-$taskId"

/** How many months can be swiped before and after the month the screen opens on. */
private const val MonthsAround = 60

/** Six rows of days always fit: a month needs at most six weeks. */
private const val WeeksPerMonth = 6

private const val RowHeightDp = 46
private const val GridHeightDp = RowHeightDp * WeeksPerMonth

/** How many task dots a day shows at most, before it would get crowded. */
private const val MaxDotsPerDay = 3

private val DotSize = 5.dp

private const val DaysPerWeek = 7
private const val YearColumns = 3
private const val YearRows = 4

/** 年视图里小月份名那一行的高度；它下面才是 6×7 的日期格子。 */
private val MiniMonthLabelHeight = 14.dp

/** 月份名与它所在格左边缘的距离。 */
private val MonthLabelGap = 4.dp

/** Width of the left column that carries the week numbers when they are shown. */
private val WeekNumberWidth = 26.dp

/**
 * The 日历 page: a header that names the period and carries the 时间线 toggle and
 * the view switch, then the period itself - a month grid, a column per day, or a
 * year of mini months. Below the month grid the caller draws the selected day
 * (its list, or its timeline).
 *
 * Every view only reads the stored dates - selecting another day never changes a
 * task's date.
 */
@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    today: LocalDate,
    view: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    timelineOn: Boolean,
    onToggleTimeline: () -> Unit,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    displaySettings: CalendarSettings,
    filter: TaskFilter,
    tagIdsByTask: Map<Long, Set<Long>>,
    onOpenDisplayOptions: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenLists: () -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // The pager state lives here rather than inside the month view, because the
    // header shows the year of the month the grid is currently on. Both halves of
    // "which month is the grid on" have to come back together: the page is a
    // saveable reading, so the anchor it is counted against is saved too, or a
    // return to the tab would show a month nobody selected for a frame. The anchor
    // is kept as the epoch day of its first day - a plain Long, which is the shape
    // rememberSaveable saves without a second thought.
    val anchorEpochDay by rememberSaveable {
        mutableLongStateOf(YearMonth.from(selectedDate).minusMonths(MonthsAround.toLong()).atDay(1).toEpochDay())
    }
    val startMonth = remember(anchorEpochDay) { YearMonth.from(LocalDate.ofEpochDay(anchorEpochDay)) }
    val pagerState = rememberPagerState(initialPage = MonthsAround, pageCount = { MonthsAround * 2 + 1 })
    val visibleMonth = startMonth.plusMonths(pagerState.currentPage.toLong())

    // 三日 keeps the window it was opened on: tapping a day inside it must not
    // slide the three days out from under the finger. It only re-anchors when the
    // day the rest of the app has selected falls outside the window.
    var threeDayStartEpoch by rememberSaveable { mutableLongStateOf(selectedDate.toEpochDay()) }
    val threeDayStart = LocalDate.ofEpochDay(threeDayStartEpoch)
    LaunchedEffect(selectedDate, view) {
        if (view == CalendarView.THREE_DAY && (
                selectedDate.isBefore(threeDayStart) ||
                    selectedDate.isAfter(threeDayStart.plusDays(2))
                )
        ) {
            threeDayStartEpoch = selectedDate.toEpochDay()
        }
    }

    // 显示范围与「显示已完成」都只作用在日历画的这一份数据上：设置要求隐藏已完成的
    // 就先丢掉它们，再按筛选范围收窄。日历本身只读，这里改的只是「画什么」。
    val filteredRange: (LocalDate, LocalDate) -> Flow<List<Task>> = remember(
        tasksInRange, displaySettings.showCompleted, filter, tagIdsByTask
    ) {
        { start, end ->
            tasksInRange(start, end).map { tasks ->
                visibleCalendarTasks(
                    tasks = tasks,
                    showCompleted = displaySettings.showCompleted,
                    filter = filter,
                    tagIdsByTask = tagIdsByTask
                )
            }
        }
    }

    val weekStartsMonday = displaySettings.weekStartsMonday

    // 周 / 三日 / 年 的翻页只挪选中的日期，不动任何任务的日期；月视图靠左右滑。
    val onStep: (Int) -> Unit = { step ->
        onSelectDate(
            when (view) {
                CalendarView.WEEK -> selectedDate.plusWeeks(step.toLong())
                CalendarView.THREE_DAY -> selectedDate.plusDays(3L * step)
                CalendarView.YEAR -> selectedDate.plusYears(step.toLong())
                CalendarView.MONTH -> selectedDate
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CalendarHeader(
            title = calendarTitle(view, selectedDate, visibleMonth, weekStartsMonday),
            subtitle = if (view == CalendarView.MONTH) {
                stringResource(R.string.calendar_year, visibleMonth.year)
            } else {
                null
            },
            view = view,
            timelineOn = timelineOn,
            onToggleTimeline = onToggleTimeline,
            onStep = onStep,
            onSelectView = onSelectView,
            onOpenDisplayOptions = onOpenDisplayOptions,
            onOpenLists = onOpenLists
        )

        when (view) {
            CalendarView.MONTH -> MonthView(
                selectedDate = selectedDate,
                today = today,
                startMonth = startMonth,
                pagerState = pagerState,
                tasksInRange = filteredRange,
                weekStartsMonday = weekStartsMonday,
                showWeekNumbers = displaySettings.showWeekNumbers,
                onSelectDate = onSelectDate
            )

            CalendarView.WEEK -> DayColumnsView(
                view = CalendarView.WEEK,
                days = weekOf(selectedDate, weekStartsMonday),
                selectedDate = selectedDate,
                today = today,
                tasksInRange = filteredRange,
                onSelectDate = onSelectDate,
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f)
            )

            CalendarView.THREE_DAY -> DayColumnsView(
                view = CalendarView.THREE_DAY,
                days = (0L..2L).map { threeDayStart.plusDays(it) },
                selectedDate = selectedDate,
                today = today,
                tasksInRange = filteredRange,
                onSelectDate = onSelectDate,
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f)
            )

            // A day of the year view is a way in: it selects that day and drops
            // back to the month view, which is where a day can be worked on.
            CalendarView.YEAR -> YearView(
                year = selectedDate.year,
                selectedDate = selectedDate,
                today = today,
                tasksInRange = filteredRange,
                weekStartsMonday = weekStartsMonday,
                onPickDate = {
                    onSelectDate(it)
                    onSelectView(CalendarView.MONTH)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The rows the calendar actually draws: with 显示已完成 off the finished ones are
 * dropped, then the 显示范围 filter narrows what is left. Read only - the stored
 * dates of every task are untouched.
 */
private fun visibleCalendarTasks(
    tasks: List<Task>,
    showCompleted: Boolean,
    filter: TaskFilter,
    tagIdsByTask: Map<Long, Set<Long>>
): List<Task> = filterByRange(
    tasks = if (showCompleted) tasks else tasks.filter { it.completedAt == null },
    filter = filter,
    tagIdsOfTask = { tagIdsByTask[it].orEmpty() }
)

/** The weekdays the month grid and the 周 view put left to right. */
private fun orderedWeekdays(weekStartsMonday: Boolean): List<DayOfWeek> =
    if (weekStartsMonday) {
        DayOfWeek.entries.toList()
    } else {
        listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
    }

/** The Monday the week of [date] starts on, or the Sunday when a week starts then. */
private fun weekOf(date: LocalDate, weekStartsMonday: Boolean): List<LocalDate> {
    val dayOfWeek = date.dayOfWeek
    val offset = if (weekStartsMonday) dayOfWeek.value - 1 else dayOfWeek.value % 7
    val first = date.minusDays(offset.toLong())
    return (0L..6L).map { first.plusDays(it) }
}

// ---------------------------------------------------------------- the header

@Composable
private fun CalendarHeader(
    title: String,
    subtitle: String?,
    view: CalendarView,
    timelineOn: Boolean,
    onToggleTimeline: () -> Unit,
    onStep: (Int) -> Unit,
    onSelectView: (CalendarView) -> Unit,
    onOpenDisplayOptions: () -> Unit,
    onOpenLists: () -> Unit
) {
    val colors = LocalTodoColors.current
    var viewMenuOpen by remember { mutableStateOf(false) }
    // The toggle names what it will switch to, so the button never has to be read
    // as an abstract glyph.
    val timelineDescription = stringResource(
        if (timelineOn) R.string.action_timeline_off else R.string.action_timeline_on
    )
    val switchViewDescription = stringResource(R.string.action_switch_view)
    val displayOptionsDescription = stringResource(R.string.calendar_display_options)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The reference has no hamburger here, but 清单 and the two smart views
        // have to stay reachable from this tab.
        IconButton(onClick = onOpenLists, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.action_open_lists)
            )
        }

        // 周 / 三日 / 年 有一个会挪动的周期，所以给一对前后箭头；月视图靠左右滑。
        if (view != CalendarView.MONTH) {
            StepArrow(
                forward = false,
                description = stringResource(R.string.calendar_step_previous),
                tag = CalendarStepPreviousTag,
                onClick = { onStep(-1) }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
            }
        }

        if (view != CalendarView.MONTH) {
            StepArrow(
                forward = true,
                description = stringResource(R.string.calendar_step_next),
                tag = CalendarStepNextTag,
                onClick = { onStep(1) }
            )
        }

        // Only the month view has a day panel to lay out, so only there does the
        // timeline switch have anything to switch.
        if (view == CalendarView.MONTH) {
            IconButton(
                onClick = onToggleTimeline,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(CalendarTimelineTag)
                    .semantics { contentDescription = timelineDescription }
            ) {
                TimelineGlyph(
                    tint = if (timelineOn) colors.accent else colors.dateMuted
                )
            }
        }
        Box {
            IconButton(
                onClick = { viewMenuOpen = true },
                modifier = Modifier
                    .size(40.dp)
                    .testTag(CalendarViewSwitchTag)
                    .semantics { contentDescription = switchViewDescription }
            ) {
                ViewGlyph(tint = colors.dateMuted)
            }
            DropdownMenu(expanded = viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                CalendarView.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(calendarViewLabelRes(option))) },
                        onClick = {
                            onSelectView(option)
                            viewMenuOpen = false
                        },
                        modifier = Modifier.testTag(calendarViewOptionTag(option))
                    )
                }
            }
        }

        // ⋮ 打开「显示范围 + 显示设置」面板：筛选因子和日期显示方式都在里面。
        IconButton(
            onClick = onOpenDisplayOptions,
            modifier = Modifier
                .size(40.dp)
                .testTag(CalendarDisplayOptionsTag)
                .semantics { contentDescription = displayOptionsDescription }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = colors.dateMuted
            )
        }
    }
}

/** One of the two small arrows that step the 周 / 三日 / 年 periods. */
@Composable
private fun StepArrow(forward: Boolean, description: String, tag: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = if (forward) {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            },
            contentDescription = null,
            tint = LocalTodoColors.current.dateMuted
        )
    }
}

/** 时间线: three stacked rules of different length, one entry each. */
@Composable
private fun TimelineGlyph(tint: Color) {
    Column(modifier = Modifier.size(18.dp), verticalArrangement = Arrangement.Center) {
        listOf(1f, 0.6f, 0.85f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(vertical = 1.5.dp)
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}

/** 切换视图: the four panes of a grid. */
@Composable
private fun ViewGlyph(tint: Color) {
    Column(modifier = Modifier.size(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.5.dp, tint, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun calendarTitle(
    view: CalendarView,
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    weekStartsMonday: Boolean
): String =
    when (view) {
        CalendarView.MONTH -> stringResource(monthNameRes(visibleMonth.monthValue))
        CalendarView.YEAR -> stringResource(R.string.calendar_year, selectedDate.year)
        CalendarView.WEEK -> stringResource(
            R.string.date_range,
            monthDayText(weekOf(selectedDate, weekStartsMonday).first()),
            monthDayText(weekOf(selectedDate, weekStartsMonday).last())
        )

        CalendarView.THREE_DAY -> stringResource(
            R.string.date_range,
            monthDayText(selectedDate),
            monthDayText(selectedDate.plusDays(2))
        )
    }

@Composable
private fun monthDayText(date: LocalDate): String =
    stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)

private fun calendarViewLabelRes(view: CalendarView): Int = when (view) {
    CalendarView.YEAR -> R.string.calendar_view_year
    CalendarView.MONTH -> R.string.calendar_view_month
    CalendarView.WEEK -> R.string.calendar_view_week
    CalendarView.THREE_DAY -> R.string.calendar_view_three_day
}

// ---------------------------------------------------------------- month view

@Composable
private fun MonthView(
    selectedDate: LocalDate,
    today: LocalDate,
    startMonth: YearMonth,
    pagerState: PagerState,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    weekStartsMonday: Boolean,
    showWeekNumbers: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Coming back from the week / three day / year view with another day in hand
    // scrolls the grid to that day's month instead of leaving the user on a month
    // they no longer have selected. [startMonth] is already the selected month
    // minus [MonthsAround], so the page of the selected month is exactly the
    // distance between the two - adding the offset a second time threw the grid
    // five years ahead every time the tab was opened.
    LaunchedEffect(selectedDate) {
        val target = ChronoUnit.MONTHS.between(startMonth, YearMonth.from(selectedDate))
            .toInt()
            .coerceIn(0, MonthsAround * 2)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    // A month the user swiped to selects its first day, so the day panel under
    // the grid always belongs to the month shown above it. The very first settle
    // is the one the effect above is still lining up, so it is not a swipe.
    var settlesSeen by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.settledPage) {
        settlesSeen++
        if (settlesSeen == 1) return@LaunchedEffect
        val month = startMonth.plusMonths(pagerState.settledPage.toLong())
        if (YearMonth.from(selectedDate) != month) onSelectDate(month.atDay(1))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            // The week-number column keeps its own width so the day columns line up
            // with the grid below it.
            if (showWeekNumbers) Spacer(Modifier.width(WeekNumberWidth))
            orderedWeekdays(weekStartsMonday).forEach { dayOfWeek ->
                Text(
                    text = stringResource(weekdayNarrowRes(dayOfWeek)),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalTodoColors.current.dateMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(GridHeightDp.dp)
                .testTag(calendarBodyTag(CalendarView.MONTH)),
            key = { page -> page }
        ) { page ->
            MonthGrid(
                month = startMonth.plusMonths(page.toLong()),
                selectedDate = selectedDate,
                today = today,
                tasksInRange = tasksInRange,
                weekStartsMonday = weekStartsMonday,
                showWeekNumbers = showWeekNumbers,
                onSelectDate = onSelectDate
            )
        }
    }
}

/** One month of the grid: six weeks of day cells, task dots included. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    weekStartsMonday: Boolean,
    showWeekNumbers: Boolean,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    // The week the grid starts on: Monday by default, Sunday when the user asks.
    val firstCell = firstOfMonth.minusDays(weekStartOffset(firstOfMonth, weekStartsMonday))
    val lastCell = firstCell.plusDays((WeeksPerMonth * DaysPerWeek - 1).toLong())

    // The grid asks for the six weeks it draws, not just its own month: a span that
    // starts in the previous month has to keep its dots as they cross the edge
    // instead of stopping dead at the 1st.
    val monthTasks by remember(month, tasksInRange) {
        tasksInRange(firstCell, lastCell)
    }.collectAsStateWithLifecycle(emptyList())
    val dotsByDate = remember(monthTasks) { tasksByDate(monthTasks, firstCell, lastCell) }

    Column(modifier = Modifier.fillMaxSize()) {
        repeat(WeeksPerMonth) { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (showWeekNumbers) {
                    WeekNumberCell(
                        weekStart = firstCell.plusDays((week * DaysPerWeek).toLong()),
                        weekStartsMonday = weekStartsMonday
                    )
                }
                repeat(DaysPerWeek) { dayOfWeek ->
                    val date = firstCell.plusDays((week * DaysPerWeek + dayOfWeek).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        selected = date == selectedDate,
                        today = date == today,
                        dots = dotsByDate[date].orEmpty(),
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** How many days the first day of [date]'s month is past the start of its week. */
private fun weekStartOffset(date: LocalDate, weekStartsMonday: Boolean): Long =
    if (weekStartsMonday) (date.dayOfWeek.value - 1).toLong() else (date.dayOfWeek.value % 7).toLong()

/** The `W12` label of one row of the grid; numbering follows the ISO week of its Monday. */
@Composable
private fun WeekNumberCell(weekStart: LocalDate, weekStartsMonday: Boolean) {
    val monday = if (weekStartsMonday) weekStart else weekStart.plusDays(1)
    Box(
        modifier = Modifier.width(WeekNumberWidth).fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.calendar_week_prefix, monday.get(WeekFields.ISO.weekOfWeekBasedYear())),
            style = MaterialTheme.typography.labelSmall,
            color = LocalTodoColors.current.dateMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    dots: List<Task>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val textColor = when {
        selected -> Color.White
        today -> colors.accent
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> colors.dateMuted
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
        Row(
            modifier = Modifier.height(DotSize + 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dots.sortedByDescending { it.priority.ordinal }.take(MaxDotsPerDay).forEach { task ->
                Box(
                    modifier = Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(priorityColor(task.priority))
                        .testTag(dayDotTag(date, task.id))
                )
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}

// ------------------------------------------------- week and three day views

/**
 * 周 (seven columns) and 三日 (three): one column per day, the day's entries
 * listed under its header. Clicking a header selects that day, clicking an entry
 * opens the task - nothing here changes a date.
 */
@Composable
private fun DayColumnsView(
    view: CalendarView,
    days: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val start = days.first()
    val end = days.last()

    val periodTasks by remember(start, end, tasksInRange) {
        tasksInRange(start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val tasksByDay = remember(periodTasks, start, end) { tasksByDate(periodTasks, start, end) }

    Column(modifier = modifier.fillMaxWidth().testTag(calendarBodyTag(view))) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            days.forEach { date ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectDate(date) }
                        .testTag(calendarDayColumnTag(date))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(weekdayNarrowRes(date.dayOfWeek)),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (date == selectedDate) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                date == selectedDate -> Color.White
                                date == today -> colors.accent
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            days.forEach { date ->
                key(date) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (date == selectedDate) colors.groupHeader else Color.Transparent)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 3.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sortDayEntries(tasksByDay[date].orEmpty()).forEach { task ->
                            DayEntry(task = task, onClick = { onOpenTask(task.id) })
                        }
                    }
                }
            }
        }
    }
}

/** One task inside a day column: a card with its priority dot and, if set, its time. */
@Composable
private fun DayEntry(task: Task, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(priorityColor(task.priority))
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            task.plannedTime?.let { time ->
                Text(
                    text = clockTimeText(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/** The entries of one day column: planned time first, then the more urgent one. */
private fun sortDayEntries(tasks: List<Task>): List<Task> =
    tasks.sortedWith(
        compareBy({ it.plannedTime ?: LocalTime.MAX }, { -it.priority.ordinal })
    )

// ---------------------------------------------------------------- year view

/**
 * 年: twelve mini months, all drawn by a *single* Canvas.
 *
 * 原来是「12 个 MiniMonth，每个里 42 个 MiniDay 组合项」——一年 504 个节点，切到年视图
 * 要一两秒。改成一张 Canvas 之后，格子和数字都只是 draw 指令，节点数掉到 1，跟
 * [FocusStatsScreen] 的年度热力图是同一个路子。数字的字型量一次就够，31 个数字复用。
 *
 * A tap on a day is a way in, not a place to stay, so it selects that day and
 * returns to the month view; the hit test walks the same grid math the drawing does.
 */
@Composable
private fun YearView(
    year: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    weekStartsMonday: Boolean,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val yearTasks by remember(year, tasksInRange) {
        tasksInRange(yearStart, yearEnd)
    }.collectAsStateWithLifecycle(emptyList())
    val tasksByDay = remember(yearTasks, year) { tasksByDate(yearTasks, yearStart, yearEnd) }

    // 字型只量两样：1..31 这组日期数字，和十二个月份名。量一次整年复用，不必每格都量。
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.dateMuted)
    val monthNames = (1..12).map { stringResource(monthNameRes(it)) }
    val dayTexts = remember(measurer, labelStyle) {
        (1..31).map { measurer.measure(it.toString(), labelStyle) }
    }
    val monthLabels = remember(measurer, labelStyle, monthNames) {
        monthNames.map { measurer.measure(it, labelStyle) }
    }

    val accent = colors.accent
    val muted = colors.dateMuted
    val labelHeight = MiniMonthLabelHeight
    val todayRingWidth = 1.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .testTag(calendarBodyTag(CalendarView.YEAR))
            .pointerInput(year, weekStartsMonday) {
                detectTapGestures { offset ->
                    yearDateAt(
                        x = offset.x,
                        y = offset.y,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        labelHeight = labelHeight.toPx(),
                        year = year,
                        weekStartsMonday = weekStartsMonday
                    )?.let(onPickDate)
                }
            }
    ) {
        val blockWidth = size.width / YearColumns
        val blockHeight = size.height / YearRows
        val labelHeightPx = labelHeight.toPx()
        val cellWidth = blockWidth / DaysPerWeek
        val cellHeight = (blockHeight - labelHeightPx) / WeeksPerMonth
        val circleRadius = minOf(cellWidth, cellHeight) * 0.42f

        repeat(YearRows * YearColumns) { index ->
            val month = index + 1
            val left = (index % YearColumns) * blockWidth
            val top = (index / YearColumns) * blockHeight

            drawText(
                monthLabels[index],
                color = muted,
                topLeft = Offset(left + MonthLabelGap.toPx(), top)
            )

            val firstOfMonth = YearMonth.of(year, month).atDay(1)
            val firstCell = firstOfMonth.minusDays(weekStartOffset(firstOfMonth, weekStartsMonday))
            repeat(WeeksPerMonth) { week ->
                repeat(DaysPerWeek) { dayOfWeek ->
                    val date = firstCell.plusDays((week * DaysPerWeek + dayOfWeek).toLong())
                    val centerX = left + dayOfWeek * cellWidth + cellWidth / 2f
                    val centerY = top + labelHeightPx + week * cellHeight + cellHeight / 2f
                    val center = Offset(centerX, centerY)
                    val inMonth = YearMonth.from(date).monthValue == month
                    val isSelected = inMonth && date == selectedDate

                    if (isSelected) {
                        drawCircle(color = accent, radius = circleRadius, center = center)
                    } else if (inMonth && date == today) {
                        drawCircle(
                            color = accent,
                            radius = circleRadius,
                            center = center,
                            style = Stroke(width = todayRingWidth.toPx())
                        )
                    }

                    val text = dayTexts[date.dayOfMonth - 1]
                    val textColor = when {
                        !inMonth -> muted
                        isSelected -> Color.White
                        date == today || tasksByDay.containsKey(date) -> accent
                        else -> onSurface
                    }
                    drawText(
                        text,
                        color = textColor,
                        topLeft = Offset(
                            centerX - text.size.width / 2f,
                            centerY - text.size.height / 2f
                        )
                    )
                }
            }
        }
    }
}

/**
 * 年视图里一个点落在哪一天；落在月份名那一行、或点在整个网格之外时返回 null。
 *
 * 画和点必须用同一套格子算法，否则看上去点中的格子跟实际选中的那一天会错位——所以这里
 * 只有这一份几何计算，[YearView] 的 Canvas 和它的手势都调它。
 */
private fun yearDateAt(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    labelHeight: Float,
    year: Int,
    weekStartsMonday: Boolean
): LocalDate? {
    if (width <= 0f || height <= 0f || x < 0f || y < 0f || x >= width || y >= height) return null

    val blockWidth = width / YearColumns
    val blockHeight = height / YearRows
    val column = (x / blockWidth).toInt().coerceIn(0, YearColumns - 1)
    val row = (y / blockHeight).toInt().coerceIn(0, YearRows - 1)
    val month = row * YearColumns + column + 1

    // 月份名那一行只是标题，点它不算选日期。
    val withinBlockY = y - row * blockHeight
    if (withinBlockY < labelHeight) return null

    val cellWidth = blockWidth / DaysPerWeek
    val cellHeight = (blockHeight - labelHeight) / WeeksPerMonth
    if (cellHeight <= 0f) return null
    val week = ((withinBlockY - labelHeight) / cellHeight).toInt().coerceIn(0, WeeksPerMonth - 1)
    val day = ((x - column * blockWidth) / cellWidth).toInt().coerceIn(0, DaysPerWeek - 1)

    val firstOfMonth = YearMonth.of(year, month).atDay(1)
    val firstCell = firstOfMonth.minusDays(weekStartOffset(firstOfMonth, weekStartsMonday))
    val date = firstCell.plusDays((week * DaysPerWeek + day).toLong())
    // 邻月那几天只是撑住格子，点它们不算选择。
    return if (YearMonth.from(date).monthValue == month) date else null
}

// ---------------------------------------------------------------- shared bits

/**
 * Tasks grouped by every day they cover inside [start]..[end].
 *
 * A task that has both a planned date and a due date is drawn across the whole
 * interval between them, so a multi-day task is visible on each of its days
 * instead of only on its two endpoints. A task with a single date is drawn on
 * that day. This is a display decision only: no date of any task is changed, and
 * the 今日 view keeps its exact "planned or due today" rule.
 *
 * The interval is clamped to the range being drawn, so an unusually long span
 * (or a typo in a date) can never make a view walk through years of days.
 */
internal fun tasksByDate(
    tasks: List<Task>,
    start: LocalDate,
    end: LocalDate
): Map<LocalDate, List<Task>> {
    val byDate = mutableMapOf<LocalDate, MutableList<Task>>()

    tasks.forEach { task ->
        val planned = task.plannedDate
        val due = task.dueDate
        when {
            planned != null && due != null -> {
                var day = maxOf(minOf(planned, due), start)
                val last = minOf(maxOf(planned, due), end)
                while (!day.isAfter(last)) {
                    byDate.getOrPut(day) { mutableListOf() }.add(task)
                    day = day.plusDays(1)
                }
            }

            planned != null -> if (!planned.isBefore(start) && !planned.isAfter(end)) {
                byDate.getOrPut(planned) { mutableListOf() }.add(task)
            }

            due != null -> if (!due.isBefore(start) && !due.isAfter(end)) {
                byDate.getOrPut(due) { mutableListOf() }.add(task)
            }
        }
    }
    return byDate
}

private fun monthNameRes(month: Int): Int = when (month) {
    1 -> R.string.month_1
    2 -> R.string.month_2
    3 -> R.string.month_3
    4 -> R.string.month_4
    5 -> R.string.month_5
    6 -> R.string.month_6
    7 -> R.string.month_7
    8 -> R.string.month_8
    9 -> R.string.month_9
    10 -> R.string.month_10
    11 -> R.string.month_11
    else -> R.string.month_12
}

/** The single-character weekday names of the calendar header; the statistics page uses them too. */
internal fun weekdayNarrowRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_narrow_mon
    DayOfWeek.TUESDAY -> R.string.weekday_narrow_tue
    DayOfWeek.WEDNESDAY -> R.string.weekday_narrow_wed
    DayOfWeek.THURSDAY -> R.string.weekday_narrow_thu
    DayOfWeek.FRIDAY -> R.string.weekday_narrow_fri
    DayOfWeek.SATURDAY -> R.string.weekday_narrow_sat
    DayOfWeek.SUNDAY -> R.string.weekday_narrow_sun
}
