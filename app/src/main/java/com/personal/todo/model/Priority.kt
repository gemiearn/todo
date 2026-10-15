package com.personal.todo.model

/**
 * 任务优先级就是四象限：「重要」和「紧急」是两个独立的维度，两个开关的组合正好四种。
 *
 * 两个维度都存在任务自己身上（`tasks.priority` 这一列），不由截止日期推算——紧急是
 * 用户标的，不是「今天到期」自动算出来的。DB v7 把原来的 NORMAL / HIGH / URGENT
 * 三个旧值重写成下面这四个名字。
 *
 * [rank] 只用于排序：重要又紧急的最靠前，两个都不占的最后。
 */
enum class Priority(val important: Boolean, val urgent: Boolean, val rank: Int) {
    IMPORTANT_URGENT(important = true, urgent = true, rank = 3),
    IMPORTANT_NOT_URGENT(important = true, urgent = false, rank = 2),
    NOT_IMPORTANT_URGENT(important = false, urgent = true, rank = 1),
    NOT_IMPORTANT_NOT_URGENT(important = false, urgent = false, rank = 0);

    companion object {
        /** 两个开关都没打开时的那一档，也是新建任务的默认值。 */
        val DEFAULT = NOT_IMPORTANT_NOT_URGENT

        /** 由两个开关组合出优先级。 */
        fun of(important: Boolean, urgent: Boolean): Priority =
            entries.first { it.important == important && it.urgent == urgent }

        fun fromStored(name: String?): Priority =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
