package com.personal.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.personal.todo.ui.theme.TodoPomodoroTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 图标选择器按「种类」分页，不再按清单 / 习惯分。
 *
 * 这里只钉两件事：种类页签能切换到对应格子；点一个图标真的把字符串交给上层。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class IconPickerSmokeTest {

    @get:Rule
    val idleTimeout = shortIdleTimeout()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theKindTabsSwitchTheGridAndPickingReturnsTheEmoji() {
        var picked by mutableStateOf("")
        compose.setContent {
            TodoPomodoroTheme {
                IconPicker(
                    selected = picked,
                    onPick = { picked = it }
                )
            }
        }

        // 默认在「工作」这一页，点一个工作图标。
        compose.onNodeWithText("💼").performClick()
        assertEquals("💼", picked)

        // 切到「学习」页，格子换成学习那一组。
        compose.onNodeWithText("学习").performClick()
        compose.onNodeWithText("📚").performClick()
        assertEquals("📚", picked)

        // 「无」清空。
        compose.onNodeWithText("无").performClick()
        assertEquals(NoIcon, picked)
    }

    @Test
    fun anExistingIconOpensItsOwnKind() {
        var picked by mutableStateOf("🐶")
        compose.setContent {
            TodoPomodoroTheme {
                IconPicker(
                    selected = picked,
                    onPick = { picked = it }
                )
            }
        }

        // 已经选了动物图标：打开就该停在「动物」页，而不是从「工作」开始找。
        compose.onNodeWithText("🐱").performClick()
        assertEquals("🐱", picked)
    }

    /**
     * 弹层里那块内容（名称输入 + 图标种类）真的接上了图标选择器。
     *
     * 这里组合的是 [TextInputDialogBody]，不是整套 [TextInputDialog]：Robolectric 上
     * `TextField` 一旦进了 `Dialog`（第二个窗口），那个窗口的测量就永远收敛不了，
     * `setContent` 会一直等到 Espresso 的 60 秒空闲超时抛 `AppNotIdleException`——报的是
     * "infinite composition loop"，但页面代码没有问题（Robolectric 4.14.1 与 4.17、
     * native 与 legacy 图形模式都一样卡）。所以「弹层这一层」只能留在真机上验，
     * JVM 这边钉住它里面装的是什么。
     */
    @Test
    fun theListDialogBodyShowsTheSameKindPicker() {
        var value by mutableStateOf("")
        var icon by mutableStateOf(NoIcon)
        compose.setContent {
            TodoPomodoroTheme {
                TextInputDialogBody(
                    label = "名称",
                    value = value,
                    onValueChange = { value = it },
                    icon = icon,
                    onIconChange = { icon = it }
                )
            }
        }

        // 弹层里真有种类页签和图标格子，不是只有清单编辑页接对了。
        compose.onNodeWithText("工作").assertExists()
        compose.onNodeWithText("💼").performClick()
        assertEquals("💼", icon)
    }
}
