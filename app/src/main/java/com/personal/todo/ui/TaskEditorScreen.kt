package com.personal.todo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.ReminderKind
import com.personal.todo.ui.theme.DefaultTagColor
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** The tag the smoke tests use to type a title into the editor. */
internal const val EditorTitleTag = "editor-title"

/** The tag the smoke tests use to reach the 时间安排 rows of the editor. */
internal const val EditorToolbarTag = "editor-toolbar"

/** The tag the smoke tests use to reach the「新建标签」入口 of the editor's 标签 group. */
internal const val EditorNewTagTag = "editor-new-tag"

/** 新建弹层里「子任务」那一行的 testTag：点它展开输入框。 */
internal const val EditorSubtaskTag = "editor-subtask"

/** 子任务输入框的 testTag：冒烟测试靠它往里敲字。 */
internal const val EditorSubtaskInputTag = "editor-subtask-input"

/** 底部那颗主按钮的 testTag（新建＝「添加任务」，编辑＝「保存」）。 */
internal const val EditorSaveTag = "editor-save"

/**
 * 新建任务的底部弹层，按 `docs/designs/task-entry` 的**「一页填写」**方案重做
 * （2026-11-15）。
 *
 * 与上一版的区别只有一件事：**所有时间和提醒都在这一页里直接改完**。
 * 上一版的「日期」是一枚属性胶囊，点开还是那个日期弹层（月历 + 时间段两个页签），
 * 用户得进两层才看得到「几点提醒」。现在计划日期 / 时间 / 提醒 / 重复是四行普通的
 * 值行，各自点一下就地改，改完上面那行摘要立刻写出算出来的提醒时刻。
 *
 * 收进「更多选项」的是几件不常改的事：清单、优先级、截止日期与时间、标签、子任务。
 * 一进来它们全是收起的，所以第一屏只有标题、备注、快捷日期、四行时间安排。
 *
 * 弹层本身仍是全高底部弹层（上滑即满屏），没有改成推入的路由：新建是一次性的动作，
 * 关掉就该回到列表，而 `editor-sheet` 这个 view model 的「每次打开先 [TaskEditorViewModel.startNewTask]」
 * 逻辑就挂在 MainScreen 上——换路由要连那套重置逻辑一起搬，收益只有顶部两个圆角。
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
        EntryChrome(
            viewModel = viewModel,
            headerTitle = stringResource(R.string.editor_title_new),
            saveLabel = stringResource(R.string.editor_title_new),
            hint = stringResource(R.string.editor_entry_hint),
            saveEnabled = !saving,
            onCancel = onDismiss,
            onSave = viewModel::save,
            modifier = Modifier.fillMaxHeight(),
            showHandle = true
        ) {
            TaskEntryBody(viewModel = viewModel)
        }
    }
}

/**
 * 编辑任务（推入的一页）：和新建共用同一份「一页填写」，只有三处不同——顶部写「编辑任务」、
 * 底部按钮写「保存」、顶栏左边是返回而不是取消。
 */
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    // 读库没回来、或者上一次保存还没回来：按钮都不收点击。
    val saveEnabled = !loading && !saving
    val colors = LocalTodoColors.current

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(containerColor = colors.card) { innerPadding ->
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
                    color = colors.dateMuted
                )
            }
            return@Scaffold
        }
        EntryChrome(
            viewModel = viewModel,
            headerTitle = stringResource(R.string.editor_title_edit),
            saveLabel = stringResource(R.string.action_save),
            hint = stringResource(R.string.editor_entry_hint),
            saveEnabled = saveEnabled,
            onCancel = onBack,
            onSave = viewModel::save,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TaskEntryBody(viewModel = viewModel)
        }
    }
}

// ------------------------------------------------------------------ 外壳

/**
 * 一页填写的骨架：把手（弹层才有）、顶栏、可滚的内容区、底部主按钮。
 *
 * **只有这一处**画边框：内容区与底栏之间那条 1dp 的分隔线。原来那版在弹层里套卡片、
 * 卡片里再套描边输入框，一层套一层；这一版内容区是平的，靠行距和细分隔线分组。
 */
