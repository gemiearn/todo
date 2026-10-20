package com.personal.todo.ui

import java.util.concurrent.TimeUnit
import org.junit.rules.ExternalResource

/**
 * 把 Espresso 的「等应用空闲」超时从默认 60 秒压到 [seconds] 秒。
 *
 * Compose 的 JVM 测试在 Robolectric 上如果永远等不到空闲，默认会原地转满 **60 秒**才抛
 * `AppNotIdleException`；一个用例卡住就是整整一分钟，几个就凑成几分钟的构建，而日志里
 * 只有一句 "did not get idle"。压到 20 秒之后同样的失败 20 秒就能看到，正常用例不受影响
 * （它们的 `waitForIdle` 都在毫秒级返回，这个超时只是一条安全线）。
 *
 * Espresso 只是 Compose `ui-test` 的运行时依赖，没有进测试的编译类路径，所以这里反射设置：
 * 为此多引一个 `espresso-core` 依赖不值得。
 */
class ShortIdleTimeoutRule(private val seconds: Long = 20L) : ExternalResource() {

    override fun before() {
        val policies = Class.forName("androidx.test.espresso.IdlingPolicies")
        for (method in listOf("setMasterPolicyTimeout", "setIdlingResourceTimeout")) {
            policies
                .getMethod(method, Long::class.javaPrimitiveType, TimeUnit::class.java)
                .invoke(null, seconds, TimeUnit.SECONDS)
        }
    }
}

/** 20 秒的等空闲超时，见 [ShortIdleTimeoutRule]。 */
fun shortIdleTimeout(seconds: Long = 20L): ShortIdleTimeoutRule = ShortIdleTimeoutRule(seconds)
