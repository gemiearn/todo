package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.ListSummary
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskCollection
import java.time.ZoneId

/**
 * The list of one view (inbox / a user list / completed / trash), drawn as the
 * screenshots show it: grey canvas, one white card per task, and a coloured bar
 * marking its left edge.
 *
 * The view is a query result - the same task row is never copied around, and
 * what a row offers depends on the view: open tasks can be completed or
 * deleted (by checkbox, by the swipe gestures, or from the detail screen),
 * completed tasks can be reopened, trashed tasks can be restored.
 *
 * A list keeps its finished tasks folded away behind a "已完成 N" header, so
 * completing a task removes it from the working list without hiding it.
 */
@Composable
fun TaskCollectionContent(
    collection: TaskCollection,
    tasks: List<Task>,
    completedTasks: List<Task>,
    subtaskCounts: Map<Long, Int>,
    tagsByTask: Map<Long, List<Tag>>,
    emptyText: String,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onComplete: (Task) -> Unit,
    onReopen: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onRestore: (Task) -> Unit,
    modifier: Modifier = Modifier,
    /** 清单小结（收件箱与用户清单各一条）；其它视图给 null，就不画那条。 */
    summary: ListSummary? = null,
    /** 任务卡上的「开始专注」；给 null 就不显示那个按钮。 */
    onQuickFocus: ((Task) -> Unit)? = null
) {
    val foldsCompleted = when (collection) {
        TaskCollection.Inbox,
        is TaskCollection.ListDetail,
        TaskCollection.Today,
        is TaskCollection.DayView -> true

        TaskCollection.Completed,
        TaskCollection.Trash,
        TaskCollection.Future,
        TaskCollection.Overdue,
        TaskCollection.None -> false
    }

    if (tasks.isEmpty() && !(foldsCompleted && completedTasks.isNotEmpty())) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTodoColors.current.dateMuted,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val nowMillis = rememberNowMillis()
    var completedExpanded by rememberSaveable { mutableStateOf(false) }

    val deleteLabel = stringResource(R.string.action_delete)
    val reopenLabel = stringResource(R.string.action_reopen)
    val restoreLabel = stringResource(R.string.action_restore)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 清单小结排在任务行之前：先说这个清单整体什么情况，再看具体任务。
        if (summary != null) {
            item(key = "list-summary") { ListSummaryCard(summary) }
        }

        items(tasks, key = { it.id }) { task ->
            val subtaskCount = subtaskCounts[task.id] ?: 0
            val tags = tagsByTask[task.id].orEmpty()
            when (collection) {
                TaskCollection.Trash -> TaskCard(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = null,
                    onCheckedChange = null,
                    onClick = { onOpenTask(task.id) },
                    nowMillis = nowMillis,
                    zone = zone,
                    actionLabel = restoreLabel,
                    onAction = { onRestore(task) },
                    tags = tags
                )

                TaskCollection.Completed -> TaskCard(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = true,
                    onCheckedChange = { onReopen(task) },
                    onClick = { onOpenTask(task.id) },
                    nowMillis = nowMillis,
                    zone = zone,
                    actionLabel = reopenLabel,
                    onAction = { onReopen(task) },
                    tags = tags
                )

                else -> SwipeableTaskCard(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = false,
                    onCheckedChange = { checked ->
                        if (checked) onComplete(task) else onReopen(task)
                    },
                    onClick = { onOpenTask(task.id) },
                    nowMillis = nowMillis,
                    zone = zone,
                    onSwipeComplete = { onComplete(task) },
                    onSwipeDelete = { onDelete(task) },
                    tags = tags,
                    // 「开始专注」只在还能干这件事的清单里给（回收站 / 已完成那两个分支
                    // 走的是另一条路，没有它）。
                    onQuickFocus = onQuickFocus?.let { focus -> { focus(task) } }
                )
            }
        }

        if (foldsCompleted && completedTasks.isNotEmpty()) {
            item(key = "completed-group-header") {
                CompletedGroupHeader(
                    count = completedTasks.size,
                    expanded = completedExpanded,
                    onToggle = { completedExpanded = !completedExpanded }
                )
            }
            if (completedExpanded) {
                items(completedTasks, key = { "completed-${it.id}" }) { task ->
                    TaskCard(
                        task = task,
                        subtaskCount = subtaskCounts[task.id] ?: 0,
                        checked = true,
                        onCheckedChange = { onReopen(task) },
                        onClick = { onOpenTask(task.id) },
                        nowMillis = nowMillis,
                        zone = zone,
                        actionLabel = reopenLabel,
                        onAction = { onReopen(task) },
                        tags = tagsByTask[task.id].orEmpty()
                    )
                }
            }
        }
    }
}

/**
 * 清单页顶部那条小结：一条完成率进度条 + 「已完成 n / 共 m」+ 该清单累计专注时长。
 *
 * 数字全部来自 `TaskRepository.observeListSummary`，与下面那份列表同一口径（未删除顶层任务、
 * 不含重复实例），所以两处永远对得上。
 */
@Composable
private fun ListSummaryCard(summary: ListSummary) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.list_summary_progress,
                    summary.completed,
                    summary.total
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.list_summary_focus,
                    durationText(summary.focusSeconds)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        // 自己画那条进度条：比拉 Material3 的进度条少一层依赖，也更好跟卡片圆角对齐。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.groupHeader)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(colors.accent)
            )
        }
    }
}

/** The "已完成 N ˅" strip that folds the finished tasks of a list away. */
@Composable
private fun CompletedGroupHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.groupHeader)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nav_completed),
            style = MaterialTheme.typography.labelLarge,
            color = colors.dateMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.dateMuted
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(
                if (expanded) R.string.action_hide_completed else R.string.action_show_completed
            ),
            tint = colors.dateMuted
        )
    }
}
