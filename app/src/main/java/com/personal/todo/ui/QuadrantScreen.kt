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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.model.Priority
import com.personal.todo.model.nearestOpenOccurrencePerSeries
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

private val QuadrantCardShape = RoundedCornerShape(14.dp)

/**
 * How strong a quadrant tints its own card. It is deliberately faint: the tint is
 * what makes the page feel light and colourful, and anything stronger would fight
 * the task text printed on top of it.
 */
private const val QuadrantTintAlpha = 0.10f

/**
 * The 四象限 page of the screenshots: a two by two grid of white cards, each
 * with a coloured roman numeral badge, the tasks that belong to it and - when it
 * is empty - the grey "没有任务" in the middle.
 *
 * A task's quadrant *is* its stored [Priority]: 重要 and 紧急 are two switches the
 * user sets in the editor, not something derived from a due date. A task
 * therefore only ever changes box when the user moves it, never because a day
 * passed.
 */
@Composable
fun QuadrantScreen(
    openTasks: Flow<List<Task>>,
    completedTasks: Flow<List<Task>>,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onComplete: (Task) -> Unit,
    onReopen: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = rememberNowMillis()
    val open by openTasks.collectAsStateWithLifecycle(emptyList())
    val completed by completedTasks.collectAsStateWithLifecycle(emptyList())

    // 一串重复任务在这里也只占一格：只显示最近那条还没做完的。库里那些未来的每一次
    // 都还在，日历上也照旧按天画——收拢只发生在打开态的列表里。
    val visibleOpen = remember(open) { nearestOpenOccurrencePerSeries(open) }

    // Open tasks first, then the finished ones faded below: the screenshots show
    // the finished tasks of a quadrant the same way.
    val grouped = remember(visibleOpen, completed) {
        val byPriority = mutableMapOf<Priority, MutableList<Task>>()
        (visibleOpen + completed).forEach { task ->
            byPriority.getOrPut(task.priority) { mutableListOf() }.add(task)
        }
        byPriority
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuadrantCard(
                priority = Priority.IMPORTANT_URGENT,
                tasks = grouped[Priority.IMPORTANT_URGENT].orEmpty(),
                nowMillis = nowMillis,
                zone = zone,
                onOpenTask = onOpenTask,
                onComplete = onComplete,
                onReopen = onReopen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuadrantCard(
                priority = Priority.IMPORTANT_NOT_URGENT,
                tasks = grouped[Priority.IMPORTANT_NOT_URGENT].orEmpty(),
                nowMillis = nowMillis,
                zone = zone,
                onOpenTask = onOpenTask,
                onComplete = onComplete,
                onReopen = onReopen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuadrantCard(
                priority = Priority.NOT_IMPORTANT_URGENT,
                tasks = grouped[Priority.NOT_IMPORTANT_URGENT].orEmpty(),
                nowMillis = nowMillis,
                zone = zone,
                onOpenTask = onOpenTask,
                onComplete = onComplete,
                onReopen = onReopen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuadrantCard(
                priority = Priority.NOT_IMPORTANT_NOT_URGENT,
                tasks = grouped[Priority.NOT_IMPORTANT_NOT_URGENT].orEmpty(),
                nowMillis = nowMillis,
                zone = zone,
                onOpenTask = onOpenTask,
                onComplete = onComplete,
                onReopen = onReopen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun QuadrantCard(
    priority: Priority,
    tasks: List<Task>,
    nowMillis: Long,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onComplete: (Task) -> Unit,
    onReopen: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val accent = priorityColor(priority)

    Column(
        modifier = modifier
            .clip(QuadrantCardShape)
            // 卡片先铺白/深灰底，再罩一层这个象限自己的淡色——「清新」就来自这一层。
            .background(colors.card)
            .background(accent.copy(alpha = QuadrantTintAlpha))
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = stringResource(priorityNumeralRes(priority)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onPriorityColor(accent)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = priorityLabel(priority),
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
        Spacer(Modifier.height(6.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_quadrant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.dateMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    val completed = task.completedAt != null
                    QuadrantTaskRow(
                        task = task,
                        checked = completed,
                        onCheckedChange = { checked ->
                            if (checked) onComplete(task) else onReopen(task)
                        },
                        onClick = { onOpenTask(task.id) },
                        nowMillis = nowMillis,
                        zone = zone
                    )
                }
            }
        }
    }
}

/** One task inside a quadrant: checkbox, title and its date underneath. */
@Composable
private fun QuadrantTaskRow(
    task: Task,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    nowMillis: Long,
    zone: ZoneId,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val (dateText, dateColor) = taskDateLabel(task, nowMillis, zone)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = if (checked) colors.doneInk else colors.accent,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) colors.doneInk else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (dateText != null) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = dateColor,
                    maxLines = 1
                )
            }
        }
    }
}

/** 角标上的罗马数字：Ⅰ 红、Ⅱ 琥珀、Ⅲ 蓝、Ⅳ 绿，颜色见 [priorityColor]。 */
internal fun priorityNumeralRes(priority: Priority): Int = when (priority) {
    Priority.IMPORTANT_URGENT -> R.string.quadrant_numeral_1
    Priority.IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_2
    Priority.NOT_IMPORTANT_URGENT -> R.string.quadrant_numeral_3
    Priority.NOT_IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_4
}
