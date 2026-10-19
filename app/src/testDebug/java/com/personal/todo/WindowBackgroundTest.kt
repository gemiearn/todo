package com.personal.todo

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.ui.theme.todoCanvasColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 窗口底色必须是 App 自己的页面底色。
 *
 * 系统主题给的是纯白 / 纯黑；Compose 还没画满的那一帧露出来的就是它——也就是切页时
 * 闪一下的那块颜色。这里把「窗口底 == 页面底」钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WindowBackgroundTest {

    @Test
    fun theWindowBackgroundIsTheAppCanvasNotTheSystemWhite() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        try {
            val background = activity.window.decorView.background
            assertTrue("窗口背景不是纯色: $background", background is ColorDrawable)
            assertEquals(
                todoCanvasColor(dark = false, backgroundStyle = BackgroundStyle.PLAIN).toArgb(),
                (background as ColorDrawable).color
            )
        } finally {
            activity.finish()
        }
    }
}
