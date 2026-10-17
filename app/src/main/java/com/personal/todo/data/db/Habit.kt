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
 * [dailyTarget] 是每天想完成的量，[unitLabel] 是它的单位、[stepAmount] 是点一下代表多少：
 * 「喝水 5 次」＝目标 5、单位「次」、单次 1；「阅读 60 分钟」＝目标 60、单位「分钟」、
 * 单次 15（点 4 下够数）。单位与单次都可以由用户改，[unitLabel] 为空就是「次」、
 * [stepAmount] 为空就是 1，于是 v11 之前的老习惯显示与原来一模一样。
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** 每天想完成的量，**以 [unit] 为单位**（如 单位=分钟、目标=60 分钟）。 */
    val dailyTarget: Int = 1,
    /**
     * 量的单位文字（「分钟」「小时」「杯」「毫升」「页」…）。
     *
     * null 表示「次」——这是 v11 之前就有的习惯，也是一个新习惯的默认值，所以不用数据库
     * 默认值，读出来再兜底，迁移时那一列留空即可。
     */
    val unitLabel: String? = null,
    /**
     * 点一下打卡代表多少（以同一单位计，如「分钟 15」）。
     *
     * null 表示 1——同样是老习惯与新习惯的默认值，历史数据不动。
     */
    val stepAmount: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 显示用的单位；没有就是「次」。 */
    val unit: String get() = unitLabel ?: DefaultUnit

    /** 显示用的单次基数；至少 1，「0 步」没有意义。 */
    val step: Int get() = (stepAmount ?: 1).coerceAtLeast(1)

    companion object {
        const val DefaultUnit = "次"
    }
}

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
