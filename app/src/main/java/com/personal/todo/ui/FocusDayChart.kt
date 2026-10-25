package com.personal.todo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.model.FocusSliceShare
import com.personal.todo.model.focusDayShares
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import java.time.LocalDate
import java.time.ZoneId

/** 饼图的直径与环宽。 */
private val DonutDiameter = 108.dp
private val DonutStroke = 17.dp

/**
 * 饼图的调色顺序：第一块永远是专注页那个番茄红，后面才轮换标签色。
 *
 * 图例与「按任务」排行都用它，所以同一个任务在饼图和排行里是同一个颜色。
 */
internal fun focusSlicePalette(accent: Color): List<Color> = listOf(
    accent,
    TagPalette[3],
    TagPalette[2],
    TagPalette[1],
    TagPalette[4],
    TagPalette[6]
)

/**
 * 「专注占比」页顶上的那一天饼图（用户 2026-10-25：占比该是一页自己的东西，日期由页面
 * 说了算，切到哪天就看哪天）。
 *
 * 内容是 [day] 那一天按任务切开的甜甜圈：中间是这一天的总时长，下面是图例（色点 + 任务名 +
 * 时长 + 占比）。那对箭头只挪这一天、不改任何记录；日期本身可以点回「今天」。
 *
 * 图例的每一行都能点：点中哪个任务，页面下面那块记录列表就只留那一天这件事的那几段，
 * 再点一次取消。这一层不自己持有日期与选中的任务——两者都由页面说了算，否则「我点的是
 * 今天，下面显示的却是昨天」这种对不上还会回来。
 */
@Composable
internal fun FocusDayDonutCard(
    sessions: List<FocusSession>,
    day: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    selectedTaskId: Long?,
    onSelectTask: (Long?) -> Unit,
    onStepDay: (Int) -> Unit,
    onPickToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val shares = remember(sessions, day, zone) { focusDayShares(sessions, day, zone) }
    val totalSeconds = shares.sumOf { it.seconds }
    val palette = remember(colors.focusAccent) { focusSlicePalette(colors.focusAccent) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_donut_card_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            DayStepButton(
                forward = false,
                description = stringResource(R.string.focus_donut_prev),
                onClick = { onStepDay(-1) }
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
                    .clickable(onClick = onPickToday)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            DayStepButton(
                forward = true,
                description = stringResource(R.string.focus_donut_next),
                onClick = { onStepDay(1) }
            )
        }

        if (totalSeconds <= 0) {
            Text(
                text = stringResource(R.string.focus_donut_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, bottom = 4.dp)
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        // 饼图单独占一行居中，图例在它下面铺满整行：并排放的话图例只剩六七十 dp，
        // 任务名一律被省略号吃掉。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Donut(shares = shares, total = totalSeconds, palette = palette)
                Text(
                    text = shortDuration(totalSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.focusAccent
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shares.forEachIndexed { index, share ->
                SliceLegendRow(
                    label = when {
                        share.others -> stringResource(R.string.focus_donut_other)
                        share.label == null -> stringResource(R.string.focus_task_none_label)
                        else -> share.label
                    },
                    seconds = share.seconds,
                    fraction = share.fraction,
                    color = palette[index % palette.size],
                    // 「其他」是好几件事合起来的一块，点它没有「只看这件事」可言，
                    // 所以那一行不做成可点的。
                    selectable = share.taskId != null,
                    selected = share.taskId != null && share.taskId == selectedTaskId,
                    onClick = { onSelectTask(share.taskId) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 选着某个任务时，把「再点一次就取消」这句话写清楚——图例那几行看起来只是行文字，
        // 不说一句没人知道点得动；没选时这行就是这一天的合计。
        val selectedLabel = shares
            .firstOrNull { it.taskId != null && it.taskId == selectedTaskId }
            ?.label
        Text(
            text = if (selectedLabel != null) {
                stringResource(R.string.focus_donut_tap_to_clear, selectedLabel)
            } else {
                stringResource(R.string.focus_donut_total, shortDuration(totalSeconds))
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

/** 那对只挪一天的箭头（32dp，和日历上的步进箭头同一个尺寸）。 */
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
private fun Donut(shares: List<FocusSliceShare>, total: Int, palette: List<Color>) {
    Canvas(modifier = Modifier.size(DonutDiameter)) {
        val stroke = DonutStroke.toPx()
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)

        if (shares.size <= 1) {
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
        val gap = minOf(2f, 45f / shares.size)
        var start = -90f
        shares.forEachIndexed { index, share ->
            val sweep = 360f * share.seconds / total
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

/** 图例的一行：色点 + 任务名 + 时长 + 百分比；可点的那几行点中之后描一圈淡底。 */
@Composable
private fun SliceLegendRow(
    label: String,
    seconds: Int,
    fraction: Float,
    color: Color,
    selectable: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    val shape = RoundedCornerShape(8.dp)
    // 读屏要能说出「点这一行会发生什么」：它看起来只是一行字，不说明的话没人知道点得动。
    val description = if (selectable) {
        stringResource(
            if (selected) R.string.focus_donut_filter_off else R.string.focus_donut_filter_on,
            label
        )
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.10f) else Color.Transparent)
            .then(
                if (selectable) {
                    Modifier
                        .clickable(
                            onClickLabel = description,
                            onClick = onClick
                        )
                        .semantics { contentDescription = description.orEmpty() }
                } else {
                    Modifier
                }
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) color.copy(alpha = 0.35f) else Color.Transparent,
                shape = shape
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

/** 饼图 / 排行里的时长：`45m` / `1h20m` / `12s`，比一整句「X 分钟」短，塞得进一行。 */
internal fun shortDuration(seconds: Int): String {
    if (seconds <= 0) return "0m"
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "${hours}h" else "${hours}h${"%02d".format(rest)}m"
}
