package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.model.FocusSliceShare
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.focusDayRecords
import com.personal.todo.model.focusDayShares
import com.personal.todo.model.localDateOf
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 「专注占比」页的根测试钩子。 */
internal const val FocusRatioTag = "focus-ratio"

/** 页里右上角「专注记录」那个入口的测试钩子。 */
internal const val FocusRatioOpenRecordsTag = "focus-ratio-open-records"

/** 「没有选中任何任务」那个哨兵：rememberSaveable 存不了 null，用一个不可能的 id。 */
private const val NoTaskSelected = -1L

/**
 * 专注占比：**一个页面**，不是抽屉里那张小卡片（用户 2026-10-25）。
 *
 * 上面是所选那一天的甜甜圈（按任务切开），中间是这一天按任务的排行，下面直接是这一天
 * 的逐条记录——三块读的是**同一个日期**，所以「切到某一天、点进去看到的还是一样」这种事
 * 不会再发生。点饼图图例或排行里的某一项，下面的记录就只留那一天那件事的那几段，再点
 * 一次回到全部。
 *
 * 只读：改记录、补记、删记录都还在「专注记录」那一页，这里负责回答「这一天时间花在哪儿」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRatioScreen(
    sessions: Flow<List<FocusSession>>,
    zone: ZoneId,
    today: LocalDate,
    onOpenRecords: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val all by sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val colors = LocalTodoColors.current

    // 日期与「只看哪个任务」都存在 saveable 里：转屏回来还是那一天、还是那个筛选。
    var dayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var selectedTaskId by rememberSaveable { mutableLongStateOf(NoTaskSelected) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    val taskFilter = selectedTaskId.takeIf { it != NoTaskSelected }

    val shares = remember(all, day, zone) { focusDayShares(all, day, zone) }
    val totalSeconds = shares.sumOf { it.seconds }
    val records = remember(all, day, zone, taskFilter) {
        focusDayRecords(all, day, zone, taskFilter)
    }
    val palette = remember(colors.focusAccent) { focusSlicePalette(colors.focusAccent) }

    // 换日期就把「只看某个任务」一起清掉：昨天的那个任务在今天本来就没有记录，留着这个
    // 筛子只会把新的一天筛成空白，用户会以为今天什么都没做。
    val moveToDay: (LocalDate) -> Unit = { target ->
        dayEpoch = target.toEpochDay()
        selectedTaskId = NoTaskSelected
    }
    // 点同一个任务＝取消：筛子只有「某一个任务」和「全部」两种状态，不必另开清空按钮。
    val toggleTask: (Long?) -> Unit = { id ->
        selectedTaskId = if (id == null || id == taskFilter) NoTaskSelected else id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_ratio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenRecords,
                        modifier = Modifier.testTag(FocusRatioOpenRecordsTag)
                    ) {
                        Text(stringResource(R.string.focus_ratio_open_records))
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
                .testTag(FocusRatioTag),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "donut") {
                FocusDayDonutCard(
                    sessions = all,
                    day = day,
                    today = today,
                    zone = zone,
                    selectedTaskId = taskFilter,
                    onSelectTask = toggleTask,
                    onStepDay = { step -> moveToDay(day.plusDays(step.toLong())) },
                    onPickToday = { moveToDay(today) }
                )
            }

            if (shares.isNotEmpty()) {
                item(key = "rank") {
                    DayRankCard(
                        shares = shares,
                        totalSeconds = totalSeconds,
                        palette = palette,
                        selectedTaskId = taskFilter,
                        onSelectTask = toggleTask
                    )
                }
            }

            item(key = "records-header") {
                DayRecordsHeader(
                    day = day,
                    today = today,
                    recordCount = records.size,
                    // 筛中的任务名优先取饼图/排行那一份；万一它已经不在前几名里（数据刚变过），
                    // 就退回这一条记录自己的标题快照，最后才是「未关联任务」——不能把有任务
                    // 的记录错标成「未关联」。
                    filteredTaskLabel = taskFilter?.let { id ->
                        shares.firstOrNull { it.taskId == id }?.label
                            ?: records.firstOrNull()?.taskTitleSnapshot
                            ?: stringResource(R.string.focus_task_none_label)
                    },
                    onClearFilter = { selectedTaskId = NoTaskSelected }
                )
            }

            if (records.isEmpty()) {
                item(key = "records-empty") {
                    Text(
                        text = stringResource(
                            if (taskFilter != null) {
                                R.string.focus_ratio_records_empty_filtered
                            } else {
                                R.string.focus_ratio_records_empty
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 6.dp)
                    )
                }
            } else {
                items(records, key = { "ratio-record-${it.id}" }) { session ->
                    DayRecordRow(session = session, zone = zone)
                }
            }
        }
    }
}

/**
 * 这一天按任务的排行：色点 + 任务名 + 时长 + 占比条。
 *
 * 与饼图共用同一份 [FocusSliceShare]，所以「图里这块」与「排行里这行」永远是同一个数、
 * 同一个颜色。点某一行就只看那个任务的记录（再点一次取消）；「其他」那一块是几件事的
 * 合体，点它没有意义，所以不做成可点的。
 */
