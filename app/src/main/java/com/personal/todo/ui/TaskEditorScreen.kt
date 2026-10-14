package com.personal.todo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Create / edit screen for title, notes, priority, list and dates.
 *
 * The title is mandatory: saving with an empty title shows an inline error
 * instead of creating a task without a title.
 *
 * The date fields follow spec section 4.1: the planned date and the due date are
 * independent of each other, and a due time can only exist on a day - so the
 * time field stays disabled until a due date is set, and clearing the due date
 * clears the time with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    var plannedPickerOpen by remember { mutableStateOf(false) }
    var duePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNewTask) R.string.editor_title_new else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.task_title_label)) },
                isError = viewModel.titleHasError,
                supportingText = if (viewModel.titleHasError) {
                    { Text(stringResource(R.string.task_title_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.task_notes_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.task_priority_label))
            Spacer(Modifier.height(4.dp))
            PriorityChips(selected = viewModel.priority, onSelect = viewModel::onPriorityChange)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.task_list_label))
            Spacer(Modifier.height(4.dp))
            ListPicker(
                lists = lists,
                selectedListId = viewModel.listId,
                onSelect = viewModel::onListChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.task_date_label))
            Spacer(Modifier.height(4.dp))

            DateValueRow(
                label = stringResource(R.string.task_planned_date_label),
                valueText = viewModel.plannedDate?.let { calendarDateText(it, viewModel.today) }
                    ?: stringResource(R.string.date_not_set),
                isSet = viewModel.plannedDate != null,
                pickEnabled = true,
                onPick = { plannedPickerOpen = true },
                onClear = { viewModel.onPlannedDateChange(null) }
            )
            DateValueRow(
                label = stringResource(R.string.task_due_date_label),
                valueText = viewModel.dueDate?.let { calendarDateText(it, viewModel.today) }
                    ?: stringResource(R.string.date_not_set),
                isSet = viewModel.dueDate != null,
                pickEnabled = true,
                onPick = { duePickerOpen = true },
                onClear = { viewModel.onDueDateChange(null) }
            )
            DateValueRow(
                label = stringResource(R.string.task_due_time_label),
                valueText = viewModel.dueTime?.let { timeText(it) }
                    ?: stringResource(
                        if (viewModel.dueDate == null) R.string.due_time_needs_date else R.string.date_not_set
                    ),
                isSet = viewModel.dueTime != null,
                pickEnabled = viewModel.dueDate != null,
                onPick = { timePickerOpen = true },
                onClear = { viewModel.onDueTimeChange(null) }
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (plannedPickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(viewModel.plannedDate ?: viewModel.today)
        )
        DatePickerDialog(
            onDismissRequest = { plannedPickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { viewModel.onPlannedDateChange(utcMillisToLocalDate(it)) }
                        plannedPickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { plannedPickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (duePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(viewModel.dueDate ?: viewModel.today)
        )
        DatePickerDialog(
            onDismissRequest = { duePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { viewModel.onDueDateChange(utcMillisToLocalDate(it)) }
                        duePickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { duePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (timePickerOpen) {
        val start = viewModel.dueTime ?: DefaultDueTime
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerOpen = false },
            title = { Text(stringResource(R.string.dialog_time_picker_title)) },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDueTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                        timePickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** The hour the time picker starts on when the task has no due time yet. */
private val DefaultDueTime: LocalTime = LocalTime.of(9, 0)

/** One row of the date section: label, current value, pick and clear. */
@Composable
private fun DateValueRow(
    label: String,
    valueText: String,
    isSet: Boolean,
    pickEnabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTodoColors.current.dateMuted
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (pickEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                LocalTodoColors.current.dateMuted
            }
        )
        TextButton(onClick = onPick, enabled = pickEnabled) {
            Text(stringResource(R.string.action_pick))
        }
        if (isSet && pickEnabled) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear)
                )
            }
        } else {
            Spacer(Modifier.width(IconSlot))
        }
    }
}

/** Keeps the rows aligned when the clear button is not shown. */
private val IconSlot = 40.dp

/** The due time as `HH:mm`; the picker itself works on hour and minute. */
private fun timeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** The material date picker works in UTC midnight millis, so the date never shifts. */
private fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
