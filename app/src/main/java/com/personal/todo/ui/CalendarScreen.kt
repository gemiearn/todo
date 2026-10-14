package com.personal.todo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.model.Priority
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** How many months can be swiped before and after the month the screen opens on. */
private const val MonthsAround = 60

/** Six rows of days always fit: a month needs at most six weeks. */
private const val WeeksPerMonth = 6

private const val RowHeightDp = 46
private const val GridHeightDp = RowHeightDp * WeeksPerMonth

/** How many task dots a day shows at most, before it would get crowded. */
private const val MaxDotsPerDay = 3

private val DotSize = 5.dp

/**
 * The 日历 page of the screenshots: a large month title, the single character
 * weekday header, a swipeable month grid where today is a filled blue circle
 * and every day with tasks carries small coloured dots, and below the grid the
 * task list of the selected day (drawn by the caller).
 *
 * The grid only reads the stored dates - selecting another day never changes a
 * task's date.
 */
@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    today: LocalDate,
    tasksInRange: (LocalDate, LocalDate) -> Flow<List<Task>>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenLists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    // The month the screen opens on is anchored once, so rotation or a
    // recomposition does not scroll the grid away from where the user left it.
    val startMonth = remember { YearMonth.from(selectedDate).minusMonths(MonthsAround.toLong()) }
    val pagerState = rememberPagerState(initialPage = MonthsAround, pageCount = { MonthsAround * 2 + 1 })

    val visibleMonth = startMonth.plusMonths(pagerState.currentPage.toLong())

    // A month the user swiped to selects its first day, so the list under the
    // grid always belongs to the month shown above it.
    LaunchedEffect(pagerState.settledPage) {
        val month = startMonth.plusMonths(pagerState.settledPage.toLong())
        if (YearMonth.from(selectedDate) != month) onSelectDate(month.atDay(1))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // The screenshots have no hamburger here, but the 清单 and the two
            // smart views have to stay reachable from this tab.
            IconButton(onClick = onOpenLists) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.action_open_lists)
                )
            }
            Text(
                text = stringResource(monthNameRes(visibleMonth.monthValue)),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.calendar_year, visibleMonth.year),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            DayOfWeek.entries.forEach { dayOfWeek ->
                Text(
                    text = stringResource(weekdayNarrowRes(dayOfWeek)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(GridHeightDp.dp),
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
    val dotsByDate = remember(monthTasks) { tasksByDate(monthTasks) }

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

private const val DaysPerWeek = 7

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
 * Tasks grouped by the day they belong to. A task with both a planned and a due
 * date is listed on both days, because the 今日 view merges the same way.
 */
private fun tasksByDate(tasks: List<Task>): Map<LocalDate, List<Task>> {
    val byDate = mutableMapOf<LocalDate, MutableList<Task>>()
    tasks.forEach { task ->
        task.plannedDate?.let { byDate.getOrPut(it) { mutableListOf() }.add(task) }
        task.dueDate?.takeIf { it != task.plannedDate }
            ?.let { byDate.getOrPut(it) { mutableListOf() }.add(task) }
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
