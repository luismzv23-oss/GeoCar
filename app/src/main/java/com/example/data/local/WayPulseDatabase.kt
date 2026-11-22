package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [PositionEntity::class, LogEventEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class WayPulseDatabase : RoomDatabase() {
  abstract fun positionDao(): PositionDao
  abstract fun statusLogDao(): StatusLogDao
}
