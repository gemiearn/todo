package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import com.personal.todo.ui.theme.DefaultTagColor
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.LocalDate
import java.time.LocalTime

/** The tag the smoke tests use to type a title into the editor. */
internal const val EditorTitleTag = "editor-title"

/** The tag the smoke tests use to reach the icon toolbar of the editor. */
internal const val EditorToolbarTag = "editor-toolbar"

/** The tag the smoke tests use to reach the「新建标签」入口 of the editor's 标签 group. */
internal const val EditorNewTagTag = "editor-new-tag"

/**
 * 编辑器里能就地展开的三块面板：优先级 / 清单 / 备注。
 *
 * 日期**不**在这里：日期有自己的弹层（[TaskDateSheet]：月历 + 时间 + 重复 + 时间段），
 * 点属性行上那枚日期就是开它。原来「日期」也占一格面板，可面板里只有「计划日期 / 时间段」
 * 两行，点进去还是开同一个弹层——白占一层高度。
 */
private enum class EditorGroup(val labelRes: Int, val emoji: String) {
    PRIORITY(R.string.task_priority_label, "🎯"),
    LIST(R.string.editor_tab_list, "📋"),
    NOTES(R.string.editor_tab_notes, "📝")
}

/**
 * 编辑任务（推入的一页）：顶栏是 ✕ 和保存，下面是和「新建任务」弹层共用的同一份内容——
 * 标题行、属性行（日期 / 优先级 / 清单 / 备注）、当前展开的那一块。区别只有两处：
 * 没有快捷日期那一行（改一条已经有日期的任务，用不着「今天 / 明天」），标题行尾也没有 ✕。
 *
 * All the fields the editor had before are still here and nothing new was added;
 * only 优先级 changed shape - 重要 and 紧急 are now two switches, because a task's
 * quadrant *is* its priority (see [Priority]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    // 读库没回来、或者上一次保存还没回来：两个保存按钮都不收点击。
    val saveEnabled = !loading && !saving

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = { SaveIconButton(onClick = viewModel::save, enabled = saveEnabled) }
            )
        }
    ) { innerPadding ->
        if (loading) {
            // 表单在读库返回前不画：输入框一旦画出来，用户敲的字就会被回流结果盖掉。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.editor_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_title_edit),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            TaskEditorBody(viewModel = viewModel, showQuickDates = false)
        }
    }
}

/**
 * 新建任务的底部弹层。
 *
 * 2026-10-22 又重做了一次（用户说「布局很不好」）：这一版的原则是**一层边界**——
 * 整张弹层里不再出现「卡片里套描边框、框下面又是一张卡片」，只有输入区、属性行、
 * 面板之间那几条 1dp 的分隔线。具体的减法：
 *
 * - 顶部那颗 ✍️ + 「新建任务」+ 说明文字整块撤掉，只留把手；关闭键挪到标题那一行的行尾。
 *   标题就是输入框本身（占位文字「要做什么？」，原来占位是「标题」两个字），不再套一层
 *   带描边的 `OutlinedTextField`——它就是这一行的名字，不需要再被一个框圈起来。
 * - 快捷日期从「五个等分胶囊」改成一行靠左的小字，够用就好，不再像一排按钮。
 * - 图标工具条换成属性行：四枚胶囊各自显示**当前值**（日期 / 优先级 / 清单 / 备注），
 *   点一下才在下面展开那一块；一进来什么都不展开，所以这张弹层不再一开就要滚动。
 *
 * 字段一个都没少，改的只是它们排成什么样。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalTodoColors.current

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        // 自己画一颗更细的把手：默认那颗跟着拖拽槽一起往下压，内容就离顶太远。
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.dateMuted.copy(alpha = 0.28f))
                )
            }

            // 标题、快捷日期、属性行、面板：全在 [TaskEditorBody] 里，和编辑页共用同一份。
            // onClose 只有弹层传：编辑页的 ✕ 在顶栏上，不必再来一个。
            TaskEditorBody(
                viewModel = viewModel,
                showQuickDates = true,
                onClose = onDismiss
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::save,
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** The purple ↑ of the reference: the one way to save a task. 保存中 / 读库中时它不收点击。 */
@Composable
private fun SaveIconButton(onClick: () -> Unit, enabled: Boolean) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = LocalTodoColors.current.accent,
            contentColor = Color.White
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.action_save)
        )
    }
}

