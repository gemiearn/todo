package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 一个习惯（需求文档 3：习惯与任务分开建模，绝不拿「每天重复任务」冒充）。
 *
 * 习惯是「每天想做的一件事」：**不生成未来实例、不进日历、不进逾期、没有连续天数/惩罚**。
 * 明天该做什么完全由 [dailyTarget] 与当天的打卡记录决定，库里不留任何预先生成的行。
 *
 * [dailyTarget] 是每天想完成的次数（喝水 5 次 → 5），至少 1；次数为 1 的习惯就是普通的
 * 「今天做没做」。
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val dailyTarget: Int = 1,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 某个习惯某一天的打卡记录，主键＝`(habitId, 本地日期)`。
 *
 * `date` 是**本地日期**（和任务的日期一样存成 `YYYY-MM-DD`），绝不用 UTC 时刻去反推——
 * 否则换个时区就会把打卡挪到隔壁那天。日期键必须一次定对，因为以后加频率（每周几次之类）
 * 也要挂在这个键上。
 *
 * 没有记录就等于那天一次都没打，所以 [count] 减到 0 时调用方会直接把这一行删掉，不留 0 值行。
 */
@Entity(
    tableName = "habit_check_ins",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("habitId"), Index("date")]
)
data class HabitCheckIn(
    val habitId: Long,
    val date: LocalDate,
    val count: Int,
    val updatedAt: Long
)
