package com.personal.todo.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.todo.R
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.DevSettingsStore
import com.personal.todo.data.LauncherIcon
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.ThemeChoice
import com.personal.todo.notification.ReminderScheduler
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
    /** 开发者选项那一组入口是否已经解锁（连点「开发者声明」五下之后为 true）。 */
    developerUnlocked: Boolean = false,
    onDeveloperModeUnlocked: () -> Unit = {},
    onOpenDeveloperSettings: () -> Unit = {},
    onBack: () -> Unit
) {
    val colors = LocalTodoColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 提醒的授权状态不是「读一次就定」的：用户可能在这里跳去系统设置，回来时权限变了，
    // 也可能在别处把通知关掉。所以每次回到前台都重读一遍。
    val context = LocalContext.current
    val reminderStore = remember(context) { ReminderSettingsStore(context) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactAlarmAllowed by remember {
        mutableStateOf(ReminderScheduler(context).canScheduleExact())
    }
    var exactEnabled by remember {
        mutableStateOf(reminderStore.readExactAlarm() && exactAlarmAllowed)
    }
    // 用户点了开关、被踢去系统设置授权：回来时若真给了，就替他把开关落下。
    var awaitingExact by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                val allowed = ReminderScheduler(context).canScheduleExact()
                exactAlarmAllowed = allowed
                if (awaitingExact && allowed && !reminderStore.readExactAlarm()) {
                    reminderStore.writeExactAlarm(true)
                }
                if (awaitingExact && allowed) awaitingExact = false
                // 系统收回了权限，开关就得自己回到关：显示成开、实际却不准，是骗人的。
                exactEnabled = reminderStore.readExactAlarm() && allowed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportMessage = stringResource(R.string.settings_export_soon)
    // 距解锁还差几下；点够 [DevSettingsStore.TapsToUnlock] 下就解锁并清零。
    var devTaps by remember { mutableIntStateOf(0) }
    val devUnlockedMessage = stringResource(R.string.dev_unlocked_message)

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
                // 这一页有 5 个分组标题 + 17 行选项 + 4 条入口，加起来比任何手机屏都长：
                // 不滚动的话底部那几条**永远够不到**（审查报告：全项目唯一的功能阻断）。
                .verticalScroll(rememberScrollState())
                .testTag(SettingsScreenTag)
        ) {
            SettingsGroupLabel(stringResource(R.string.settings_section_theme))
            SettingsCard {
                ThemeChoice.entries.forEachIndexed { index, choice ->
                    if (index > 0) SettingsDivider()
                    ChoiceRow(
                        label = stringResource(themeLabelRes(choice)),
                        selected = choice == themeChoice,
                        onClick = { onThemeChoiceChange(choice) }
                    )
                }
            }

            SettingsGroupLabel(stringResource(R.string.settings_section_color))
            SettingsCard {
                ColorStyle.entries.forEachIndexed { index, style ->
                    if (index > 0) SettingsDivider()
                    ChoiceRow(
                        label = stringResource(colorStyleLabelRes(style)),
                        selected = style == colorStyle,
                        onClick = { onColorStyleChange(style) }
                    )
                }
            }

            SettingsGroupLabel(stringResource(R.string.settings_section_background))
            SettingsCard {
                BackgroundStyle.entries.forEachIndexed { index, style ->
                    if (index > 0) SettingsDivider()
                    ChoiceRow(
                        label = stringResource(backgroundStyleLabelRes(style)),
                        selected = style == backgroundStyle,
                        onClick = { onBackgroundStyleChange(style) }
                    )
                }
            }

            SettingsGroupLabel(stringResource(R.string.settings_section_icon))
            SettingsCard {
                LauncherIcon.entries.forEachIndexed { index, icon ->
                    if (index > 0) SettingsDivider()
                    ChoiceRow(
                        label = stringResource(launcherIconLabelRes(icon)),
                        selected = icon == launcherIcon,
                        onClick = { onLauncherIconChange(icon) }
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_icon_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )

            // 提醒：权限状态 + 准点开关。两行都不是「设一次就忘」的东西——通知权限可能被
            // 系统收回，准点开关要跳系统设置去授权，回来这里必须能看出到底成没成。
            SettingsGroupLabel(stringResource(R.string.settings_section_reminder))
            SettingsCard {
                SettingsEntry(
                    icon = "🔔",
                    label = stringResource(R.string.settings_reminder_permission),
                    trailing = if (notificationsEnabled) {
                        stringResource(R.string.settings_reminder_permission_on)
                    } else {
                        stringResource(R.string.settings_reminder_permission_off)
                    },
                    onClick = {
                        // 直接落到本应用的通知设置页，而不是让用户自己去系统里翻。
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_reminder_exact),
                    checked = exactEnabled,
                    onCheckedChange = { wanted ->
                        val store = ReminderSettingsStore(context)
                        when {
                            !wanted -> {
                                store.writeExactAlarm(false)
                                exactEnabled = false
                                scope.launch { ReminderScheduler(context).rescheduleAll() }
                            }

                            exactAlarmAllowed -> {
                                store.writeExactAlarm(true)
                                exactEnabled = true
                                scope.launch { ReminderScheduler(context).rescheduleAll() }
                            }

                            // 系统还没给「闹钟和提醒」权限：去要，回来后 [OnResume] 再判一次。
                            else -> {
                                awaitingExact = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        }
                    }
                )
            }
            Text(
                text = stringResource(R.string.settings_reminder_exact_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )
            // 通知没开时单独说一句：这时提醒到点不会弹，别让人以为设了就会响。
            if (!notificationsEnabled) {
                Text(
                    text = stringResource(R.string.settings_reminder_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateOverdue,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            }

            SettingsGroupLabel(stringResource(R.string.settings_section_more))
            SettingsCard {
                SettingsEntry(
                    icon = "⏱️",
                    label = stringResource(R.string.more_entry_focus),
                    onClick = onOpenFocus
                )
                SettingsDivider()
                SettingsEntry(
                    icon = "📊",
                    label = stringResource(R.string.more_entry_stats),
                    onClick = onOpenStats
                )
                SettingsDivider()
                SettingsEntry(
                    icon = "🏷️",
                    label = stringResource(R.string.more_entry_tags),
                    onClick = onOpenTags
                )
                SettingsDivider()
                SettingsEntry(
                    icon = "📤",
                    label = stringResource(R.string.settings_export),
                    trailing = stringResource(R.string.settings_export_soon_short),
                    onClick = { scope.launch { snackbarHostState.showSnackbar(exportMessage) } }
                )
            }

            // 开发者选项：解锁之后才出现在这里（入口本身在开发者选项页里还能再藏起来）。
            if (developerUnlocked) {
                SettingsGroupLabel(stringResource(R.string.dev_section_title))
                SettingsCard {
                    SettingsEntry(
                        icon = "🛠️",
                        label = stringResource(R.string.dev_entry_title),
                        onClick = onOpenDeveloperSettings
                    )
                }
            }

            // 可滚动列里不能再让子项抢剩余高度（weight 在无界高度下没有意义），
            // 所以这里用一段固定间距把「关于」推到底部。
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_about_local),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // 「开发者声明」：一个看上去只是说明文字的行，连点五下才解锁开发者选项。
            // 用户 2026-10-21 要的入口就藏在这里——正常用的人不会去连点一段声明文字，
            // 而调试的人知道该点哪儿；解锁之后还能从开发者选项页里再把它藏回去。
            Text(
                text = stringResource(R.string.settings_developer_statement),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.dateMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!developerUnlocked) {
                            devTaps++
                            if (devTaps >= DevSettingsStore.TapsToUnlock) {
                                devTaps = 0
                                onDeveloperModeUnlocked()
                                scope.launch { snackbarHostState.showSnackbar(devUnlockedMessage) }
                            }
                        }
                    }
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            )
            Text(
                text = stringResource(R.string.settings_developer_statement_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp)
            )
        }
    }
}

/** 设置页里一组选项的圆角卡片：与更多页、任务卡同一个视觉语言。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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

/** 设置卡片里两行之间的淡分隔线。 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = LocalTodoColors.current.dateMuted.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 20.dp)
    )
}

/** 卡片上方的分组小标题（贴着卡片外沿，不再像原来的裸行）。 */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 4.dp)
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
            fontWeight = if (selected) FontWeight.SemiBold else null,
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
private fun SettingsEntry(
    icon: String,
    label: String,
    trailing: String? = null,
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

/**
 * 带开关的一行。点整行也能切，不必非得戳中那个小滑块。
 *
 * 放在这里而不是复用 [ChoiceRow]：那一组是「几选一，选中打勾」，开关是「开 / 关」，
 * 语义不一样，视觉上也要让人一眼看出这是可以反复拨的。
 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