@Composable
private fun EntryChrome(
    viewModel: TaskEditorViewModel,
    headerTitle: String,
    saveLabel: String,
    hint: String,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val listName = lists.firstOrNull { it.id == viewModel.listId }?.name.orEmpty()
    val colors = LocalTodoColors.current

    Column(modifier = modifier.fillMaxWidth().background(colors.card)) {
        if (showHandle) {
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
        }

        EntryHeader(title = headerTitle, listName = listName, onCancel = onCancel)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp, bottom = 10.dp)
        ) {
            content()
        }

        EntryFooter(
            label = saveLabel,
            enabled = saveEnabled,
            hint = hint,
            onSave = onSave
        )
    }
}

/** 顶栏：左「取消」、中间标题、右边只读地写着这条任务落在哪条清单（和稿子一致）。 */
@Composable
private fun EntryHeader(title: String, listName: String, onCancel: () -> Unit) {
    val colors = LocalTodoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 22.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.dateMuted
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            text = listName,
            style = MaterialTheme.typography.labelMedium,
            color = colors.dateMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp)
        )
    }
}

/** 底部：一颗整行铺满的主按钮 + 一行说明（保存前就能确认提醒时刻 / 保存中的状态）。 */
@Composable
private fun EntryFooter(label: String, enabled: Boolean, hint: String, onSave: () -> Unit) {
    val colors = LocalTodoColors.current

    Column(modifier = Modifier.fillMaxWidth().background(colors.card)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.dateMuted.copy(alpha = 0.15f))
        )
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp)) {
            Button(
                onClick = onSave,
                enabled = enabled,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(EditorSaveTag)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ------------------------------------------------------------------ 内容

/** 打开「计划日期 / 截止日期」哪一行的日期选择器。 */
private enum class EntryDateField { PLANNED, DUE }

/** 打开「时间 / 截止时间」哪一行的时刻选择器。 */
private enum class EntryTimeField { PLANNED, DUE }

/**
 * 一页填写的全部内容。
 *
 * 顺序照稿子：标题 → 备注 → 快捷日期（今天 / 明天 / 选日期）→「时间安排」四行 → 提醒摘要
 * →「更多选项」→（校验与保存失败的提示）。
 */
@Composable
private fun TaskEntryBody(viewModel: TaskEditorViewModel) {
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val colors = LocalTodoColors.current

    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var dateField by remember { mutableStateOf<EntryDateField?>(null) }
    var timeField by remember { mutableStateOf<EntryTimeField?>(null) }
    var reminderOpen by remember { mutableStateOf(false) }
    var customReminderOpen by remember { mutableStateOf(false) }
    var repeatOpen by remember { mutableStateOf(false) }

    // 通知权限只在用户**真的设了提醒**这一刻才要（照日期弹层原来的做法）：一进来就弹是骚扰。
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 拒绝只是「到点不弹」，任务照常保存 */ }
    val askNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TitleField(viewModel = viewModel)
        NotesField(viewModel = viewModel)

        Spacer(Modifier.height(16.dp))
        QuickDateRow(
            today = viewModel.today,
            plannedDate = viewModel.plannedDate,
            onPick = { viewModel.onPlannedDateChange(it) },
            onPickExact = { dateField = EntryDateField.PLANNED }
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.editor_section_schedule))

        // 四行时间安排。这一块是这一版的全部重点：不进任何二级页面就能改完。
        Column(modifier = Modifier.testTag(EditorToolbarTag)) {
            ScheduleValueRow(
                emoji = "📅",
                label = stringResource(R.string.task_planned_date_label),
                value = viewModel.plannedDate?.let { entryDateText(it, viewModel.today) }
                    ?: stringResource(R.string.editor_value_none),
                onClick = { dateField = EntryDateField.PLANNED }
            )
            ScheduleValueRow(
                emoji = "🕒",
                label = stringResource(R.string.editor_time_label),
                value = viewModel.plannedTime?.let { timeText(it) }
                    ?: stringResource(R.string.editor_value_none),
                onClick = { timeField = EntryTimeField.PLANNED }
            )
            ScheduleValueRow(
                emoji = "🔔",
                label = stringResource(R.string.editor_reminder_label),
                value = if (!viewModel.canSetReminder) {
                    // 没有日期就没有锚点，提醒算不出时刻——先把话说明白，别让这一行点了没反应。
                    stringResource(R.string.reminder_need_date_short)
                } else {
                    reminderValueText(
                        kind = viewModel.reminderKind,
                        customAt = viewModel.customReminderAt,
                        plannedDate = viewModel.plannedDate,
                        plannedTime = viewModel.plannedTime,
                        dueDate = viewModel.dueDate,
                        dueTime = viewModel.dueTime
                    )
                },
                muted = !viewModel.canSetReminder,
                onClick = { if (viewModel.canSetReminder) reminderOpen = true }
            )
            ScheduleValueRow(
                emoji = "🔁",
                label = stringResource(R.string.editor_repeat_label),
                value = repeatText(viewModel.repeatRule),
                onClick = { repeatOpen = true },
                divider = false
            )
        }

        // 提醒摘要：算出来的**具体时刻**写在强调色里，改日期 / 时间时它跟着变。
        if (viewModel.reminderKind != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "🔔", fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(
                        R.string.reminder_editor_summary,
                        reminderValueText(
                            kind = viewModel.reminderKind,
                            customAt = viewModel.customReminderAt,
                            plannedDate = viewModel.plannedDate,
                            plannedTime = viewModel.plannedTime,
                            dueDate = viewModel.dueDate,
                            dueTime = viewModel.dueTime
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent
                )
            }
            Spacer(Modifier.height(8.dp))
            ReminderPermissionHint()
        }

        MoreOptionsToggle(open = moreOpen, onToggle = { moreOpen = !moreOpen })

        if (moreOpen) {
            Column(modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)) {
                SubLabel(stringResource(R.string.task_list_label))
                ListPicker(
                    lists = lists,
                    selectedListId = viewModel.listId,
                    onSelect = viewModel::onListChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                SubLabel(stringResource(R.string.task_priority_label))
                PrioritySection(viewModel = viewModel)

                Spacer(Modifier.height(10.dp))
                // 截止日期 / 截止时间：稿子把它们收在「更多选项」里，但一个字段都没少。
                ScheduleValueRow(
                    emoji = "⏳",
                    label = stringResource(R.string.task_due_date_label),
                    value = viewModel.dueDate?.let { entryDateText(it, viewModel.today) }
                        ?: stringResource(R.string.editor_value_none),
                    onClick = { dateField = EntryDateField.DUE },
                    divider = false
                )
                if (viewModel.dueDate != null) {
                    ScheduleValueRow(
                        emoji = "🕒",
                        label = stringResource(R.string.task_due_time_label),
                        value = viewModel.dueTime?.let { timeText(it) }
                            ?: stringResource(R.string.editor_value_none),
                        onClick = { timeField = EntryTimeField.DUE },
                        divider = false
                    )
                }

                Spacer(Modifier.height(18.dp))
                TagSection(viewModel = viewModel, tags = allTags)

                if (viewModel.isNewTask) {
                    Spacer(Modifier.height(14.dp))
                    SubtaskSection(viewModel = viewModel)
                }
            }
        }

        if (viewModel.titleHasError) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.task_title_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (viewModel.saveFailed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.editor_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateOverdue
            )
        }
    }

    dateField?.let { field ->
        EntryDateDialog(field = field, viewModel = viewModel, onDismiss = { dateField = null })
    }
    timeField?.let { field ->
        EntryTimeDialog(field = field, viewModel = viewModel, onDismiss = { timeField = null })
    }
    if (reminderOpen) {
        ReminderPickerDialog(
            current = viewModel.reminderKind,
            onPick = { kind ->
                reminderOpen = false
                when (kind) {
                    null -> viewModel.onReminderChange(null)

                    ReminderKind.CUSTOM -> {
                        // 自定义要挑一个具体时刻：先按现在的锚点起个头，再让用户在弹窗里改。
                        askNotificationPermission()
                        customReminderOpen = true
                    }

                    else -> {
                        askNotificationPermission()
                        viewModel.onReminderChange(kind)
                    }
                }
            },
            onDismiss = { reminderOpen = false }
        )
    }
    if (customReminderOpen) {
        EntryCustomReminderDialog(
            viewModel = viewModel,
            onDismiss = { customReminderOpen = false }
        )
    }
    if (repeatOpen) {
        RepeatRuleDialog(
            current = viewModel.repeatRule,
            onPick = {
                viewModel.onRepeatChange(it)
                repeatOpen = false
            },
            onDismiss = { repeatOpen = false }
        )
    }
}

