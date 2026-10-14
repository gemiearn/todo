package com.personal.todo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 0 sanity test.
 *
 * It only proves that the JVM unit-test source set, the JUnit dependency and
 * the CI test step are wired up correctly. Business-rule tests (dates,
 * completion semantics, focus statistics, ...) are added in the phases that
 * implement those rules.
 */
class SkeletonTest {

    @Test
    fun unitTestHarnessIsWired() {
        assertEquals(4, 2 + 2)
    }
}
