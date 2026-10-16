package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.Tag
import com.personal.todo.ui.theme.DefaultTagColor
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.ui.theme.TagPalette
import com.personal.todo.viewmodel.TagViewModel

/** The tag the smoke tests use to reach the page. */
internal const val TagScreenTag = "tag-screen"

/**
 * 标签管理页 (pushed from 更多): every tag with its colour, and the three things a
 * tag goes through - a new one, a rename/recolour, a delete.
 *
 * A tag is an attribute a task carries, not a place a task lives: deleting a tag
 * here removes the mark from every task that had it, and never touches a task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(
    viewModel: TagViewModel,
    onBack: () -> Unit
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    // null = closed; a tag = editing it; the reserved "new" sentinel = creating.
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tag_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.tag_new)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tag_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(TagScreenTag),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tags, key = { it.id }) { tag ->
                TagRow(
                    tag = tag,
                    onEdit = { editing = tag },
                    onDelete = { viewModel.delete(tag.id) }
                )
            }
        }
    }

    if (creating) {
        TagEditDialog(
            title = stringResource(R.string.tag_new),
            initialName = "",
            initialColor = DefaultTagColor.toArgb(),
            onConfirm = { name, color ->
                viewModel.create(name, color)
                creating = false
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { tag ->
        TagEditDialog(
            title = stringResource(R.string.tag_edit),
            initialName = tag.name,
            initialColor = tag.color,
            onConfirm = { name, color ->
                viewModel.update(tag.id, name, color)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** One tag: its chip on the left, delete on the right; the whole row opens the editor. */
@Composable
private fun TagRow(tag: Tag, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TagChip(name = tag.name, color = tag.color)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.tag_delete),
                tint = colors.dateMuted
            )
        }
    }
}

/** 新建 / 编辑标签：一个名字格子加一排色块。 */
@Composable
private fun TagEditDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.tag_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalTodoColors.current.dateMuted
                )
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagPalette.forEach { entry ->
                        ColorDot(
                            color = entry,
                            selected = entry.toArgb() == color,
                            onClick = { color = entry.toArgb() }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
