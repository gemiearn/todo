package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 同时落在选中的清单和选中的标签里。三个维度都空时等于没筛。列表顶栏、日历的筛选条
 * 用的是同一个面板，只是筛选结果各自独立。
 *
 * 2026-10-21 重做（用户要求「重新设计和美化」）：
 *  - 每个维度一张独立的小卡片，带图标、名字和「已选几个」，不再三组裸排在白底上；
 *  - 胶囊改成**换行铺开**（[FlowRow]）而不是横向滚动——原来标签一多就得左右摸，
 *    右半边有什么全靠猜；
 *  - 每个维度前面有一枚「全部」：点它就是清掉这一维，比再去底部找「清除筛选」直观；
 *  - 顶部标题行带漏斗图标与一句口径说明，筛过东西时右边出现「清除筛选」。
 *
 * [extra] 是给日历留的槽：面板要在筛选下面再接一段「显示设置」。
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
        containerColor = colors.card,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            FilterSheetHeader(
                title = title,
                selectedCount = filter.selectedCount(),
                onClear = { onFilterChange(TaskFilter()) }
            )

            Spacer(Modifier.height(6.dp))

            FilterSectionCard(
                icon = "📋",
                title = stringResource(R.string.filter_dimension_list),
                selectedCount = filter.listIds.size
            ) {
                AllChip(
                    selected = filter.listIds.isEmpty(),
                    accent = colors.accent,
                    onClick = { onFilterChange(filter.copy(listIds = emptySet())) }
                )
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

            Spacer(Modifier.height(10.dp))

            FilterSectionCard(
                icon = "🎯",
                title = stringResource(R.string.filter_dimension_priority),
                selectedCount = filter.priorities.size
            ) {
                AllChip(
                    selected = filter.priorities.isEmpty(),
                    accent = colors.accent,
                    onClick = { onFilterChange(filter.copy(priorities = emptySet())) }
                )
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

            Spacer(Modifier.height(10.dp))

            FilterSectionCard(
                icon = "🏷️",
                title = stringResource(R.string.filter_dimension_tag),
                selectedCount = filter.tagIds.size
            ) {
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted
                    )
                } else {
                    AllChip(
                        selected = filter.tagIds.isEmpty(),
                        accent = colors.accent,
                        onClick = { onFilterChange(filter.copy(tagIds = emptySet())) }
                    )
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

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text(
                    text = stringResource(R.string.filter_apply),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 面板顶部：漏斗图标 + 标题 + 口径说明，筛过东西时右边是「清除筛选」。 */
@Composable
private fun FilterSheetHeader(
    title: String,
    selectedCount: Int,
    onClear: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            FilterGlyph(tint = colors.accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.filter_sheet_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1
            )
        }
        if (selectedCount > 0) {
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.filter_clear),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
            }
        }
    }
}

/**
 * 一个维度的小卡片：图标 + 名字 + 「n」的淡色计数，下面是换行的胶囊区。
 *
 * 卡片底只比面板底色深一点点（groupHeader 的 32%），看上去是一块「分组」而不是又一层卡片。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSectionCard(
    icon: String,
    title: String,
    selectedCount: Int,
    content: @Composable () -> Unit
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.5.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.07f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(colors.groupHeader.copy(alpha = 0.32f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selectedCount > 0) {
                Spacer(Modifier.width(6.dp))
                CountBadge(count = selectedCount, tint = colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            content()
        }
    }
}

/** 每个维度前面那枚「全部」：点它清掉这个维度（不影响别的维度）。 */
@Composable
private fun AllChip(selected: Boolean, accent: Color, onClick: () -> Unit) {
    ToggleChip(
        label = stringResource(R.string.filter_section_all),
        selected = selected,
        accent = accent,
        onClick = onClick
    )
}

/**
 * 日历的「显示设置」三段（说明文档 4.2）：显示已完成 / 显示周数 / 每周从周一开始。
 * 它挂在日历筛选面板的筛选下面，是这个面板 [FilterSheet] 的 extra 槽。
 */
@Composable
fun DisplaySettingsSection(
    settings: CalendarSettings,
    onChange: (CalendarSettings) -> Unit
) {
    val colors = LocalTodoColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 1.5.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.07f)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(colors.groupHeader.copy(alpha = 0.32f))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "👁️", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.calendar_display_settings),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            SettingSwitch(
                label = stringResource(R.string.calendar_show_completed),
                checked = settings.showCompleted,
                onChange = { onChange(settings.copy(showCompleted = it)) }
            )
            HorizontalDivider(color = colors.dateMuted.copy(alpha = 0.12f))
            SettingSwitch(
                label = stringResource(R.string.calendar_show_week_numbers),
                checked = settings.showWeekNumbers,
                onChange = { onChange(settings.copy(showWeekNumbers = it)) }
            )
            HorizontalDivider(color = colors.dateMuted.copy(alpha = 0.12f))
            SettingSwitch(
                label = stringResource(R.string.calendar_week_start_monday),
                checked = settings.weekStartsMonday,
                onChange = { onChange(settings.copy(weekStartsMonday = it)) }
            )
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
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
 * 顶栏 / 日历筛选条上那个漏斗图标：三条长度递减的横线。手画而不是用图标库里的
 * FilterList，是因为这个项目只依赖 material-icons-core，那里面没有它。
 */
@Composable
fun FilterGlyph(tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(1f, 0.68f, 0.36f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(vertical = 1.4.dp)
                    .fillMaxWidth(fraction)
                    .height(1.8.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}
