package com.personal.todo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.ThemeChoice
import com.personal.todo.ui.theme.LocalTodoColors
import kotlinx.coroutines.launch

/** The tag the smoke tests use to reach the page. */
internal const val SettingsScreenTag = "settings-screen"

/**
 * 设置页 (pushed from 更多). It owns exactly one real setting today - 主题三选
 * (跟随系统 / 浅色 / 深色), stored locally and applied to the whole tree at once -
 * and gathers the entries that used to be scattered: 标签管理, 专注统计, 番茄钟 and
 * the data export placeholder.
 *
 * A row that only gathers information is a way in, not a dead button: every entry
 * here opens something, and 数据导出 says plainly that it is not built yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeChoice: ThemeChoice,
    onThemeChoiceChange: (ThemeChoice) -> Unit,
    colorStyle: ColorStyle,
    onColorStyleChange: (ColorStyle) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTags: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalTodoColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportMessage = stringResource(R.string.settings_export_soon)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(SettingsScreenTag)
        ) {
            SectionLabel(stringResource(R.string.settings_section_theme))
            ThemeChoice.entries.forEach { choice ->
                ChoiceRow(
                    label = stringResource(themeLabelRes(choice)),
                    selected = choice == themeChoice,
                    onClick = { onThemeChoiceChange(choice) }
                )
            }

            SectionLabel(stringResource(R.string.settings_section_color))
            ColorStyle.entries.forEach { style ->
                ChoiceRow(
                    label = stringResource(colorStyleLabelRes(style)),
                    selected = style == colorStyle,
                    onClick = { onColorStyleChange(style) }
                )
            }

            SectionLabel(stringResource(R.string.settings_section_more))
            SettingsEntry(
                label = stringResource(R.string.more_entry_focus),
                onClick = onOpenFocus
            )
            SettingsEntry(
                label = stringResource(R.string.more_entry_stats),
                onClick = onOpenStats
            )
            SettingsEntry(
                label = stringResource(R.string.more_entry_tags),
                onClick = onOpenTags
            )
            SettingsEntry(
                label = stringResource(R.string.settings_export),
                trailing = stringResource(R.string.settings_export_soon_short),
                onClick = { scope.launch { snackbarHostState.showSnackbar(exportMessage) } }
            )

            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.settings_about_local),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** 主题 / 色系的每一行都一样：名字在左，选中的那行右边打一个勾。 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accent
            )
        }
    }
}

@Composable
private fun SettingsEntry(label: String, trailing: String? = null, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.dateMuted
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.dateMuted
        )
    }
}

private fun themeLabelRes(choice: ThemeChoice): Int = when (choice) {
    ThemeChoice.SYSTEM -> R.string.settings_theme_system
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
}

private fun colorStyleLabelRes(style: ColorStyle): Int = when (style) {
    ColorStyle.CLASSIC -> R.string.settings_color_classic
    ColorStyle.MACARON -> R.string.settings_color_macaron
    ColorStyle.MORANDI -> R.string.settings_color_morandi
}