/**
 * The editor content, shared by the page and the bottom sheet: 标题行、快捷日期（只有弹层
 * 显示）、属性行、当前展开的那一块面板。It also owns the date / time dialogs, so both
 * callers get the same behaviour without repeating it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorBody(
    viewModel: TaskEditorViewModel,
    showQuickDates: Boolean,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    // 展开的是哪一块面板：-1 = 都没开。新建任务一进来什么都不展开，弹层就只有标题、
    // 快捷日期、属性行三行——原来默认展开「日期」，一开就是四行内容 + 一条要滚的尾巴。
    var groupIndex by rememberSaveable { mutableIntStateOf(-1) }
    val group = EditorGroup.entries.getOrNull(groupIndex)

    // 日期、时间段、时刻、重复全在同一个弹层里（照「待办时间」那张图），所以这里只留
    // 一个状态：弹层开着的话停在哪个页签。
    var dateSheet by remember { mutableStateOf<DateSheetTab?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        TitleRow(viewModel = viewModel, onClose = onClose)

        if (showQuickDates) {
            QuickDates(
                today = viewModel.today,
                onPick = { viewModel.onPlannedDateChange(it) },
                onPickExact = { dateSheet = DateSheetTab.DATE }
            )
        }

        Spacer(Modifier.height(if (showQuickDates) 10.dp else 12.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))

        AttributeRow(
            group = group,
            viewModel = viewModel,
            lists = lists,
            onOpenDate = { dateSheet = DateSheetTab.DATE },
            onSelect = { picked ->
                groupIndex = if (groupIndex == picked.ordinal) -1 else picked.ordinal
            }
        )

        if (group != null) {
            Spacer(Modifier.height(4.dp))
            when (group) {
                EditorGroup.PRIORITY -> PriorityGroup(viewModel = viewModel)

                EditorGroup.LIST -> ListGroup(
                    viewModel = viewModel,
                    lists = lists,
                    tags = allTags
                )

                EditorGroup.NOTES -> NotesGroup(viewModel = viewModel)
            }
        }

        // 写库失败：留在这一页，把话说出来（页面与弹层共用这一段）。
        if (viewModel.saveFailed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.editor_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTodoColors.current.dateOverdue
            )
        }
    }
    dateSheet?.let { tab ->
        TaskDateSheet(
            viewModel = viewModel,
            tab = tab,
            onDismiss = { dateSheet = null }
        )
    }
}

// ------------------------------------------------------------------ 标题行

/**
 * 标题那一行：一个**没有框**的输入框 + 行尾的关闭键（只有弹层传 [onClose]）。
 *
 * 用 `BasicTextField` 而不是 `OutlinedTextField`：后者自带 1dp 圆角描边和 16dp 内边距，
 * 摆在弹层里就是「弹层里还有一张表单项」的样子，而且缩进和下面几行对不齐。
 */
@Composable
private fun TitleRow(viewModel: TaskEditorViewModel, onClose: (() -> Unit)?) {
    val colors = LocalTodoColors.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = viewModel.title,
            onValueChange = viewModel::onTitleChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag(EditorTitleTag),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (viewModel.title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.task_title_placeholder),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.dateMuted.copy(alpha = 0.70f),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = colors.dateMuted
                )
            }
        }
    }

    if (viewModel.titleHasError) {
        Text(
            text = stringResource(R.string.task_title_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ---------------------------------------------------------------- 快捷日期

/**
 * 快捷日期行：今天 / 明天 / 下周 / 下个月 / 选日期。
 *
 * 2026-10-22：从「五个等分胶囊」改成**一行靠左的小字**。等分之后每个都撑满一格、
 * 文字居中，看着就是五个按钮；这本来只是一条捷径，不该和下面的保存按钮抢分量。
 * 放不下就横向滑动（其实五个字都在，411dp 的屏也放得下）。
 */
@Composable
private fun QuickDates(today: LocalDate, onPick: (LocalDate) -> Unit, onPickExact: () -> Unit) {
    val colors = LocalTodoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QuickDateChip(
            label = stringResource(R.string.date_today),
            onClick = { onPick(today) }
        )
        QuickDateChip(
            label = stringResource(R.string.date_tomorrow),
            onClick = { onPick(today.plusDays(1)) }
        )
        // 「下周 / 下个月」按同一个日子往后推，不做「下周一」那种额外推断。
        QuickDateChip(
            label = stringResource(R.string.editor_quick_next_week),
            onClick = { onPick(today.plusWeeks(1)) }
        )
        QuickDateChip(
            label = stringResource(R.string.editor_quick_next_month),
            onClick = { onPick(today.plusMonths(1)) }
        )
        QuickDateChip(
            label = stringResource(R.string.editor_quick_pick_date),
            onClick = onPickExact,
            tint = colors.accent
        )
    }
}

/** 快捷日期上的一个字：没有底色、没有边框，只有字（点下去的涟漪是唯一的按钮感）。 */
@Composable
private fun QuickDateChip(
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalTodoColors.current.dateMuted
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------- 属性行

/** 整张编辑器里唯一的分隔线：输入区和属性区之间那一条。 */
@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalTodoColors.current.dateMuted.copy(alpha = 0.15f))
    )
}

