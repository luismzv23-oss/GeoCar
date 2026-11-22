package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusLogRepository(private val statusLogDao: StatusLogDao) {

  val logsFlow: Flow<List<LogEventEntity>> = statusLogDao.observeLogs()

  suspend fun log(message: String, level: String = "INFO") {
    statusLogDao.insert(
      LogEventEntity(
        timestamp = System.currentTimeMillis(),
        level = level,
        message = message,
      )
    )
    val count = statusLogDao.getCount()
    if (count > MAX_LOG_ROWS) {
      statusLogDao.deleteOldest(count - MAX_LOG_ROWS)
    }
  }

  suspend fun clear() {
    statusLogDao.clearAll()
  }

  suspend fun buildExportText(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val items = statusLogDao.getAllAscending()
    if (items.isEmpty()) return "WayPulse Status Log (empty)\n"
    return buildString {
      items.forEach { item ->
        val ts = formatter.format(Date(item.timestamp))
        append("[$ts] [${item.level}] ${item.message}\n")
      }
    }
  }

  companion object {
    private const val MAX_LOG_ROWS = 1000
  }
}