// ---------------------------------------------------------------- 标题与备注

/**
 * 标题：一个**没有框**的输入框，字号 22sp 加粗——它就是这一页的名字，不该再被一个框圈起来。
 */
@Composable
private fun TitleField(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    BasicTextField(
        value = viewModel.title,
        onValueChange = viewModel::onTitleChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp, max = 120.dp)
            .padding(top = 6.dp)
            .testTag(EditorTitleTag),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (viewModel.title.isEmpty()) {
                    Text(
                        text = stringResource(R.string.task_title_placeholder),
                        style = textStyle,
                        color = colors.dateMuted.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

/** 备注：贴着标题下面的一行淡字，写到哪儿就长到哪儿（没有底色、没有描边）。 */
@Composable
private fun NotesField(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 21.sp
    )

    BasicTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .padding(top = 2.dp),
        decorationBox = { innerTextField ->
            Box {
                if (viewModel.notes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_add_notes),
                        style = textStyle,
                        color = colors.dateMuted.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

// ---------------------------------------------------------------- 快捷日期

/**
 * 快捷日期：今天 / 明天 / 选日期。
 *
 * 和上一版的区别是这三枚有了形状（细描边 + 圆角 10dp），选中的那枚用淡强调色底——
 * 稿子上它们就是三颗小按钮，而不再是三个字。
 */
@Composable
private fun QuickDateRow(
    today: LocalDate,
    plannedDate: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onPickExact: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EntryChip(
            label = stringResource(R.string.date_today),
            selected = plannedDate == today,
            onClick = { onPick(today) }
        )
        EntryChip(
            label = stringResource(R.string.date_tomorrow),
            selected = plannedDate == today.plusDays(1),
            onClick = { onPick(today.plusDays(1)) }
        )
        EntryChip(
            label = stringResource(R.string.editor_quick_pick_date),
            selected = false,
            onClick = onPickExact
        )
    }
}

/** 快捷日期上的一枚：选中＝淡强调色底 + 强调色字；没选中＝细描边 + 灰字。 */
@Composable
private fun EntryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(colors.accent.copy(alpha = 0.14f))
                } else {
                    Modifier.border(1.dp, colors.dateMuted.copy(alpha = 0.28f), shape)
                }
            )
            .clickable(onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) colors.accent else colors.dateMuted,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------- 时间安排的行

/** 「时间安排」这种小标题：12sp、灰、字距略开。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/** 「更多选项」里的分组名（清单 / 优先级 / 标签）。 */
@Composable
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/**
 * 一行「图标 + 名字 + 当前值」。当前值画成一块淡底的圆角小格（稿子上的 input / select），
 * 点整行就是改它——不需要先点进一个二级页面。
 */
@Composable
private fun ScheduleValueRow(
    emoji: String,
    label: String,
    value: String,
    onClick: () -> Unit,
    muted: Boolean = false,
    divider: Boolean = true
) {
    val colors = LocalTodoColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !muted, onClick = onClick)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 13.sp, modifier = Modifier.width(26.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 178.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.groupHeader.copy(alpha = 0.75f))
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (muted) colors.dateMuted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dateMuted.copy(alpha = 0.12f))
            )
        }
    }
}