/**
 * 属性行：日期 / 优先级 / 清单 / 备注，四枚扁胶囊**等分四格、内容居中**，各自写着**当前值**。
 *
 * 它替掉了原来那排「图标 + 文字」的小方块，也替掉了下面那张面板卡：点一下就在行下面
 * 展开那一块，再点一下收起来；一进来什么都不展开。所以「新建任务」打开就是
 * 标题 + 快捷日期 + 这一行，一共三行，不用滚动。
 *
 * 日期不在 [EditorGroup] 里：它没有「展开」这一说，点它就是开日期弹层。
 *
 * 2026-10-22 第二稿：四枚统一 `weight(1f)`。第一稿给「日期 / 清单」各多分了 0.15 份，
 * 结果两条缝宽两条缝窄，看着像没对齐；而且内容靠左时，字短的「备注」那格右边空一大块。
 */
@Composable
private fun AttributeRow(
    group: EditorGroup?,
    viewModel: TaskEditorViewModel,
    lists: List<TaskList>,
    onOpenDate: () -> Unit,
    onSelect: (EditorGroup) -> Unit
) {
    val dateText = viewModel.plannedDate?.let {
        dateAndTimeText(it, viewModel.plannedTime, viewModel.today)
    }
    val priorityIsSet = viewModel.priority != Priority.DEFAULT
    val listName = lists.firstOrNull { it.id == viewModel.listId }?.name?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier.fillMaxWidth().testTag(EditorToolbarTag),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AttributePill(
            emoji = "📅",
            text = dateText ?: stringResource(R.string.editor_add_date),
            isSet = dateText != null,
            isOpen = false,
            onClick = onOpenDate,
            modifier = Modifier.weight(1f)
        )
        AttributePill(
            emoji = EditorGroup.PRIORITY.emoji,
            text = if (priorityIsSet) {
                priorityNumeral(viewModel.priority)
            } else {
                stringResource(EditorGroup.PRIORITY.labelRes)
            },
            isSet = priorityIsSet,
            isOpen = group == EditorGroup.PRIORITY,
            onClick = { onSelect(EditorGroup.PRIORITY) },
            modifier = Modifier.weight(1f)
        )
        AttributePill(
            emoji = EditorGroup.LIST.emoji,
            text = listName ?: stringResource(EditorGroup.LIST.labelRes),
            isSet = listName != null,
            isOpen = group == EditorGroup.LIST,
            onClick = { onSelect(EditorGroup.LIST) },
            modifier = Modifier.weight(1f)
        )
        AttributePill(
            emoji = EditorGroup.NOTES.emoji,
            text = stringResource(EditorGroup.NOTES.labelRes),
            isSet = viewModel.notes.isNotBlank(),
            isOpen = group == EditorGroup.NOTES,
            onClick = { onSelect(EditorGroup.NOTES) },
            modifier = Modifier.weight(1f)
        )
    }
}

