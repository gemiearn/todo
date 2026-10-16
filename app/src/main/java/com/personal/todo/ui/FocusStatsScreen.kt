package com.personal.todo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.model.FocusDay
import com.personal.todo.model.FocusHourBar
import com.personal.todo.model.FocusPeriod
import com.personal.todo.model.FocusPeriodTotals
import com.personal.todo.model.FocusPeriodUnit
import com.personal.todo.model.FocusRankBy
import com.personal.todo.model.FocusRankEntry
import com.personal.todo.model.FocusTrendBar
import com.personal.todo.model.FocusTrendUnit
import com.personal.todo.model.focusDailyAverage
import com.personal.todo.model.focusHourBars
import com.personal.todo.model.focusPeriodOf
import com.personal.todo.model.focusPeriodTotals
import com.personal.todo.model.focusRanking
import com.personal.todo.model.focusSpans
import com.personal.todo.model.focusTrendBars
import com.personal.todo.model.focusYearWeeks
import com.personal.todo.model.shift
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/** The tag the smoke tests use to reach the page's list. */
internal const val FocusStatsTag = "focus-stats"

private val DonutSize = 132.dp
private val DonutStroke = 16.dp
private val TrendChartHeight = 130.dp
private val TimelineHeight = 148.dp
private val HoursHeight = 150.dp
private val YearHeight = 62.dp

private val AxisLabelHeight = 15.dp

/** 专注详情那张卡的四个分段：天 / 周 / 月，外加自己选起止日的「自定义」。 */
private enum class DetailSegment { DAY, WEEK, MONTH, CUSTOM }

/** LocalDate 与 DatePicker 的 UTC 零时毫秒互转（一天正好 86400000 毫秒）。 */
private const val MillisPerDay = 86_400_000L
private fun pickerMillis(date: LocalDate): Long = date.toEpochDay() * MillisPerDay
private fun dateOfPickerMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(millis / MillisPerDay)

/** 时间线左轴的四个刻度，最佳专注时间下轴的六个刻度。 */
private val TimelineHours = listOf("00:00", "06:00", "12:00", "18:00")
private val HoursAxisHours = listOf(0, 4, 8, 12, 16, 20)
private fun hourText(hour: Int) = (if (hour < 10) "0" else "") + hour + ":00"

