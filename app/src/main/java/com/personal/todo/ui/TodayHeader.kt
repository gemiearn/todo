package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 首页（今天）最上面那条小结：今天是几号星期几、还剩几件、做完了几件，外加一条进度。
 *
 * 用户 2026-10-21 要求「首页面也美化一下」。原来这一页除了标题就是卡片，一进来不知道今天
 * 到底还剩多少事；现在先给一条浅浅的渐变卡把这件事说清楚——数字全部由这一页已经拿到的
 * 两份列表现算，不另开查询，所以不会和下面的卡片对不上。
 */
@Composable
internal fun TodaySummaryCard(
    date: LocalDate,
    openCount: Int,
    doneCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val total = openCount + doneCount
    val progress = if (total <= 0) 0f else doneCount.toFloat() / total
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = colors.accent.copy(alpha = 0.06f),
                spotColor = colors.accent.copy(alpha = 0.10f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(colors.card, lerp(colors.card, colors.accent, 0.09f)))
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_today),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.date_month_day,
                        date.monthValue,
                        date.dayOfMonth
                    ) + " · " + stringResource(weekdayRes(date.dayOfWeek)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
            }
            // 右边只报「还剩几件」：这是打开这一页时唯一真正要回答的问题。
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = openCount.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (openCount == 0) colors.success else colors.accent
                )
                Text(
                    text = if (total == 0) {
                        stringResource(R.string.today_summary_empty)
                    } else {
                        stringResource(R.string.today_summary_left, openCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }
        }

        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            // 进度条自己画：一圈 6dp 的圆角底 + 一段完成的占比，不引额外的组件。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.groupHeader)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.success)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (openCount == 0) {
                    Text(
                        text = stringResource(R.string.today_summary_all_done),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.success
                    )
                } else {
                    Text(
                        text = stringResource(R.string.today_summary_left, openCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.today_summary_done, doneCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                }
            }
        }
    }
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