/** 「更多选项」的开合行：左边一行小字，右边一个 ＋ / −。 */
@Composable
private fun MoreOptionsToggle(open: Boolean, onToggle: () -> Unit) {
    val colors = LocalTodoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🎛️", fontSize = 12.sp)
        Spacer(Modifier.width(9.dp))
        Text(
            text = stringResource(R.string.editor_more_options),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.dateMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (open) "−" else "＋",
            fontSize = 18.sp,
            color = colors.dateMuted
        )
    }
}

// ---------------------------------------------------------------- 更多选项里的三块

/** 优先级就是四象限：两个开关 + 一行实时说明落进哪一格。 */
@Composable
private fun PrioritySection(viewModel: TaskEditorViewModel) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
 * 标签：先给一个「新建标签」的入口（建完立刻勾在这条任务上），再把全部标签列出来逐条勾。
 *
 * 标签是多值属性，和上面的清单（单值）分工不同：清单是任务住哪，标签是任务身上带什么标记。
 */
@Composable
private fun TagSection(viewModel: TaskEditorViewModel, tags: List<Tag>) {
    val colors = LocalTodoColors.current
    var creatingTag by remember { mutableStateOf(false) }

    SubLabel(stringResource(R.string.task_tags_label))

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
                    .padding(vertical = 2.dp),
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
 * 新建任务时的子任务草稿区：一行「子任务 + 添加」，点开才出现输入框，加过的列在下面可逐条移除。
 *
 * 草稿真正的落库在保存那一步（[TaskEditorViewModel.save] → `TaskRepository.saveTask` 的同一个
 * 事务）：父任务这一刻还没有 id，子任务挂不上去。
 */
@Composable
private fun SubtaskSection(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val count = viewModel.subtaskDrafts.size
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(adding) {
        if (adding) focusRequester.requestFocus()
    }

    val submit = {
        viewModel.onAddSubtask(draft)
        draft = ""
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { adding = !adding }
                .testTag(EditorSubtaskTag)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🧩", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (count == 0) {
                    stringResource(R.string.task_subtasks_title)
                } else {
                    stringResource(R.string.task_subtask_count, count)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (count > 0) FontWeight.SemiBold else null,
                color = if (count > 0) colors.accent else colors.dateMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.action_add),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent
            )
        }

        if (adding) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.groupHeader.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(EditorSubtaskInputTag),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.task_add_subtask_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.dateMuted
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                TextButton(onClick = submit, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.action_add))
                }
            }
        }

        viewModel.subtaskDrafts.forEachIndexed { index, title ->
            SubtaskDraftRow(title = title, onRemove = { viewModel.onRemoveSubtask(index) })
        }

        if (count > 0) {
            Text(
                text = stringResource(R.string.editor_subtask_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

/** 草稿区里的一条：一个小圆点 + 标题 + 行尾的移除键（还没落库，删掉就是纯表单动作）。 */
@Composable
private fun SubtaskDraftRow(title: String, onRemove: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.editor_subtask_remove),
                tint = colors.dateMuted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- 三个选择器

/** 计划日期 / 截止日期：就地弹一个月历，点哪一天就是哪一天，不用再点一次「确认」。 */
@Composable
private fun EntryDateDialog(
    field: EntryDateField,
    viewModel: TaskEditorViewModel,
    onDismiss: () -> Unit
) {
    val colors = LocalTodoColors.current
    val current = when (field) {
        EntryDateField.PLANNED -> viewModel.plannedDate
        EntryDateField.DUE -> viewModel.dueDate
    }
    var month by remember(field) { mutableStateOf(YearMonth.from(current ?: viewModel.today)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (field == EntryDateField.PLANNED) {
                        R.string.task_planned_date_label
                    } else {
                        R.string.task_due_date_label
                    }
                )
            )
        },
        text = {
            MonthCalendar(
                month = month,
                selected = current,
                today = viewModel.today,
                onSelect = {
                    when (field) {
                        EntryDateField.PLANNED -> viewModel.onPlannedDateChange(it)
                        EntryDateField.DUE -> viewModel.onDueDateChange(it)
                    }
                    onDismiss()
                },
                onStepMonth = { month = month.plusMonths(it) }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    when (field) {
                        EntryDateField.PLANNED -> viewModel.onPlannedDateChange(null)
                        EntryDateField.DUE -> viewModel.onDueDateChange(null)
                    }
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.action_clear), color = colors.dateOverdue)
            }
        }
    )
}