/**
 * 专注统计: the page 滴答清单 and 番茄ToDo both put behind a 统计 icon, laid out the
 * way the reference strips the user supplied do it.
 *
 * Every card is a rounded white card with its title top left and a period it can
 * be stepped through. On purpose, nothing here is stored as a total: it is all
 * recomputed from the sessions, so the numbers can never drift. A break - which
 * is a phase of the timer, never a session - therefore cannot show up, and the
 * 达成率 ring has two arcs instead of the reference's three.
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

    // 每张卡都停在一个周期上，左右两个箭头挪的就是它。锚点存的是 epochDay / 年份，
    // 这样转屏之后还在同一页。详情卡多一个「自定义」，起止日另存两个 epochDay。
    var detailSegmentIndex by rememberSaveable { mutableIntStateOf(DetailSegment.DAY.ordinal) }
    var detailEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var customStartEpoch by rememberSaveable { mutableLongStateOf(today.minusDays(6).toEpochDay()) }
    var customEndEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var rankIndex by rememberSaveable { mutableIntStateOf(FocusRankBy.TASK.ordinal) }
    var trendUnitIndex by rememberSaveable { mutableIntStateOf(FocusTrendUnit.WEEK.ordinal) }
    var trendEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var timelineEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var hoursEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }

    val detailSegment = DetailSegment.entries[detailSegmentIndex]
    // 「自定义」没有周期可言，所以这一格是 null：它不走 focusPeriodOf，直接用手选的区间。
    val detailUnit = when (detailSegment) {
        DetailSegment.DAY -> FocusPeriodUnit.DAY
        DetailSegment.WEEK -> FocusPeriodUnit.WEEK
        DetailSegment.MONTH -> FocusPeriodUnit.MONTH
        DetailSegment.CUSTOM -> null
    }
    // 起止日选反了也不报错：小的是头、大的是尾，读的时候归一化。
    val customStart = LocalDate.ofEpochDay(minOf(customStartEpoch, customEndEpoch))
    val customEnd = LocalDate.ofEpochDay(maxOf(customStartEpoch, customEndEpoch))
    val detailPeriod = detailUnit?.let { focusPeriodOf(it, LocalDate.ofEpochDay(detailEpoch)) }
        ?: FocusPeriod(customStart, customEnd)

    val rankBy = FocusRankBy.entries[rankIndex]
    val trendUnit = FocusTrendUnit.entries[trendUnitIndex]

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
                // 一次都没专注过时，五张空图表只是噪音，所以直接说实话。
                item(key = "empty") {
                    StatsCard(title = stringResource(R.string.more_entry_stats)) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalTodoColors.current.dateMuted
                        )
                    }
                }
            } else {
                item(key = "detail") {
                    DetailCard(
                        sessions = all,
                        segment = detailSegment,
                        period = detailPeriod,
                        unit = detailUnit,
                        customStart = customStart,
                        customEnd = customEnd,
                        rankBy = rankBy,
                        today = today,
                        zone = zone,
                        onSelectSegment = { detailSegmentIndex = it.ordinal },
                        onStep = { step ->
                            detailUnit?.let { moved ->
                                detailEpoch = focusPeriodOf(moved, LocalDate.ofEpochDay(detailEpoch))
                                    .shift(moved, step.toLong())
                                    .start
                                    .toEpochDay()
                            }
                        },
                        onPickCustomStart = { customStartEpoch = it.toEpochDay() },
                        onPickCustomEnd = { customEndEpoch = it.toEpochDay() },
                        onSelectRank = { rankIndex = it.ordinal }
                    )
                }
                item(key = "trend") {
                    TrendCard(
                        sessions = all,
                        unit = trendUnit,
                        anchor = LocalDate.ofEpochDay(trendEpoch),
                        today = today,
                        zone = zone,
                        onSelectUnit = { trendUnitIndex = it.ordinal },
                        onStep = { step ->
                            val anchor = LocalDate.ofEpochDay(trendEpoch)
                            trendEpoch = when (trendUnit) {
                                FocusTrendUnit.WEEK -> anchor.plusWeeks(step.toLong())
                                FocusTrendUnit.MONTH -> anchor.plusMonths(step.toLong())
                                FocusTrendUnit.YEAR -> anchor.plusYears(step.toLong())
                            }.toEpochDay()
                        }
                    )
                }
                item(key = "timeline") {
                    TimelineCard(
                        sessions = all,
                        anchor = LocalDate.ofEpochDay(timelineEpoch),
                        today = today,
                        zone = zone,
                        onStep = { timelineEpoch = LocalDate.ofEpochDay(timelineEpoch).plusWeeks(it.toLong()).toEpochDay() }
                    )
                }
                item(key = "hours") {
                    HoursCard(
                        sessions = all,
                        anchor = LocalDate.ofEpochDay(hoursEpoch),
                        today = today,
                        zone = zone,
                        onStep = { hoursEpoch = LocalDate.ofEpochDay(hoursEpoch).plusMonths(it.toLong()).toEpochDay() }
                    )
                }
                item(key = "year") {
                    YearCard(
                        sessions = all,
                        year = year,
                        zone = zone,
                        onStep = { year += it }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 专注详情

/** 天 / 周 / 月 / 自定义这一个周期：达成率环、图例、周期小结，以及时间花在哪个任务上。 */
@Composable
private fun DetailCard(
    sessions: List<FocusSession>,
    segment: DetailSegment,
    period: FocusPeriod,
    unit: FocusPeriodUnit?,
    customStart: LocalDate,
    customEnd: LocalDate,
    rankBy: FocusRankBy,
    today: LocalDate,
    zone: ZoneId,
    onSelectSegment: (DetailSegment) -> Unit,
    onStep: (Int) -> Unit,
    onPickCustomStart: (LocalDate) -> Unit,
    onPickCustomEnd: (LocalDate) -> Unit,
    onSelectRank: (FocusRankBy) -> Unit
) {
    val colors = LocalTodoColors.current
    val totals = remember(sessions, period, zone) { focusPeriodTotals(sessions, period, zone) }
    val ranking = remember(sessions, period, zone, rankBy) { focusRanking(sessions, period, zone, rankBy) }

    StatsCard(
        title = stringResource(R.string.stats_detail_title),
        trailing = {
            if (unit == null) {
                // 自定义没有「上一个 / 下一个」可言，右边就直接说清选的是哪一段。
                Text(
                    text = stringResource(R.string.date_range, dateLabel(customStart), dateLabel(customEnd)),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.focusAccent,
                    maxLines = 1
                )
            } else {
                PeriodStepper(
                    label = detailAnchorLabel(unit, period, today),
                    onPrevious = { onStep(-1) },
                    onNext = { onStep(1) }
                )
            }
        }
    ) {
        Segments(
            labels = listOf(
                stringResource(R.string.stats_period_day),
                stringResource(R.string.stats_period_week),
                stringResource(R.string.stats_period_month),
                stringResource(R.string.stats_period_custom)
            ),
            selectedIndex = segment.ordinal,
            onSelect = { onSelectSegment(DetailSegment.entries[it]) }
        )

        if (segment == DetailSegment.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CustomDateField(
                    label = stringResource(R.string.stats_custom_start),
                    date = customStart,
                    today = today,
                    onPick = onPickCustomStart,
                    modifier = Modifier.weight(1f)
                )
                CustomDateField(
                    label = stringResource(R.string.stats_custom_end),
                    date = customEnd,
                    today = today,
                    onPick = onPickCustomEnd,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        AchievementRing(totals, colors.focusAccent, colors.focusTrack)
        Spacer(Modifier.height(10.dp))
        FocusLegend(colors.focusAccent, colors.focusTrack)
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_detail_total, compactDuration(totals.seconds)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.focus_stats_pomodoros, totals.pomodoros),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted
            )
        }

        Spacer(Modifier.height(16.dp))
        RankHeader(rankBy = rankBy, onSelect = onSelectRank)
        Spacer(Modifier.height(8.dp))
        RankList(ranking = ranking, rankBy = rankBy)
    }
}

