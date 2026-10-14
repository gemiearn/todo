package com.personal.todo.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM test for the priority model - no Android runtime involved.
 */
class PriorityTest {

    @Test
    fun storedNamesMapBackToTheSameValue() {
        Priority.entries.forEach { priority ->
            assertEquals(priority, Priority.fromStored(priority.name))
        }
    }

    @Test
    fun unknownOrMissingStoredValueFallsBackToNormal() {
        assertEquals(Priority.NORMAL, Priority.fromStored(null))
        assertEquals(Priority.NORMAL, Priority.fromStored("SOMETHING_ELSE"))
    }

    @Test
    fun normalIsWeakerThanHighWhichIsWeakerThanUrgent() {
        assert(Priority.NORMAL.rank < Priority.HIGH.rank)
        assert(Priority.HIGH.rank < Priority.URGENT.rank)
    }
}
