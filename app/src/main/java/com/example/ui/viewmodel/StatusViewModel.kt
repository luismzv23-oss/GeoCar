package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LogEventEntity
import com.example.data.local.StatusLogRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatusViewModel(
  private val statusLogRepository: StatusLogRepository,
) : ViewModel() {

  val logs: StateFlow<List<LogEventEntity>> =
    statusLogRepository.logsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = emptyList(),
    )

  private val _lastRefreshMs = MutableStateFlow(System.currentTimeMillis())
  val lastRefreshMs: StateFlow<Long> = _lastRefreshMs.asStateFlow()

  init {
    viewModelScope.launch {
      while (true) {
        delay(5_000L)
        _lastRefreshMs.value = System.currentTimeMillis()
      }
    }
  }

  fun clearLogs() {
    viewModelScope.launch {
      statusLogRepository.clear()
    }
  }

  suspend fun exportLogsText(): String = statusLogRepository.buildExportText()

  class Factory(
    private val statusLogRepository: StatusLogRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      StatusViewModel(statusLogRepository) as T
  }
}