/**
 * 自定义范围的一个端点：一按弹出日期选择，选完立刻生效。
 *
 * DatePicker 报的是 UTC 零点的毫秒，这里按整天的毫秒数换算成 epochDay，不经过设备时
 * 区——否则东八区的用户选「10 月 16 日」会被时区推到 15 日。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateField(
    label: String,
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    var open by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.groupHeader)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = calendarDateText(date, today),
                style = MaterialTheme.typography.labelLarge,
                color = colors.focusAccent,
                maxLines = 1
            )
        }
    }

    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onPick(dateOfPickerMillis(it)) }
                        open = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** 达成率环：完成的那段是番茄红，中断的那段是浅色；一次都没专注时只有一圈轨道。 */
@Composable
private fun AchievementRing(totals: FocusPeriodTotals, accent: Color, track: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DonutSize + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(DonutSize)) {
            val stroke = DonutStroke.toPx()
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = track.copy(alpha = 0.5f),
                startAngle = RingStartAngle,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            if (totals.seconds > 0) {
                val doneSweep = 360f * totals.completionRate
                drawArc(
                    color = accent,
                    startAngle = RingStartAngle,
                    sweepAngle = doneSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke)
                )
                drawArc(
                    color = track,
                    startAngle = RingStartAngle + doneSweep,
                    sweepAngle = 360f - doneSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke)
                )
            }
        }

        if (totals.seconds > 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        R.string.stats_percent,
                        (totals.completionRate * 100f).roundToInt()
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = stringResource(R.string.stats_achievement),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTodoColors.current.dateMuted
                )
            }
        } else {
            Text(
                text = stringResource(R.string.stats_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTodoColors.current.dateMuted
            )
        }
    }
}

