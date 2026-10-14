package com.personal.todo.data

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The crash log the 更多 page shows. It is the only way to read a crash on a
 * phone that has no development tools attached to it, so it must never throw
 * and must always hold exactly the newest crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        CrashLog.clear(context)
    }

    @Test
    fun anEmptyLogReadsAsNothing() {
        assertNull(CrashLog.read(context))
    }

    @Test
    fun aCrashIsKeptUntilItIsCleared() {
        CrashLog.record(context, IllegalStateException("boom"))

        val text = requireNotNull(CrashLog.read(context))
        assertTrue("异常类型要能看到", text.contains("IllegalStateException"))
        assertTrue("异常信息要能看到", text.contains("boom"))
        assertTrue("调用栈要能看到", text.contains("CrashLogTest"))

        CrashLog.clear(context)
        assertNull(CrashLog.read(context))
    }

    @Test
    fun recordingReplacesThePreviousCrash() {
        CrashLog.record(context, IllegalStateException("第一次"))
        CrashLog.record(context, IllegalStateException("第二次"))

        val text = requireNotNull(CrashLog.read(context))
        assertFalse("只保留最新一条，避免文件无限增长", text.contains("第一次"))
        assertTrue(text.contains("第二次"))
    }
}
