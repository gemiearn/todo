package com.personal.todo.model

/**
 * Task priority. The spec (section 3) suggests three levels: normal, high and
 * urgent. Only ordering rank and the storage name matter here - the user
 * visible label lives in string resources.
 */
enum class Priority(val rank: Int) {
    NORMAL(0),
    HIGH(1),
    URGENT(2);

    companion object {
        fun fromStored(name: String?): Priority =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