@Composable
private fun FocusLegend(accent: Color, track: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = accent, text = stringResource(R.string.stats_legend_focus))
        Spacer(Modifier.width(20.dp))
        // 原型上还有第三段「休息」：休息是计时器的阶段、从来不是一条 session，
        // 所以我们这里只有两段。
        LegendDot(color = track, text = stringResource(R.string.stats_legend_interrupted))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalTodoColors.current.dateMuted
        )
    }
}

/** 「专注排行」加右边的口径下拉：按任务，或者按当时所在的清单。 */
@Composable
private fun RankHeader(rankBy: FocusRankBy, onSelect: (FocusRankBy) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = LocalTodoColors.current

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.stats_ranking_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (rankBy == FocusRankBy.TASK) {
                            R.string.stats_rank_by_task
                        } else {
                            R.string.stats_rank_by_list
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.dateMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stats_rank_by_task)) },
                    onClick = {
                        onSelect(FocusRankBy.TASK)
                        menuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stats_rank_by_list)) },
                    onClick = {
                        onSelect(FocusRankBy.LIST)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RankList(ranking: List<FocusRankEntry>, rankBy: FocusRankBy) {
    val colors = LocalTodoColors.current
    if (ranking.isEmpty()) {
        Text(
            text = stringResource(R.string.stats_rank_none),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
        return
    }

    val max = ranking.first().seconds.coerceAtLeast(1)
    ranking.forEach { entry ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.label ?: stringResource(
                        if (rankBy == FocusRankBy.TASK) {
                            R.string.focus_task_none_label
                        } else {
                            R.string.stats_rank_unlinked
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = compactDuration(entry.seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.focusAccent
                )
            }
            Spacer(Modifier.height(5.dp))
            RatioBar(fraction = entry.seconds.toFloat() / max)
        }
    }
}

@Composable
private fun RatioBar(fraction: Float) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.focusTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.focusAccent)
        )
    }
}

// ------------------------------------------------------------------ 专注趋势

/** 周 / 月 / 年一根一根的柱子，底下是「每日平均」。 */
@Composable
private fun TrendCard(
    sessions: List<FocusSession>,
    unit: FocusTrendUnit,
    anchor: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    onSelectUnit: (FocusTrendUnit) -> Unit,
    onStep: (Int) -> Unit
) {
    val colors = LocalTodoColors.current
    val bars = remember(sessions, unit, anchor, zone) { focusTrendBars(sessions, unit, anchor, zone) }
    val period = trendPeriodOf(unit, anchor)
    val average = remember(sessions, period, zone) {
        focusDailyAverage(focusPeriodTotals(sessions, period, zone), period)
    }

    StatsCard(
        title = stringResource(R.string.stats_trend_title),
        trailing = {
            PeriodStepper(
                label = trendAnchorLabel(unit, period, today),
                onPrevious = { onStep(-1) },
                onNext = { onStep(1) }
            )
        }
    ) {
        Segments(
            labels = listOf(
                stringResource(R.string.stats_period_week),
                stringResource(R.string.stats_period_month),
                stringResource(R.string.stats_period_year)
            ),
            selectedIndex = unit.ordinal,
            onSelect = { onSelectUnit(FocusTrendUnit.entries[it]) }
        )

        Spacer(Modifier.height(14.dp))
        BarChart(
            bars = bars,
            labels = bars.mapIndexed { index, bar ->
                when (unit) {
                    FocusTrendUnit.WEEK -> stringResource(weekdayNarrowRes(bar.date.dayOfWeek))
                    FocusTrendUnit.MONTH ->
                        if (index % 5 == 0 || index == bars.lastIndex) {
                            bar.date.dayOfMonth.toString()
                        } else {
                            null
                        }

                    FocusTrendUnit.YEAR -> bar.date.monthValue.toString()
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_daily_average),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = compactDuration(average),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.focusAccent
            )
        }
    }
}

