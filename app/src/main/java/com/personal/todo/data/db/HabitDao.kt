package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 习惯与它的打卡记录。
 *
 * 打卡记录按 `(habitId, 日期)` 定位：没有行＝那天没打过，所以减到 0 由调用方删行，
 * 这里不提供「写 0」的路径。删习惯时调用方在同一事务里先清它的记录，绝不留一行指向
 * 已经不存在的习惯（外键是 RESTRICT）。
 */
@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    fun observeHabits(): Flow<List<Habit>>

    /** [from]..[to]（含首尾）这段日期里的打卡记录，页面的「最近 7 天」格子用它。 */
    @Query("SELECT * FROM habit_check_ins WHERE date >= :from AND date <= :to")
    fun observeCheckInsBetween(from: LocalDate, to: LocalDate): Flow<List<HabitCheckIn>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    @Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND date = :date")
    suspend fun getCheckIn(habitId: Long, date: LocalDate): HabitCheckIn?

    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun maxSortOrder(): Int?

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckIn(checkIn: HabitCheckIn)

    @Query("DELETE FROM habit_check_ins WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCheckIn(habitId: Long, date: LocalDate)

    @Query("DELETE FROM habit_check_ins WHERE habitId = :habitId")
    suspend fun clearCheckInsOf(habitId: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)
}
