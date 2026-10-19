package com.personal.todo

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.personal.todo.data.AppearanceSettingsStore
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ThemeChoice
import com.personal.todo.data.TodoContainer
import com.personal.todo.data.applyLauncherIcon
import com.personal.todo.ui.TodoApp
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.ui.theme.todoCanvasColor

/**
 * Single activity for the whole app.
 *
 * Phase 1 wires the task core: the repository is created once here and handed
 * to the Compose tree, which owns navigation, screens and view models.
 *
 * 外观（主题、色系、背景与桌面图标）也在这里读一次并交给 Compose 当状态：设置页改一次就
 * 立刻重画整棵树，同时落盘；桌面图标那项还多一步——去系统里换掉启用的 activity-alias。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TodoContainer.repository(applicationContext)
        val focusRepository = TodoContainer.focusRepository(applicationContext)
        val habitRepository = TodoContainer.habitRepository(applicationContext)
        val appearance = AppearanceSettingsStore(applicationContext)
        // 桌面图标是系统层面的东西（启用哪个 activity-alias），所以要一个 Context；
        // 用 applicationContext，别把 Activity 攥在闭包里。
        val context = applicationContext

        // 窗口底色必须和 App 自己的页面底色一致，不能交给系统主题。
        //
        // 原来只调 `enableEdgeToEdge()`：窗口背景由 `Theme.Material.Light/NoActionBar`
        // 决定，是系统主题的纯白 / 纯黑；而 App 里真正的页面底色是 `CanvasLight` /
        // `CanvasDark`（还可能被「背景风格」掺色）。只要有一帧 Compose 还没把整页画满
        // （切页、重排、第一帧），露出来的就是那块纯白 / 纯黑——也就是「切页会闪一下」。
        // 现在先按当前外观把窗口背景直接刷成页面底色，再让 Compose 画；之后主题 / 背景
        // 风格一变就跟着刷。这样即使真有一帧露底，露出来的也是同一块底色，看不出来。
        val initialTheme = appearance.readTheme()
        val initialBackground = appearance.readBackgroundStyle()
        val initialDark = initialTheme.isDark(isSystemDark = isDarkModeNow())
        applyWindowBackground(initialDark, initialBackground)
        applySystemBars(initialDark)
        setContent {
            var themeChoice by remember { mutableStateOf(initialTheme) }
            var colorStyle by remember { mutableStateOf(appearance.readColorStyle()) }
            var backgroundStyle by remember { mutableStateOf(initialBackground) }
            var launcherIcon by remember { mutableStateOf(appearance.readLauncherIcon()) }
            // 进 onCreate 时已经刷过一次系统栏了，首帧别原地再刷一次（那会自己闪一下）。
            var systemBarsDark by remember { mutableStateOf(initialDark) }

            val darkTheme = themeChoice.isDark(isSystemDark = isSystemInDarkTheme())
            val canvasColor = todoCanvasColor(darkTheme, backgroundStyle)

            LaunchedEffect(darkTheme, backgroundStyle) {
                applyWindowBackground(darkTheme, backgroundStyle)
            }
            LaunchedEffect(darkTheme) {
                if (systemBarsDark != darkTheme) {
                    systemBarsDark = darkTheme
                    applySystemBars(darkTheme)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasColor)
            ) {
                TodoPomodoroTheme(
                    themeChoice = themeChoice,
                    colorStyle = colorStyle,
                    backgroundStyle = backgroundStyle
                ) {
                    TodoApp(
                        repository = repository,
                        focusRepository = focusRepository,
                        habitRepository = habitRepository,
                        themeChoice = themeChoice,
                        onThemeChoiceChange = {
                            themeChoice = it
                            appearance.writeTheme(it)
                        },
                        colorStyle = colorStyle,
                        onColorStyleChange = {
                            colorStyle = it
                            appearance.writeColorStyle(it)
                        },
                        backgroundStyle = backgroundStyle,
                        onBackgroundStyleChange = {
                            backgroundStyle = it
                            appearance.writeBackgroundStyle(it)
                        },
                        launcherIcon = launcherIcon,
                        onLauncherIconChange = {
                            launcherIcon = it
                            appearance.writeLauncherIcon(it)
                            applyLauncherIcon(context, it)
                        }
                    )
                }
            }
        }
    }

    /** 窗口底＝App 页面底；背景风格一变只重刷这一层，不重复动系统栏。 */
    private fun applyWindowBackground(darkTheme: Boolean, backgroundStyle: BackgroundStyle) {
        window.setBackgroundDrawable(
            ColorDrawable(todoCanvasColor(darkTheme, backgroundStyle).toArgb())
        )
    }

    /**
     * 系统栏按 App 的主题选择（而不是系统主题）决定图标明暗。
     *
     * 原来 `enableEdgeToEdge()` 只看系统深色模式：App 选「深色」而系统是浅色时，
     * 状态栏 / 导航栏还是深色图标，压在深色页面上几乎看不见；现在跟 App 的深浅一致。
     */
    private fun applySystemBars(darkTheme: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            // 状态栏在 29+ 是透明的；29 以下 `auto` 会按深浅给一层默认 scrim。
            statusBarStyle = SystemBarStyle.auto(transparent, transparent) { darkTheme },
            // 导航栏不要跟着传 transparent：API 26–28 的三键导航要用一层 scrim 保证
            // 手势条 / 按钮在页面上看得见，传透明会把那层保护弄没。这两个值就是
            // Activity 库自己用的默认浅 / 深 scrim。
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = 0xE6FFFFFF.toInt(),
                darkScrim = 0x801B1B1B.toInt(),
                detectDarkMode = { darkTheme }
            )
        )
    }

    /** 系统现在是不是深色模式（不经过 Compose 的 `isSystemInDarkTheme`，启动时先算一次）。 */
    private fun isDarkModeNow(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

private fun ThemeChoice.isDark(isSystemDark: Boolean): Boolean = when (this) {
    ThemeChoice.SYSTEM -> isSystemDark
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}
