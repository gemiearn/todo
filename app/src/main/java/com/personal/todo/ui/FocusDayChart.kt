package com.personal.todo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.model.FocusPeriod
import com.personal.todo.model.FocusRankBy
import com.personal.todo.model.focusPeriodTotals
import com.personal.todo.model.focusRanking
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import java.time.LocalDate
import java.time.ZoneId

/** 饼图的直径与环宽。 */
private val DonutDiameter = 108.dp
private val DonutStroke = 17.dp

/** 饼图最多分几块：再多图例就比图还长，剩下的归到「其他」。 */
private const val MaxSlices = 4

/**
 * 专注抽屉里的「这一天」饼图（用户 2026-10-21：抽屉里要能看某一天的专注占比）。
 *
 * 内容是**选中那一天**的专注时长按任务切开的甜甜圈：中间是这一天的总时长，右边是图例
 * （色点 + 任务名 + 时长 + 占比）。上面那对箭头只挪这一天，不动任何记录——它是个「怎么看」
 * 的开关，真要改记录还是去「专注记录」那一页（右下角那个入口）。
 *
 * 每一块的颜色按顺序取：第一块永远是专注页那个番茄红，后面才轮换调色板——所以同一个任务
 * 在「今天」和「昨天」都排在第一位时颜色不会跳。
 */
@Composable
internal fun FocusDayDonutCard(
    sessions: List<FocusSession>,
    today: LocalDate,
    zone: ZoneId,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    var dayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)

    val slices = remember(sessions, day, zone) { daySlices(sessions, day, zone) }
    val totalSeconds = slices.sumOf { it.seconds }
    val palette = remember(colors.focusAccent) {
        listOf(
            colors.focusAccent,
            TagPalette[3],
            TagPalette[2],
            TagPalette[1],
            TagPalette[4],
            TagPalette[6]
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_donut_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            DayStepButton(
                forward = false,
                description = stringResource(R.string.focus_donut_prev),
                onClick = { dayEpoch = day.minusDays(1).toEpochDay() }
            )
            Text(
                text = calendarDateText(day, today),
                style = MaterialTheme.typography.labelMedium,
                color = if (day == today) colors.focusAccent else colors.dateMuted,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (day == today) {
                            colors.focusSurface
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { dayEpoch = today.toEpochDay() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            DayStepButton(
                forward = true,
                description = stringResource(R.string.focus_donut_next),
                onClick = { dayEpoch = day.plusDays(1).toEpochDay() }
            )
        }

        if (totalSeconds <= 0) {
            Text(
                text = stringResource(R.string.focus_donut_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp)
            )
            TextButton(
                onClick = onOpenRecords,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 2.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.focus_donut_open_records),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        // 抽屉只有半屏宽：饼图单独占一行居中，图例在它下面铺满整行。并排放的话图例只剩
        // 六七十 dp，任务名一律被省略号吃掉。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Donut(slices = slices, total = totalSeconds, palette = palette)
                Text(
                    text = shortDuration(totalSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.focusAccent
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEachIndexed { index, slice ->
                SliceLegendRow(
                    label = when {
                        slice.others -> stringResource(R.string.focus_donut_other)
                        slice.label == null -> stringResource(R.string.focus_task_none_label)
                        else -> slice.label
                    },
                    seconds = slice.seconds,
                    fraction = slice.seconds.toFloat() / totalSeconds,
                    color = palette[index % palette.size]
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_donut_total, shortDuration(totalSeconds)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.focus_donut_open_records),
                style = MaterialTheme.typography.labelMedium,
                color = colors.focusAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenRecords)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/** 那对只挪一天的箭头（36dp，和日历上的步进箭头同一个尺寸）。 */
@Composable
private fun DayStepButton(forward: Boolean, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (forward) {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            },
            contentDescription = description,
            tint = LocalTodoColors.current.dateMuted
        )
    }
}

/** 甜甜圈本体：一块一块按占比画圆弧，块与块之间留一点缝。 */
@Composable
private fun Donut(slices: List<FocusSlice>, total: Int, palette: List<Color>) {
    Canvas(modifier = Modifier.size(DonutDiameter)) {
        val stroke = DonutStroke.toPx()
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)

        if (slices.size <= 1) {
            // 只有一块时不要那个缝：一整圈才看得出「这一天全花在它上面」。
            drawArc(
                color = palette.first(),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            return@Canvas
        }

        // 每块之间空一点：最多空掉整圈的 1/8，块数多的时候缝自己会变细。
        val gap = minOf(2f, 45f / slices.size)
        var start = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.seconds / total
            drawArc(
                color = palette[index % palette.size],
                startAngle = start,
                sweepAngle = (sweep - gap).coerceAtLeast(1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt)
            )
            start += sweep
        }
    }
}

/** 图例的一行：色点 + 任务名 + 时长 + 百分比。 */
@Composable
private fun SliceLegendRow(label: String, seconds: Int, fraction: Float, color: Color) {
    val colors = LocalTodoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
        Spacer(Modifier.width(5.dp))
        Text(
            text = "${(fraction * 100f).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * 饼图里的这么一块：哪个任务、多少秒。[others] 为 true 表示「前几名之外的其他任务」；
 * [label] 为 null（而 [others] 为 false）表示那几段专注当时没有关联任务。
 */
private data class FocusSlice(val label: String?, val seconds: Int, val others: Boolean = false)

/**
 * 某一天按任务切开的专注时长。
 *
 * 只取前 [MaxSlices] 条（排行已经是按时长降序的），剩下的合成一条「其他」——否则图例会
 * 比抽屉还长，而零碎的几条本来也看不出什么。分母用的是**这一天真实的总时长**，不是前几名
 * 的和，所以「其他」永远等于差额，不会凭空多出一块。
 */
private fun daySlices(sessions: List<FocusSession>, day: LocalDate, zone: ZoneId): List<FocusSlice> {
    val period = FocusPeriod(day, day)
    val ranking = focusRanking(sessions, period, zone, FocusRankBy.TASK, limit = MaxSlices)
    if (ranking.isEmpty()) return emptyList()

    val total = focusPeriodTotals(sessions, period, zone).seconds
    val ranked = ranking.sumOf { it.seconds }
    val slices = ranking.map { FocusSlice(label = it.label, seconds = it.seconds) }.toMutableList()
    if (total > ranked) {
        slices += FocusSlice(label = null, seconds = total - ranked, others = true)
    }
    return slices
}

/** 饼图里的时长：`45m` / `1h20m` / `12s`，比一整句「X 分钟」短，塞得进图例。 */
private fun shortDuration(seconds: Int): String {
    if (seconds <= 0) return "0m"
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "${hours}h" else "${hours}h${"%02d".format(rest)}m"
}
