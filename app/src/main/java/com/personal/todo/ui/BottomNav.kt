package com.personal.todo.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.ui.theme.LocalTodoColors

/**
 * The four tabs of the bottom bar, in the order the screenshots show them.
 *
 * The route of each tab is the root route of that view: switching tabs resets
 * the back stack instead of pushing on top of it, which is what a bottom bar is
 * expected to do.
 */
enum class HomeTab(val route: String, @StringRes val labelRes: Int) {
    Today("today", R.string.nav_today),
    Calendar("calendar", R.string.nav_calendar),
    Quadrant("quadrant", R.string.nav_quadrant),
    /** 专注（番茄钟）: a root of its own, so the timer is always one tap away. */
    Focus("focus", R.string.focus_title),
    More("more", R.string.nav_more)
}

/** The tag the smoke tests use to reach a tab, since the icons carry no text. */
internal fun tabTag(tab: HomeTab): String = "tab-${tab.route}"

/** The box every tab icon is centred in, so all four line up. */
private val TabHeight = 40.dp

/**
 * The bottom bar of the screenshots: no labels, one rounded square icon per
 * tab, the selected one filled with the blue accent. [todayDayOfMonth] is the
 * number drawn inside the calendar icon, which always shows today.
 */
@Composable
fun TodoBottomBar(
    selected: HomeTab?,
    todayDayOfMonth: Int,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeTab.entries.forEach { tab ->
            TabIcon(
                tab = tab,
                selected = tab == selected,
                todayDayOfMonth = todayDayOfMonth,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).testTag(tabTag(tab))
            )
        }
    }
}

@Composable
private fun TabIcon(
    tab: HomeTab,
    selected: Boolean,
    todayDayOfMonth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    // The selected tab is the blue accent, every other tab the muted grey the
    // screenshots use for the unselected icons.
    val tint = if (selected) colors.accent else colors.dateMuted
    val label = stringResource(tab.labelRes)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, role = Role.Tab, onClick = onClick)
            // Every tab is the same box with the icon centred in it: a tab whose
            // icon is smaller (更多 is three small dots) would otherwise sit higher
            // than its neighbours.
            .height(TabHeight),
        contentAlignment = Alignment.Center
    ) {
        when (tab) {
            HomeTab.Today -> CheckTabIcon(tint)
            HomeTab.Calendar -> CalendarTabIcon(tint, todayDayOfMonth)
            HomeTab.Quadrant -> QuadrantTabIcon(tint)
            HomeTab.Focus -> FocusTabIcon(tint)
            HomeTab.More -> MoreTabIcon(tint)
        }
    }
}

/** 今天: a filled rounded square with a check, scrolled to the accent when selected. */
@Composable
private fun CheckTabIcon(color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 日历: a filled rounded square carrying today's day number. */
@Composable
private fun CalendarTabIcon(color: Color, dayOfMonth: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/** 四象限: a target - the four quadrants around a centre. */
@Composable
private fun QuadrantTabIcon(color: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** 专注: a tomato - a round body under a little stem, the shape of a pomodoro. */
@Composable
private fun FocusTabIcon(color: Color) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 7.dp, height = 3.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 更多: the three dots of the screenshot, inside the same box the other tabs draw in. */
@Composable
private fun MoreTabIcon(color: Color) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                if (index > 0) Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
