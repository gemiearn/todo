package com.personal.todo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 优先级就是四象限：「重要」和「紧急」两个开关正好组合出四种，存的是那一个值。
 */
class PriorityTest {

    @Test
    fun theTwoSwitchesCombineIntoExactlyFourPriorities() {
        assertEquals(
            Priority.NOT_IMPORTANT_NOT_URGENT,
            Priority.of(important = false, urgent = false)
        )
        assertEquals(
            Priority.NOT_IMPORTANT_URGENT,
            Priority.of(important = false, urgent = true)
        )
        assertEquals(
            Priority.IMPORTANT_NOT_URGENT,
            Priority.of(important = true, urgent = false)
        )
        assertEquals(
            Priority.IMPORTANT_URGENT,
            Priority.of(important = true, urgent = true)
        )
    }

    @Test
    fun everyPriorityKnowsItsOwnTwoDimensions() {
        assertEquals(4, Priority.entries.size)
        Priority.entries.forEach { priority ->
            assertEquals(priority, Priority.of(priority.important, priority.urgent))
        }
    }

    @Test
    fun theDefaultIsTheOneWithBothSwitchesOff() {
        assertEquals(Priority.NOT_IMPORTANT_NOT_URGENT, Priority.DEFAULT)
        assertFalse(Priority.DEFAULT.important)
        assertFalse(Priority.DEFAULT.urgent)
    }

    @Test
    fun storedNamesRoundTripAndAnythingElseFallsBackToTheDefault() {
        Priority.entries.forEach { priority ->
            assertEquals(priority, Priority.fromStored(priority.name))
        }
        assertEquals(Priority.DEFAULT, Priority.fromStored(null))
        assertEquals(Priority.DEFAULT, Priority.fromStored("SOMETHING_ELSE"))
    }

    @Test
    fun theRankSortsImportantFirstAndThenUrgent() {
        assertTrue(Priority.NOT_IMPORTANT_NOT_URGENT.rank < Priority.NOT_IMPORTANT_URGENT.rank)
        assertTrue(Priority.NOT_IMPORTANT_URGENT.rank < Priority.IMPORTANT_NOT_URGENT.rank)
        assertTrue(Priority.IMPORTANT_NOT_URGENT.rank < Priority.IMPORTANT_URGENT.rank)
    }
}