@Composable
private fun DayRankCard(
    shares: List<FocusSliceShare>,
    totalSeconds: Int,
    palette: List<Color>,
    selectedTaskId: Long?,
    onSelectTask: (Long?) -> Unit
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_ratio_rank_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.focus_ratio_rank_total, shortDuration(totalSeconds)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
        }
        Spacer(Modifier.height(6.dp))
        shares.forEachIndexed { index, share ->
            RankRow(
                label = when {
                    share.others -> stringResource(R.string.focus_donut_other)
                    share.label == null -> stringResource(R.string.focus_task_none_label)
                    else -> share.label
                },
                seconds = share.seconds,
                fraction = share.fraction,
                color = palette[index % palette.size],
                selectable = share.taskId != null,
                selected = share.taskId != null && share.taskId == selectedTaskId,
                onClick = { onSelectTask(share.taskId) }
            )
        }
    }
}

@Composable
private fun RankRow(
    label: String,
    seconds: Int,
    fraction: Float,
    color: Color,
    selectable: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.10f) else Color.Transparent)
            .then(if (selectable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = shortDuration(seconds),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${(fraction * 100f).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.focusTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/**
 * 「这一天的记录」那一条小标题：说清这是哪一天、几条；选了某个任务时再给一句「只看 X」
 * 和一个清掉的口子——否则用户看不出下面为什么少了那么多条。
 */
@Composable
private fun DayRecordsHeader(
    day: LocalDate,
    today: LocalDate,
    recordCount: Int,
    filteredTaskLabel: String?,
    onClearFilter: () -> Unit
) {
    val colors = LocalTodoColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_ratio_records_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = calendarDateText(day, today),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.focus_ratio_records_summary, recordCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1
            )
        }
        if (filteredTaskLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.focus_ratio_filtered_by, filteredTaskLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.focusAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = onClearFilter) {
                    Text(
                        text = stringResource(R.string.focus_ratio_filter_clear),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * 这一天的某一条记录：左边开始 / 结束时刻，中间任务名与状态，右边时长。
 *
 * 这里只读（改 / 删 / 补记都在「专注记录」那一页），所以没有 ⋮ 菜单，也不写计时模式——
 * 三种计时都是同一件事的投入。
 */
@Composable
private fun DayRecordRow(session: FocusSession, zone: ZoneId) {
    val colors = LocalTodoColors.current
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    val endClock = "%02d:%02d".format(end.hour, end.minute)
    val endLabel = if (localDateOf(session.endedAt, zone) == localDateOf(session.startedAt, zone)) {
        endClock
    } else {
        stringResource(R.string.focus_ratio_next_day, endClock)
    }
    val statusColor = if (session.status == FocusStatus.COMPLETED) colors.success else colors.dateOverdue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(
                text = "%02d:%02d".format(start.hour, start.minute),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.35f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.taskTitleSnapshot ?: stringResource(R.string.focus_task_none_label),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        if (session.status == FocusStatus.COMPLETED) {
                            R.string.focus_status_completed
                        } else {
                            R.string.focus_status_interrupted
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }
        }
        Text(
            text = durationText(session.actualFocusSeconds),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
        )
    }
}