/** 虚线网格 + 番茄红柱子 + 轴标签，都画在一张 Canvas 里，保证对齐。 */
@Composable
private fun BarChart(bars: List<FocusTrendBar>, labels: List<String?>) {
    val colors = LocalTodoColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.dateMuted)
    val labelHeight = AxisLabelHeight
    val accent = colors.focusAccent
    val track = colors.focusTrack
    val muted = colors.dateMuted

    Canvas(modifier = Modifier.fillMaxWidth().height(TrendChartHeight)) {
        val labelHeightPx = labelHeight.toPx()
        val plotHeight = size.height - labelHeightPx
        val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = plotHeight * fraction
            drawLine(
                color = muted.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dash
            )
        }

        if (bars.isEmpty()) return@Canvas

        val maxSeconds = bars.maxOf { it.seconds }.coerceAtLeast(1)
        val slot = size.width / bars.size
        val barWidth = (slot * 0.52f).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { index, bar ->
            val x = slot * index + (slot - barWidth) / 2f
            val height = if (bar.seconds <= 0) {
                2.dp.toPx()
            } else {
                maxOf(4.dp.toPx(), plotHeight * 0.9f * bar.seconds / maxSeconds)
            }
            drawRoundRect(
                color = if (bar.seconds <= 0) track.copy(alpha = 0.6f) else accent,
                topLeft = Offset(x, plotHeight - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }

        labels.forEachIndexed { index, text ->
            if (text == null) return@forEachIndexed
            val measured = measurer.measure(text, labelStyle)
            val x = (slot * index + slot / 2f - measured.size.width / 2f)
                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
            drawText(measured, topLeft = Offset(x, plotHeight + 3.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------- 专注时间线

/** 一周七列 × 时间轴：每天一条圆角横条，就是那天的专注时段。 */
@Composable
private fun TimelineCard(
    sessions: List<FocusSession>,
    anchor: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    onStep: (Int) -> Unit
) {
    val colors = LocalTodoColors.current
    val period = focusPeriodOf(FocusPeriodUnit.WEEK, anchor)
    val spans = remember(sessions, period, zone) { focusSpans(sessions, period, zone) }
    val weekdayLabels = (1..7).map { stringResource(weekdayNarrowRes(DayOfWeek.of(it))) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.dateMuted)
    val accent = colors.focusAccent
    val track = colors.focusTrack
    val muted = colors.dateMuted

    StatsCard(
        title = stringResource(R.string.stats_timeline_title),
        trailing = {
            PeriodStepper(
                label = weekAnchorLabel(period, today),
                onPrevious = { onStep(-1) },
                onNext = { onStep(1) }
            )
        }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(TimelineHeight)) {
            val labelHeight = AxisLabelHeight.toPx()
            val plotTop = 4.dp.toPx()
            val plotBottom = size.height - labelHeight
            val plotHeight = plotBottom - plotTop
            val leftInset = 42.dp.toPx()
            val plotWidth = size.width - leftInset
            val columnWidth = plotWidth / DaysPerWeek.toFloat()
            val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)

            TimelineHours.forEachIndexed { index, text ->
                val hour = index * 6
                val y = plotTop + plotHeight * hour / 24f
                drawLine(
                    color = muted.copy(alpha = 0.35f),
                    start = Offset(leftInset, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = dash
                )
                val measured = measurer.measure(text, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        x = (leftInset - 6.dp.toPx() - measured.size.width).coerceAtLeast(0f),
                        y = y - measured.size.height / 2f
                    )
                )
            }

            spans.forEach { span ->
                val column = span.date.dayOfWeek.value - 1
                if (column !in 0 until DaysPerWeek) return@forEach
                val barWidth = columnWidth * 0.5f
                val x = leftInset + columnWidth * column + (columnWidth - barWidth) / 2f
                val top = plotTop + plotHeight * span.startMinute / MinutesPerDay.toFloat()
                val bottom = plotTop + plotHeight * span.endMinute / MinutesPerDay.toFloat()
                drawRoundRect(
                    color = if (span.interrupted) track else accent,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, (bottom - top).coerceAtLeast(3.dp.toPx())),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }

            weekdayLabels.forEachIndexed { index, text ->
                val measured = measurer.measure(text, labelStyle)
                val x = (leftInset + columnWidth * index + columnWidth / 2f - measured.size.width / 2f)
                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                drawText(measured, topLeft = Offset(x, plotBottom + 2.dp.toPx()))
            }
        }
    }
}

// ------------------------------------------------------------ 最佳专注时间

/** 一个月里 24 根小时柱：看出来自己一天中什么时候最专注。 */
@Composable
private fun HoursCard(
    sessions: List<FocusSession>,
    anchor: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    onStep: (Int) -> Unit
) {
    val colors = LocalTodoColors.current
    val period = focusPeriodOf(FocusPeriodUnit.MONTH, anchor)
    val bars = remember(sessions, period, zone) { focusHourBars(sessions, period, zone) }
    val axisMinutes = remember(bars) {
        val maxMinutes = (bars.maxOfOrNull { it.seconds }?.plus(59) ?: 0) / 60
        if (maxMinutes <= 0) 0 else ((maxMinutes + 3) / 4) * 4
    }

    StatsCard(
        title = stringResource(R.string.stats_hours_title),
        trailing = {
            PeriodStepper(
                label = monthAnchorLabel(period.start, today),
                onPrevious = { onStep(-1) },
                onNext = { onStep(1) }
            )
        }
    ) {
        if (axisMinutes <= 0) {
            NoDataRow(height = HoursHeight)
        } else {
            HourChart(
                bars = bars,
                axisMinutes = axisMinutes,
                axisLabels = (0..4).map { level ->
                    compactDuration(level * axisMinutes * 60)
                }
            )
        }
    }
}

@Composable
private fun HourChart(bars: List<FocusHourBar>, axisMinutes: Int, axisLabels: List<String>) {
    val colors = LocalTodoColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.dateMuted)
    val accent = colors.focusAccent
    val track = colors.focusTrack
    val muted = colors.dateMuted
    val bottomLabels = HoursAxisHours.map(::hourText)

    Canvas(modifier = Modifier.fillMaxWidth().height(HoursHeight)) {
        val labelHeight = AxisLabelHeight.toPx()
        val plotTop = 2.dp.toPx()
        val plotBottom = size.height - labelHeight
        val plotHeight = plotBottom - plotTop
        val leftInset = 46.dp.toPx()
        val plotWidth = size.width - leftInset
        val slot = plotWidth / HoursPerDay.toFloat()
        val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)
        val axisMaxSeconds = axisMinutes * 60

        axisLabels.forEachIndexed { level, text ->
            val y = plotBottom - plotHeight * level / 4f
            drawLine(
                color = muted.copy(alpha = 0.3f),
                start = Offset(leftInset, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dash
            )
            val measured = measurer.measure(text, labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    x = (leftInset - 6.dp.toPx() - measured.size.width).coerceAtLeast(0f),
                    y = y - measured.size.height / 2f
                )
            )
        }

        val barWidth = (slot * 0.56f).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { index, bar ->
            val x = leftInset + slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                color = track.copy(alpha = 0.5f),
                topLeft = Offset(x, plotTop),
                size = Size(barWidth, plotHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
            if (bar.seconds > 0) {
                val height = plotHeight * bar.seconds / axisMaxSeconds.toFloat()
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x, plotBottom - height),
                    size = Size(barWidth, height.coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }
        }

        bottomLabels.forEachIndexed { index, text ->
            val hour = HoursAxisHours[index]
            val measured = measurer.measure(text, labelStyle)
            val x = (leftInset + slot * (hour + 0.5f) - measured.size.width / 2f)
                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
            drawText(measured, topLeft = Offset(x, plotBottom + 2.dp.toPx()))
        }
    }
}

// ------------------------------------------------------------ 年度热力图

/** 整年 53 列 × 7 行的打卡格子，颜色越深那天专注得越久。 */
@Composable
private fun YearCard(
    sessions: List<FocusSession>,
    year: Int,
    zone: ZoneId,
    onStep: (Int) -> Unit
) {
    val weeks = remember(sessions, year, zone) { focusYearWeeks(sessions, year, zone) }
    val monthLabels = weeks.map { week ->
        val firstOfMonth = week.firstOrNull {
            it.date.year == year && it.date.dayOfMonth == 1 && it.date.monthValue % 3 == 1
        }
        firstOfMonth?.let { stringResource(R.string.stats_month_label, it.date.monthValue) }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = LocalTodoColors.current.dateMuted)

    StatsCard(
        title = stringResource(R.string.stats_year_title),
        trailing = {
            PeriodStepper(
                label = stringResource(R.string.stats_year_label, year),
                onPrevious = { onStep(-1) },
                onNext = { onStep(1) }
            )
        }
    ) {
        YearGrid(weeks = weeks, year = year, monthLabels = monthLabels, measurer = measurer, labelStyle = labelStyle)
        Spacer(Modifier.height(10.dp))
        HeatLegend()
    }
}

@Composable
private fun YearGrid(
    weeks: List<List<FocusDay>>,
    year: Int,
    monthLabels: List<String?>,
    measurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val colors = LocalTodoColors.current
    val accent = colors.focusAccent
    val track = colors.focusTrack

    Canvas(modifier = Modifier.fillMaxWidth().height(YearHeight)) {
        val topInset = 12.dp.toPx()
        val columns = weeks.size.coerceAtLeast(1)
        val rows = DaysPerWeek
        val cell = minOf(size.width / columns, (size.height - topInset) / rows)
        val gap = cell * 0.18f
        val side = cell - gap

        monthLabels.forEachIndexed { column, text ->
            if (text == null) return@forEachIndexed
            val measured = measurer.measure(text, labelStyle)
            drawText(measured, topLeft = Offset((cell * column).coerceAtMost(size.width - measured.size.width), 0f))
        }

        weeks.forEachIndexed { column, week ->
            week.forEachIndexed { row, day ->
                val insideYear = day.date.year == year
                drawRoundRect(
                    color = if (insideYear) {
                        heatColor(heatLevel(day.seconds), accent, track)
                    } else {
                        Color.Transparent
                    },
                    topLeft = Offset(cell * column + gap / 2f, topInset + cell * row + gap / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.25f)
                )
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeatLegendItem(level = 0, label = stringResource(R.string.stats_heat_none))
        HeatLegendItem(level = 1, label = stringResource(R.string.stats_heat_light))
        HeatLegendItem(level = 2, label = stringResource(R.string.stats_heat_medium))
        HeatLegendItem(level = 3, label = stringResource(R.string.stats_heat_strong))
        HeatLegendItem(level = 4, label = stringResource(R.string.stats_heat_fire))
    }
}

@Composable
private fun HeatLegendItem(level: Int, label: String) {
    val colors = LocalTodoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(heatColor(level, colors.focusAccent, colors.focusTrack))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

// ------------------------------------------------------------------ 公共零件

/** 卡片标题右边那组「‹ 周期 ›」。 */
@Composable
private fun PeriodStepper(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_step_previous),
                tint = colors.focusAccent
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.focusAccent,
            maxLines = 1
        )
        IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stats_step_next),
                tint = colors.focusAccent
            )
        }
    }
}