/** 属性行里的一枚：emoji + 当前值，内容在格里居中。设过值的用强调色，展开的那枚带淡底。 */
@Composable
private fun AttributePill(
    emoji: String,
    text: String,
    isSet: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val tint = if (isSet || isOpen) colors.accent else colors.dateMuted

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isOpen) colors.accent.copy(alpha = 0.14f) else colors.groupHeader.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSet || isOpen) FontWeight.SemiBold else null,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 优先级在属性行上只占一个字：Ⅰ / Ⅱ / Ⅲ / Ⅳ，全名在下面展开的那一块里说。 */
@Composable
private fun priorityNumeral(priority: Priority): String = stringResource(
    when (priority) {
        Priority.IMPORTANT_URGENT -> R.string.quadrant_numeral_1
        Priority.IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_2
        Priority.NOT_IMPORTANT_URGENT -> R.string.quadrant_numeral_3
        Priority.NOT_IMPORTANT_NOT_URGENT -> R.string.quadrant_numeral_4
    }
)

// ---------------------------------------------------------------- 各组的行

/** 优先级就是四象限：两个开关，下面一行实时说清楚落进哪一格。 */
@Composable
private fun PriorityGroup(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current

    DimensionSwitch(
        label = stringResource(R.string.editor_toggle_important),
        checked = viewModel.priority.important,
        onCheckedChange = viewModel::onImportantChange
    )
    DimensionSwitch(
        label = stringResource(R.string.editor_toggle_urgent),
        checked = viewModel.priority.urgent,
        onCheckedChange = viewModel::onUrgentChange
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(priorityColor(viewModel.priority))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.editor_priority_result, priorityLabel(viewModel.priority)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
    }
}

@Composable
private fun DimensionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent
            )
        )
    }
}

/**
 * 「清单」那一组：先挑归属的清单（单值），再给这条任务打标签（多值）。清单是任务住
 * 哪，标签是任务身上带什么标记——两者分工不同，所以并排放在同一组里。
 */
@Composable
private fun ListGroup(viewModel: TaskEditorViewModel, lists: List<TaskList>, tags: List<Tag>) {
    val colors = LocalTodoColors.current
    // 新建标签的弹层挂在编辑器里：建完立刻勾上这条任务，不必先跑去「标签管理」再回来。
    var creatingTag by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.task_list_label),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(6.dp))
        ListPicker(
            lists = lists,
            selectedListId = viewModel.listId,
            onSelect = viewModel::onListChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.task_tags_label),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(6.dp))

        // 「新建标签」放在标签列表最上面，一眼能看到、也少滚一屏。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { creatingTag = true }
                .testTag(EditorNewTagTag)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.tag_new),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent
            )
        }

        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        } else {
            tags.forEach { tag ->
                val selected = tag.id in viewModel.selectedTagIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onToggleTag(tag.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagChip(name = tag.name, color = tag.color)
                    Spacer(Modifier.weight(1f))
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { viewModel.onToggleTag(tag.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accent,
                            checkmarkColor = Color.White
                        )
                    )
                }
            }
        }
    }

    if (creatingTag) {
        TagEditDialog(
            title = stringResource(R.string.tag_new),
            initialName = "",
            initialColor = DefaultTagColor.toArgb(),
            onConfirm = { name, color ->
                viewModel.createTag(name, color)
                creatingTag = false
            },
            onDismiss = { creatingTag = false }
        )
    }
}

/**
 * 备注那一块：一块填色的输入区，**不带描边**。
 *
 * 上一版给它画了 12dp 圆角 + 1dp 描边，还把同一套形状复制到标题卡里那条「添加备注」上，
 * 于是弹层里出现了「卡片里套一个描边框」的第二层边界。现在备注只有这一处入口（属性行上
 * 的 📝），展开后就是一块淡底输入区，形状只靠圆角区分。
 */
@Composable
private fun NotesGroup(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current
    TextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        placeholder = {
            Text(
                text = stringResource(R.string.editor_add_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.dateMuted
            )
        },
        minLines = 3,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.groupHeader.copy(alpha = 0.5f),
            unfocusedContainerColor = colors.groupHeader.copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.accent
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
}

/** 「明天 15:00」 / 「明天」: the text of the 日期 pill on the attribute row. */
@Composable
private fun dateAndTimeText(date: LocalDate, time: LocalTime?, today: LocalDate): String {
    val day = calendarDateText(date, today)
    return if (time == null) day else "$day ${timeText(time)}"
}

/** The time as `HH:mm`; the picker itself works on hour and minute. */
private fun timeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
