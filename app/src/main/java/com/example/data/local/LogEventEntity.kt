package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "status_logs")
data class LogEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestamp: Long = System.currentTimeMillis(),
  val level: String = "INFO",
  val message: String,
)