/**
 * 时间 / 截止时间：一个时刻要挂在一天上，所以先没有日期就先落到今天（和稿子上
 * 「时间」那一格的行为一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimeDialog(
    field: EntryTimeField,
    viewModel: TaskEditorViewModel,
    onDismiss: () -> Unit
) {
    val start = when (field) {
        EntryTimeField.PLANNED -> viewModel.plannedTime
        EntryTimeField.DUE -> viewModel.dueTime
    } ?: DefaultSheetTime
    val state = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (field == EntryTimeField.PLANNED) {
                        R.string.editor_time_label
                    } else {
                        R.string.task_due_time_label
                    }
                )
            )
        },
        text = { SheetTimePicker(state) },
        confirmButton = {
            TextButton(
                onClick = {
                    val time = LocalTime.of(state.hour, state.minute)
                    when (field) {
                        EntryTimeField.PLANNED -> {
                            if (viewModel.plannedDate == null) {
                                viewModel.onPlannedDateChange(viewModel.today)
                            }
                            viewModel.onPlannedTimeChange(time)
                        }

                        EntryTimeField.DUE -> {
                            if (viewModel.dueDate == null) {
                                viewModel.onDueDateChange(viewModel.plannedDate ?: viewModel.today)
                            }
                            viewModel.onDueTimeChange(time)
                        }
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * 自定义提醒：挑一个绝对时刻（日历 + 钟点）。
 *
 * 其余几种提醒只存「相对哪种锚点算」，日期一改由落库时重算；自定义是唯一需要另存绝对值的
 * 类型，所以要单独问一次日期和时刻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCustomReminderDialog(viewModel: TaskEditorViewModel, onDismiss: () -> Unit) {
    val colors = LocalTodoColors.current
    var customDate by remember {
        mutableStateOf(viewModel.plannedDate ?: viewModel.dueDate ?: viewModel.today)
    }
    var customTime by remember { mutableStateOf(viewModel.plannedTime ?: DefaultSheetTime) }
    var month by remember { mutableStateOf(YearMonth.from(customDate)) }
    var timeOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_custom_reminder_title)) },
        text = {
            Column {
                if (viewModel.repeatRule != null) {
                    Text(
                        text = stringResource(R.string.reminder_custom_repeat_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted
                    )
                    Spacer(Modifier.height(12.dp))
                }
                MonthCalendar(
                    month = month,
                    selected = customDate,
                    today = viewModel.today,
                    onSelect = { customDate = it },
                    onStepMonth = { month = month.plusMonths(it) }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { timeOpen = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editor_time_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeText(customTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.dateMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val at = customDate.atTime(customTime)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    viewModel.onReminderChange(ReminderKind.CUSTOM, at)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (timeOpen) {
        val state = rememberTimePickerState(
            initialHour = customTime.hour,
            initialMinute = customTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text(stringResource(R.string.editor_time_label)) },
            text = { SheetTimePicker(state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        customTime = LocalTime.of(state.hour, state.minute)
                        timeOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { timeOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- 小工具

/**
 * 计划日期那一行右边显示什么：「今天 · 11月14日」。
 *
 * 光写「今天」时用户看不出到底是几号；光写「11月14日」时又要自己去对星期。近三天用
 * [calendarDateText] 的相对说法，后面补上具体月日；更远的日子它本身就是「11月20日」，
 * 不再重复一遍。
 */
@Composable
private fun entryDateText(date: LocalDate, today: LocalDate): String {
    val relative = calendarDateText(date, today)
    val absolute = stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)
    return if (relative == absolute) relative else "$relative · $absolute"
}