/** 分段控件：选中的那一段是浅色底 + 番茄红字，和参考图一致。 */
@Composable
private fun Segments(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.groupHeader)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.focusSurface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else null,
                    color = if (isSelected) colors.focusAccent else colors.dateMuted
                )
            }
        }
    }
}

/** 统计页的每一块都是一张圆角卡片，标题在左上，右边可以放步进器。 */
@Composable
private fun StatsCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalTodoColors.current.card)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun NoDataRow(height: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.stats_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTodoColors.current.dateMuted
        )
    }
}

// -------------------------------------------------------------------- 文案

@Composable
private fun detailAnchorLabel(unit: FocusPeriodUnit, period: FocusPeriod, today: LocalDate): String =
    when (unit) {
        FocusPeriodUnit.DAY ->
            if (period.start == today) {
                stringResource(R.string.stats_this_day)
            } else {
                dateLabel(period.start)
            }

        FocusPeriodUnit.WEEK -> weekAnchorLabel(period, today)
        FocusPeriodUnit.MONTH -> monthAnchorLabel(period.start, today)
    }

@Composable
private fun weekAnchorLabel(period: FocusPeriod, today: LocalDate): String =
    if (period.contains(today)) stringResource(R.string.stats_this_week) else dateLabel(period.start)

@Composable
private fun monthAnchorLabel(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) {
        stringResource(R.string.stats_month_label, date.monthValue)
    } else {
        stringResource(R.string.stats_month_year_label, date.year, date.monthValue)
    }

