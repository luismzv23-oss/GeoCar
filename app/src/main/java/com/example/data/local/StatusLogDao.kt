package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusLogDao {
  @Query("SELECT * FROM status_logs ORDER BY id DESC LIMIT 500")
  fun observeLogs(): Flow<List<LogEventEntity>>

  @Query("SELECT * FROM status_logs ORDER BY id ASC")
  suspend fun getAllAscending(): List<LogEventEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(event: LogEventEntity)

  @Query("DELETE FROM status_logs")
  suspend fun clearAll()

  @Query(
    "DELETE FROM status_logs WHERE id IN (SELECT id FROM status_logs ORDER BY id ASC LIMIT :excess)"
  )
  suspend fun deleteOldest(excess: Int)

  @Query("SELECT COUNT(*) FROM status_logs")
  suspend fun getCount(): Int
}
