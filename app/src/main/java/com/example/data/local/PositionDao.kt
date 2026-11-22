package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(position: PositionEntity): Long

  @Query("SELECT * FROM positions ORDER BY id ASC LIMIT 1")
  suspend fun getOldest(): PositionEntity?

  @Query("SELECT COUNT(*) FROM positions")
  fun observeCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM positions")
  suspend fun getCount(): Int

  @Query("DELETE FROM positions WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query(
    "DELETE FROM positions WHERE id IN (SELECT id FROM positions ORDER BY id ASC LIMIT :excessCount)"
  )
  suspend fun deleteOldestBatch(excessCount: Int)

  @Query("DELETE FROM positions")
  suspend fun deleteAll()
}