@Composable
private fun dateLabel(date: LocalDate): String =
    stringResource(R.string.stats_date_label, date.monthValue, date.dayOfMonth)

@Composable
private fun trendAnchorLabel(unit: FocusTrendUnit, period: FocusPeriod, today: LocalDate): String =
    when (unit) {
        FocusTrendUnit.WEEK -> weekAnchorLabel(period, today)
        FocusTrendUnit.MONTH -> monthAnchorLabel(period.start, today)
        FocusTrendUnit.YEAR -> stringResource(R.string.stats_year_label, period.start.year)
    }

private fun trendPeriodOf(unit: FocusTrendUnit, anchor: LocalDate): FocusPeriod = when (unit) {
    FocusTrendUnit.WEEK -> focusPeriodOf(FocusPeriodUnit.WEEK, anchor)
    FocusTrendUnit.MONTH -> focusPeriodOf(FocusPeriodUnit.MONTH, anchor)
    FocusTrendUnit.YEAR -> FocusPeriod(
        start = LocalDate.of(anchor.year, 1, 1),
        end = LocalDate.of(anchor.year, 12, 31)
    )
}

/**
 * 图上的时长：到分钟为止，没有空格——0m / 23m / 1h9m / 2h。
 *
 * 统计页看的是趋势，分钟就够了，也不像「1 小时 9 分」那样把横轴挤变形。
 */
@Composable
private fun compactDuration(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.stats_duration_minutes, rest)
        rest == 0 -> stringResource(R.string.stats_duration_hours, hours)
        else -> stringResource(R.string.stats_duration_hours_minutes, hours, rest)
    }
}

/** 热力图的五档：0m / 0-1h / 1h-3h / 3h-5h / >5h。 */
private fun heatLevel(seconds: Int): Int = when {
    seconds <= 0 -> 0
    seconds < 60 * 60 -> 1
    seconds < 3 * 60 * 60 -> 2
    seconds < 5 * 60 * 60 -> 3
    else -> 4
}

private fun heatColor(level: Int, accent: Color, track: Color): Color = when (level) {
    0 -> track.copy(alpha = 0.45f)
    1 -> accent.copy(alpha = 0.3f)
    2 -> accent.copy(alpha = 0.55f)
    3 -> accent.copy(alpha = 0.8f)
    else -> accent
}

private const val RingStartAngle = -90f
private const val DaysPerWeek = 7
private const val HoursPerDay = 24
private const val MinutesPerDay = 24 * 60
