package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.todo.R
import com.personal.todo.data.CrashLog
import com.personal.todo.ui.theme.LocalTodoColors

/** 开发者选项页的根测试钩子。 */
internal const val DevSettingsScreenTag = "dev-settings-screen"

/** 开发者选项里「启动时弹出崩溃日志」那个开关的测试钩子。 */
internal const val DevShowCrashSwitchTag = "dev-show-crash-switch"

/**
 * 开发者选项：只有设置页连点「开发者声明」五下之后才进得来的一页。
 *
 * 这里放的都是「正常用户不该看见」的东西——目前只有一项：上次的崩溃日志要不要在启动时
 * 弹出来（默认关，用户 2026-10-21 明确要求别直接显示）。最下面那个「隐藏这个入口」把
 * 设置页上那一组入口撤掉，等于把这一页重新藏起来；想再进来就回设置页连点五下。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
    showCrashOnLaunch: Boolean,
    onShowCrashOnLaunchChange: (Boolean) -> Unit,
    onOpenCrashLog: () -> Unit,
    onHideEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_title)) },
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
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .testTag(DevSettingsScreenTag)
        ) {
            // 一眼看出「这一页不是给用户用的」：淡红底 + 一行小字。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.dateOverdue.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🛠️", fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.dev_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateOverdue
                )
            }

            DevGroupLabel(stringResource(R.string.crash_log_title))
            DevCard {
                DevSwitchRow(
                    icon = "🐞",
                    label = stringResource(R.string.dev_show_crash),
                    hint = stringResource(R.string.dev_show_crash_hint),
                    checked = showCrashOnLaunch,
                    onCheckedChange = onShowCrashOnLaunchChange,
                    tag = DevShowCrashSwitchTag
                )
                DevDivider()
                DevEntryRow(
                    icon = "📄",
                    label = stringResource(R.string.dev_open_crash),
                    onClick = onOpenCrashLog
                )
                DevDivider()
                DevEntryRow(
                    icon = "🧹",
                    label = stringResource(R.string.crash_log_clear),
                    // 只清掉 SharedPreferences 里那一条崩溃文本，与任务、专注记录无关，
                    // 所以不必经过 view model。
                    onClick = { CrashLog.clear(context) }
                )
            }

            DevGroupLabel(stringResource(R.string.dev_section_title))
            DevCard {
                DevEntryRow(
                    icon = "🙈",
                    label = stringResource(R.string.dev_exit),
                    danger = true,
                    onClick = onHideEntry
                )
            }
            Text(
                text = stringResource(R.string.dev_exit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 24.dp)
            )
        }
    }
}

/** 开发者页里一组开关 / 入口的圆角卡片。 */
@Composable
private fun DevCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(
                elevation = 1.5.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.07f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card),
        content = content
    )
}

@Composable
private fun DevDivider() {
    HorizontalDivider(
        color = LocalTodoColors.current.dateMuted.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 58.dp)
    )
}

@Composable
private fun DevGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** 带说明文字的一个开关行：左边圆形图标，右边 Switch。 */
@Composable
private fun DevSwitchRow(
    icon: String,
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(entryIconTint(icon).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 15.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
        )
    }
}

/** 一行普通入口：图标 + 名字 + 右箭头。 */
@Composable
private fun DevEntryRow(
    icon: String,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(entryIconTint(icon).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 15.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) colors.dateOverdue else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.dateMuted
        )
    }
}
