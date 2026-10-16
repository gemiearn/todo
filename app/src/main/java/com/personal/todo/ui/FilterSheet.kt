package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.CalendarSettings
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskFilter
import com.personal.todo.ui.theme.LocalTodoColors

/**
 * 显示范围筛选面板（说明文档 4.4）：清单 / 优先级 / 标签三个维度。
 *
 * 维度之间是「且」、维度内部是「或」——勾两个清单看的就是这两个清单，再勾标签就得
 * 同时落在选中的清单和选中的标签里。三个维度都空时等于没筛。列表顶栏和日历 ⋮ 用的
 * 是同一个面板，只是筛选结果各自独立。
 *
 * [extra] 是给日历留的槽：⋮ 面板要在筛选下面再接一段「显示设置」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    title: String,
    lists: List<TaskList>,
    tags: List<Tag>,
    filter: TaskFilter,
    onFilterChange: (TaskFilter) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = LocalTodoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            FilterSection(stringResource(R.string.filter_dimension_list)) {
                lists.forEach { list ->
                    ToggleChip(
                        label = list.name,
                        selected = list.id in filter.listIds,
                        accent = colors.accent,
                        onClick = {
                            onFilterChange(filter.copy(listIds = filter.listIds.toggle(list.id)))
                        }
                    )
                }
            }

            FilterSection(stringResource(R.string.filter_dimension_priority)) {
                Priority.entries.forEach { priority ->
                    ToggleChip(
                        label = priorityLabel(priority),
                        selected = priority in filter.priorities,
                        accent = priorityColor(priority),
                        onClick = {
                            onFilterChange(
                                filter.copy(priorities = filter.priorities.toggle(priority))
                            )
                        }
                    )
                }
            }

            FilterSection(stringResource(R.string.filter_dimension_tag)) {
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted
                    )
                } else {
                    tags.forEach { tag ->
                        ToggleChip(
                            label = tag.name,
                            selected = tag.id in filter.tagIds,
                            accent = Color(tag.color),
                            onClick = {
                                onFilterChange(filter.copy(tagIds = filter.tagIds.toggle(tag.id)))
                            }
                        )
                    }
                }
            }

            extra?.invoke(this)

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!filter.isEmpty) {
                    TextButton(onClick = { onFilterChange(TaskFilter()) }) {
                        Text(stringResource(R.string.filter_clear))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }
}

/** One dimension of the panel: its name, then the chips of that dimension. */
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = LocalTodoColors.current.dateMuted
        )
        Spacer(Modifier.height(8.dp))
        // A row that scrolls sideways: however many chips a dimension has, it never
        // has to wrap or measure - the panel stays a plain vertical scroll.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

/**
 * 日历的「显示设置」三段（说明文档 4.2）：显示已完成 / 显示周数 / 每周从周一开始。
 * 它挂在日历 ⋮ 面板的筛选下面，是这个面板 [FilterSheet] 的 extra 槽。
 */
@Composable
fun DisplaySettingsSection(
    settings: CalendarSettings,
    onChange: (CalendarSettings) -> Unit
) {
    val colors = LocalTodoColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.calendar_display_settings),
            style = MaterialTheme.typography.labelMedium,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(4.dp))
        SettingSwitch(
            label = stringResource(R.string.calendar_show_completed),
            checked = settings.showCompleted,
            onChange = { onChange(settings.copy(showCompleted = it)) }
        )
        SettingSwitch(
            label = stringResource(R.string.calendar_show_week_numbers),
            checked = settings.showWeekNumbers,
            onChange = { onChange(settings.copy(showWeekNumbers = it)) }
        )
        SettingSwitch(
            label = stringResource(R.string.calendar_week_start_monday),
            checked = settings.weekStartsMonday,
            onChange = { onChange(settings.copy(weekStartsMonday = it)) }
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LocalTodoColors.current.accent)
        )
    }
}

/** 集合里加一个就是勾上，去掉一个就是取消——筛选维度内部「或」的基本操作。 */
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/**
 * 顶栏筛选按钮的图标：一个越来越窄的漏斗（三条长度递减的横线）。手画而不是用
 * 图标库里的 FilterList，是因为这个项目只依赖 material-icons-core，那里面没有它。
 */
@Composable
fun FilterGlyph(tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(1f, 0.66f, 0.33f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(vertical = 1.5.dp)
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}
