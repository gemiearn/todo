package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.model.FocusDay
import com.personal.todo.model.FocusReport
import com.personal.todo.model.FocusTaskFocus
import com.personal.todo.model.FocusTotals
import com.personal.todo.model.focusReportOf
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** The tag the smoke tests use to reach the page's list. */
internal const val FocusStatsTag = "focus-stats"

/** The height a full-height bar of the trend chart reaches. */
private val BarMaxHeight = 84.dp

private val HeatCellHeight = 16.dp

/**
 * 专注统计: the page 滴答清单 and 番茄ToDo both put behind a 统计 icon.
 *
 * Three totals (今天 / 本周 / 累计), a week of daily trend, the tasks the time
 * actually went to, and a quarter of φ打卡 heatmap. Every number is derived from
 * the stored sessions on the spot, so a break - which is a phase of the timer and
 * never a session - cannot appear here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsScreen(
    sessions: Flow<List<FocusSession>>,
    zone: ZoneId,
    today: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val all by sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val report = remember(all, today, zone) { focusReportOf(all, today, zone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_entry_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(FocusStatsTag),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (all.isEmpty()) {
                item(key = "empty") { StatsCard(title = stringResource(R.string.more_entry_stats)) {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalTodoColors.current.dateMuted
                    )
                } }
            } else {
                item(key = "totals") { SummaryCards(report) }
                item(key = "trend") { TrendCard(days = report.lastDays, today = today) }
                item(key = "tasks") { TopTasksCard(tasks = report.topTasks) }
                item(key = "heatmap") { HeatmapCard(weeks = report.heatmapWeeks, today = today) }
            }
        }
    }
}

/** 今天 / 本周 / 累计，今天那张用番茄红标出来。 */
@Composable
private fun SummaryCards(report: FocusReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            label = stringResource(R.string.stats_card_today),
            totals = report.today,
            highlighted = true,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = stringResource(R.string.stats_card_week),
            totals = report.week,
            highlighted = false,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = stringResource(R.string.stats_card_total),
            totals = report.total,
            highlighted = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    totals: FocusTotals,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) colors.focusSurface else colors.card)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reportDuration(totals.seconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (highlighted) colors.focusAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.stats_pomodoros, totals.pomodoros),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted,
            maxLines = 1
        )
    }
}

/** 最近一周的柱状图：今天那根是番茄红，别的是浅轨道色；柱顶直接写分钟数。 */
@Composable
private fun TrendCard(days: List<FocusDay>, today: LocalDate) {
    val colors = LocalTodoColors.current
    val maxSeconds = days.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1

    StatsCard(title = stringResource(R.string.stats_trend_title)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarMaxHeight + 34.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (day.seconds >= 60) (day.seconds / 60).toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.dateMuted,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (day.seconds == 0) {
                                    3.dp
                                } else {
                                    maxOf(8.dp, BarMaxHeight * (day.seconds.toFloat() / maxSeconds))
                                }
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (day.date == today) colors.focusAccent else colors.focusTrack
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(weekdayNarrowRes(day.date.dayOfWeek)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.date == today) colors.focusAccent else colors.dateMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 时间花在哪个任务上，最多的排最前，每条下面一条占比条。 */
@Composable
private fun TopTasksCard(tasks: List<FocusTaskFocus>) {
    val colors = LocalTodoColors.current
    val maxSeconds = tasks.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1

    StatsCard(title = stringResource(R.string.stats_top_tasks_title)) {
        tasks.forEach { task ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title ?: stringResource(R.string.focus_task_none_label),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = reportDuration(task.seconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.focusAccent
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.focusTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(task.seconds.toFloat() / maxSeconds)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.focusAccent)
                    )
                }
            }
        }
    }
}

/** 一个季度的打卡热力图：格子越深，那天专注得越久；今天描一圈。 */
@Composable
private fun HeatmapCard(weeks: List<List<FocusDay>>, today: LocalDate) {
    val colors = LocalTodoColors.current
    val maxSeconds = weeks.flatten().maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1

    StatsCard(title = stringResource(R.string.stats_heatmap_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            weeks.forEach { week ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    week.forEach { day ->
                        HeatCell(day = day, maxSeconds = maxSeconds, today = today)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_heatmap_less),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.stats_heatmap_more),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
        }
    }
}

@Composable
private fun HeatCell(day: FocusDay, maxSeconds: Int, today: LocalDate) {
    val colors = LocalTodoColors.current
    val intensity = day.seconds.toFloat() / maxSeconds
    val shape = RoundedCornerShape(3.dp)
    val color = when {
        day.seconds == 0 -> colors.focusTrack.copy(alpha = 0.45f)
        intensity < 0.25f -> colors.focusAccent.copy(alpha = 0.3f)
        intensity < 0.5f -> colors.focusAccent.copy(alpha = 0.55f)
        intensity < 0.75f -> colors.focusAccent.copy(alpha = 0.8f)
        else -> colors.focusAccent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeatCellHeight)
            .clip(shape)
            .background(color)
            .then(
                if (day.date == today) {
                    Modifier.border(1.dp, colors.focusAccent, shape)
                } else {
                    Modifier
                }
            )
    )
}

/** 统计页的每一块都是一张圆角卡片，标题在左上。 */
@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LocalTodoColors.current.card)
            .padding(14.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * A focus time on the statistics page: to the minute, no seconds.
 *
 * The session rows of the focus page want seconds; this page is about trends, so
 * the minutes are enough and much easier to compare.
 */
@Composable
private fun reportDuration(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes == 0 -> stringResource(R.string.focus_duration_seconds, seconds)
        hours == 0 -> stringResource(R.string.focus_minutes_choice, rest)
        rest == 0 -> stringResource(R.string.focus_duration_hours, hours)
        else -> stringResource(R.string.focus_duration_hours_minutes, hours, rest)
    }
}
