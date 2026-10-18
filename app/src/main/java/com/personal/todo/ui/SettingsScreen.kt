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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.LauncherIcon
import com.personal.todo.data.ThemeChoice
import com.personal.todo.ui.theme.LocalTodoColors
import kotlinx.coroutines.launch

/** The tag the smoke tests use to reach the page. */
internal const val SettingsScreenTag = "settings-screen"

/**
 * 设置页 (pushed from 更多). 它管三件外观（主题三选、色系三选、背景五选）与一件真设置
 * ——桌面图标六选；再往下是把原来散落的入口收拢：标签管理、专注统计、番茄钟与数据导出
 * 的占位。
 *
 * A row that only gathers information is a way in, not a dead button: every entry
 * here opens something, and 数据导出 says plainly that it is not built yet.
 *
 * 图标这一组的写法与前两组一致（选了打勾），落盘之外还要调一次 [applyLauncherIcon]，
 * 由 MainActivity 那边负责，页面本身不碰 PackageManager。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeChoice: ThemeChoice,
    onThemeChoiceChange: (ThemeChoice) -> Unit,
    colorStyle: ColorStyle,
    onColorStyleChange: (ColorStyle) -> Unit,
    backgroundStyle: BackgroundStyle,
    onBackgroundStyleChange: (BackgroundStyle) -> Unit,
    launcherIcon: LauncherIcon,
    onLauncherIconChange: (LauncherIcon) -> Unit,
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

            SectionLabel(stringResource(R.string.settings_section_background))
            BackgroundStyle.entries.forEach { style ->
                ChoiceRow(
                    label = stringResource(backgroundStyleLabelRes(style)),
                    selected = style == backgroundStyle,
                    onClick = { onBackgroundStyleChange(style) }
                )
            }

            SectionLabel(stringResource(R.string.settings_section_icon))
            LauncherIcon.entries.forEach { icon ->
                ChoiceRow(
                    label = stringResource(launcherIconLabelRes(icon)),
                    selected = icon == launcherIcon,
                    onClick = { onLauncherIconChange(icon) }
                )
            }
            Text(
                text = stringResource(R.string.settings_icon_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )

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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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

private fun backgroundStyleLabelRes(style: BackgroundStyle): Int = when (style) {
    BackgroundStyle.PLAIN -> R.string.settings_background_plain
    BackgroundStyle.WARM -> R.string.settings_background_warm
    BackgroundStyle.MINT -> R.string.settings_background_mint
    BackgroundStyle.LILAC -> R.string.settings_background_lilac
    BackgroundStyle.DUSK -> R.string.settings_background_dusk
}

private fun launcherIconLabelRes(icon: LauncherIcon): Int = when (icon) {
    LauncherIcon.TOMATO -> R.string.settings_icon_tomato
    LauncherIcon.RING -> R.string.settings_icon_ring
    LauncherIcon.CLOCK -> R.string.settings_icon_clock
    LauncherIcon.QUAD -> R.string.settings_icon_quad
    LauncherIcon.LIME -> R.string.settings_icon_lime
    LauncherIcon.SUN -> R.string.settings_icon_sun
}
