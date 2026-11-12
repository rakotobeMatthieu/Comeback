package com.rakotobe.comeback.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ComebackDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    suspend fun habits(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): Habit?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM habits")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Query("SELECT * FROM checks")
    fun observeChecks(): Flow<List<Check>>

    @Query("SELECT * FROM checks")
    suspend fun checks(): List<Check>

    @Query("SELECT * FROM checks WHERE day = :day")
    suspend fun checksOn(day: Long): List<Check>

    @Query("SELECT * FROM checks WHERE habitId = :habitId AND day = :day")
    suspend fun check(habitId: Long, day: Long): Check?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: Check)

    @Query("DELETE FROM checks WHERE habitId = :habitId AND day = :day")
    suspend fun deleteCheck(habitId: Long, day: Long)

    @Query("SELECT * FROM day_logs ORDER BY day")
    fun observeLogs(): Flow<List<DayLog>>

    @Query("SELECT * FROM day_logs ORDER BY day")
    suspend fun logs(): List<DayLog>

    @Query("SELECT MAX(day) FROM day_logs")
    suspend fun lastLoggedDay(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<DayLog>)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM checks")
    suspend fun clearChecks()

    @Query("DELETE FROM day_logs")
    suspend fun clearLogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<Habit>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecks(checks: List<Check>)
}

@Database(entities = [Habit::class, Check::class, DayLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ComebackDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "comeback.db").build()
    }
}
