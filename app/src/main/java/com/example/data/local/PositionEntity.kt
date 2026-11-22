package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "positions")
data class PositionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val deviceId: String,
  val time: Long,
  val latitude: Double,
  val longitude: Double,
  val altitude: Double,
  val speed: Double,
  val course: Double,
  val accuracy: Double,
  val battery: Double,
  val charging: Boolean,
  val mock: Boolean,
  val alarm: String? = null,
)
