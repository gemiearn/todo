package com.personal.todo.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.LauncherIcon
import com.personal.todo.data.ThemeChoice
import com.personal.todo.data.db.Task
import com.personal.todo.ui.theme.TodoPomodoroTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Isolated layouts: small screen and enlarged text, without the app's database/startup jobs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h800dp")
class UiLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun taskMetadataStaysBelowTheTitleAndControlsRemainEasyToTap() {
        val date = LocalDate.of(2026, 11, 13)
        val zone = ZoneId.of("UTC")
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val title = "整理本月工作记录并准备下个月的项目计划"
        val task = Task(id = 1, title = title, listId = 1, createdAt = now, updatedAt = now,
            plannedDate = date, dueDate = date.plusDays(2),
            reminderAt = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli())
        var checked = false
        var focused = false
        var opened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TodoPomodoroTheme {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        TaskCard(task, 0, false, { checked = it }, { opened = true }, now, zone,
                            modifier = Modifier.testTag("card"), onQuickFocus = { focused = true })
                    }
                }
            }
        }
        val reminder = RuntimeEnvironment.getApplication().getString(R.string.reminder_card_chip, "18:00")
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val reminderBounds = compose.onNodeWithText(reminder, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val focusBounds = compose.onNodeWithTag(quickFocusTag(task.id)).fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.width > 0)
        assertTrue(reminderBounds.top >= titleBounds.bottom)
        assertTrue(titleBounds.right <= focusBounds.left)
        assertTrue(reminderBounds.right <= focusBounds.left)
        compose.onNode(isToggleable()).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag(quickFocusTag(task.id)).performClick()
        compose.runOnIdle {
            assertTrue(checked)
            assertTrue(focused)
            assertTrue(!opened) // Child buttons must not also open the task.
        }
    }

    @Test
    fun appearancePickerAppliesSelectionAndUpdatesTheSettingsRow() {
        var selected = ThemeChoice.SYSTEM
        compose.setContent {
            TodoPomodoroTheme {
                var choice by remember { mutableStateOf(ThemeChoice.SYSTEM) }
                SettingsScreen(choice, { choice = it; selected = it }, ColorStyle.CLASSIC, {},
                    BackgroundStyle.PLAIN, {}, LauncherIcon.TOMATO, {}, {}, {}, {}, onBack = {})
            }
        }
        compose.onNodeWithText("主题").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithText("深色").assertExists()
        compose.runOnIdle { assertEquals(ThemeChoice.DARK, selected) }
        compose.onNodeWithText("主题").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(ThemeChoice.DARK, selected) }
    }
}
