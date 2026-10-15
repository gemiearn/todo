package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.model.Priority
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

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

internal fun calendarBodyTag(view: CalendarView): String = "calendar-body-${view.name.lowercase()}"

internal fun calendarViewOptionTag(view: CalendarView): String = "calendar-view-option-${view.name.lowercase()}"

/** The header of one day column of the 周 / 三日 views. */
internal fun calendarDayColumnTag(date: LocalDate): String = "calendar-day-column-${date.toEpochDay()}"

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

/** The day circle of one mini month of the year view. */
private val MiniDaySize = 18.dp

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

    Column(modifier = modifier.fillMaxWidth()) {
        CalendarHeader(
            title = calendarTitle(view, selectedDate, visibleMonth),
            subtitle = if (view == CalendarView.MONTH) {
                stringResource(R.string.calendar_year, visibleMonth.year)
            } else {
                null
            },
            view = view,
            timelineOn = timelineOn,
            onToggleTimeline = onToggleTimeline,
            onSelectView = onSelectView,
            onOpenLists = onOpenLists
        )

        when (view) {
            CalendarView.MONTH -> MonthView(
                selectedDate = selectedDate,
                today = today,
                startMonth = startMonth,
                pagerState = pagerState,
                tasksInRange = tasksInRange,
                onSelectDate = onSelectDate
            )

            CalendarView.WEEK -> DayColumnsView(
                view = CalendarView.WEEK,
                days = weekOf(selectedDate),
                selectedDate = selectedDate,
                today = today,
                tasksInRange = tasksInRange,
                onSelectDate = onSelectDate,
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f)
            )

            CalendarView.THREE_DAY -> DayColumnsView(
                view = CalendarView.THREE_DAY,
                days = (0L..2L).map { threeDayStart.plusDays(it) },
                selectedDate = selectedDate,
                today = today,
                tasksInRange = tasksInRange,
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
                tasksInRange = tasksInRange,
                onPickDate = {
                    onSelectDate(it)
                    onSelectView(CalendarView.MONTH)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The Monday the week of [date] starts on. */
private fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return (0L..6L).map { monday.plusDays(it) }
}

// ---------------------------------------------------------------- the header

@Composable
private fun CalendarHeader(
    title: String,
    subtitle: String?,
    view: CalendarView,
    timelineOn: Boolean,
    onToggleTimeline: () -> Unit,
    onSelectView: (CalendarView) -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The reference has no hamburger here, but 清单 and the two smart views
        // have to stay reachable from this tab.
        IconButton(onClick = onOpenLists) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.action_open_lists)
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

        // Only the month view has a day panel to lay out, so only there does the
        // timeline switch have anything to switch.
        if (view == CalendarView.MONTH) {
            IconButton(
                onClick = onToggleTimeline,
                modifier = Modifier
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
private fun calendarTitle(view: CalendarView, selectedDate: LocalDate, visibleMonth: YearMonth): String =
    when (view) {
        CalendarView.MONTH -> stringResource(monthNameRes(visibleMonth.monthValue))
        CalendarView.YEAR -> stringResource(R.string.calendar_year, selectedDate.year)
        CalendarView.WEEK -> stringResource(
            R.string.date_range,
            monthDayText(weekOf(selectedDate).first()),
            monthDayText(weekOf(selectedDate).last())
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
            DayOfWeek.entries.forEach { dayOfWeek ->
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
    onSelectDate: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    // The week starts on Monday, as the screenshots show it.
    val firstCell = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())

    val monthTasks by remember(month, tasksInRange) {
        tasksInRange(firstOfMonth, month.atEndOfMonth())
    }.collectAsStateWithLifecycle(emptyList())
    val dotsByDate = remember(monthTasks, month) {
        tasksByDate(monthTasks, firstOfMonth, month.atEndOfMonth())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        repeat(WeeksPerMonth) { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(DaysPerWeek) { dayOfWeek ->
                    val date = firstCell.plusDays((week * DaysPerWeek + dayOfWeek).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        selected = date == selectedDate,
                        today = date == today,
                        dots = if (YearMonth.from(date) == month) dotsByDate[date].orEmpty() else emptyList(),
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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
                        .background(dotColor(task.priority))
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
                .background(dotColor(task.priority))
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
 * 年: twelve mini months. A tap on a day is a way in, not a place to stay, so it
 * selects that day and returns to the month view.
 */
@Composable
private fun YearView(
    year: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)

    val yearTasks by remember(year, tasksInRange) {
        tasksInRange(yearStart, yearEnd)
    }.collectAsStateWithLifecycle(emptyList())
    val tasksByDay = remember(yearTasks, year) { tasksByDate(yearTasks, yearStart, yearEnd) }

    Column(modifier = modifier.fillMaxWidth().testTag(calendarBodyTag(CalendarView.YEAR))) {
        repeat(YearRows) { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(YearColumns) { column ->
                    MiniMonth(
                        month = YearMonth.of(year, row * YearColumns + column + 1),
                        tasksByDay = tasksByDay,
                        selectedDate = selectedDate,
                        today = today,
                        onPickDate = onPickDate,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** One of the twelve small months of the year view; a day with tasks is blue. */
@Composable
private fun MiniMonth(
    month: YearMonth,
    tasksByDay: Map<LocalDate, List<Task>>,
    selectedDate: LocalDate,
    today: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val firstOfMonth = month.atDay(1)
    val firstCell = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())

    Column(modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
            text = stringResource(monthNameRes(month.monthValue)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted,
            maxLines = 1
        )
        repeat(WeeksPerMonth) { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(DaysPerWeek) { dayOfWeek ->
                    val date = firstCell.plusDays((week * DaysPerWeek + dayOfWeek).toLong())
                    MiniDay(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        selected = date == selectedDate,
                        today = date == today,
                        hasTasks = tasksByDay.containsKey(date),
                        onClick = { onPickDate(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDay(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    // A day of a neighbouring month only keeps the grid in shape: it carries no
    // circle and is not clickable, which holds the year view - twelve months at
    // once - down to the nodes it actually needs.
    if (!inMonth) {
        Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
        }
        return
    }

    Box(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(MiniDaySize)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .then(
                    if (today && !selected) Modifier.border(1.dp, colors.accent, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    selected -> Color.White
                    today || hasTasks -> colors.accent
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** The colour of a task dot: the same priority colours a task card carries. */
@Composable
private fun dotColor(priority: Priority): Color {
    val colors = LocalTodoColors.current
    return when (priority) {
        Priority.NORMAL -> colors.barNormal
        Priority.HIGH -> colors.barHigh
        Priority.URGENT -> colors.barUrgent
    }
}

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

private fun weekdayNarrowRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_narrow_mon
    DayOfWeek.TUESDAY -> R.string.weekday_narrow_tue
    DayOfWeek.WEDNESDAY -> R.string.weekday_narrow_wed
    DayOfWeek.THURSDAY -> R.string.weekday_narrow_thu
    DayOfWeek.FRIDAY -> R.string.weekday_narrow_fri
    DayOfWeek.SATURDAY -> R.string.weekday_narrow_sat
    DayOfWeek.SUNDAY -> R.string.weekday_narrow_sun
}
