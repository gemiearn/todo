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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.model.Priority
import com.personal.todo.model.nearestOpenOccurrencePerSeries
import com.personal.todo.ui.theme.LocalTodoColors
import kotlinx.coroutines.flow.Flow

private val QuadrantCardShape = RoundedCornerShape(18.dp)

/**
 * How strong a quadrant tints its own card. It is deliberately noticeable rather
 * than barely-there: 色系 is a real choice the user makes, and a tint nobody can
 * see makes the setting look broken. Still faint enough to leave the task text
 * perfectly readable on top of it.
 */
private const val QuadrantTintAlpha = 0.20f

/**
 * The 四象限 page of the screenshots: a two by two grid of white cards, each
 * with a coloured roman numeral badge, the tasks that belong to it and - when it
 * is empty - the grey "没有任务" in the middle.
 *
 * A task's quadrant *is* its stored [Priority]: 重要 and 紧急 are two switches the
 * user sets in the editor, not something derived from a due date. A task
 * therefore only ever changes box when the user moves it, never because a day
 * passed.
 *
 * 这一页**只读**：它是「决定优先级」的地方，不是「完成任务」的地方，所以任务卡上既没有
 * 打勾也没有滑动完成——点一行只是进任务详情，要完成请回列表或详情页。
 */
@Composable
fun QuadrantScreen(
    openTasks: Flow<List<Task>>,
    completedTasks: Flow<List<Task>>,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
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
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuadrantCard(
                priority = Priority.IMPORTANT_NOT_URGENT,
                tasks = grouped[Priority.IMPORTANT_NOT_URGENT].orEmpty(),
                onOpenTask = onOpenTask,
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
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QuadrantCard(
                priority = Priority.NOT_IMPORTANT_NOT_URGENT,
                tasks = grouped[Priority.NOT_IMPORTANT_NOT_URGENT].orEmpty(),
                onOpenTask = onOpenTask,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun QuadrantCard(
    priority: Priority,
    tasks: List<Task>,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val accent = priorityColor(priority)

    Column(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = QuadrantCardShape,
                ambientColor = accent.copy(alpha = 0.08f),
                spotColor = accent.copy(alpha = 0.10f)
            )
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                // 两条任务之间画一条淡淡的分隔线：一列里挤着最多两行标题的任务时，光靠留白
                // 看不出上一条到哪儿结束。线只画在两条之间，最后一条下面不画。
                tasks.forEachIndexed { index, task ->
                    item(key = "task-${task.id}") {
                        QuadrantTaskRow(
                            task = task,
                            completed = task.completedAt != null,
                            onClick = { onOpenTask(task.id) }
                        )
                    }
                    if (index != tasks.lastIndex) {
                        item(key = "divider-${task.id}") {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = colors.dateMuted.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一条任务：只有标题——这一页不打勾，也不再显示日期（它是「决定优先级」的地方）。 */
@Composable
private fun QuadrantTaskRow(
    task: Task,
    completed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) colors.doneInk else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 角标上的罗马数字：Ⅰ 红、Ⅱ 琥珀、Ⅲ 蓝、Ⅳ 绿，颜色见 [priorityColor]。 */
internal fun priorityNumeralRes(priority: Priority): Int = when (priority) {
    Priority.IMPORTANT_URGENT -> R.string.quadrant_numeral_1
    Priority.IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_2
    Priority.NOT_IMPORTANT_URGENT -> R.string.quadrant_numeral_3
    Priority.NOT_IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_4
}
