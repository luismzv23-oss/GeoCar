package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.PreferencesRepository
import com.example.data.local.StatusLogRepository
import com.example.data.local.WayPulseDatabase
import com.example.data.local.wayPulseDataStore
import com.example.data.network.BatteryMonitor
import com.example.data.network.OsmAndClient
import com.example.data.network.PositionDispatcher
import com.example.remote.MdmRestrictionsManager
import com.example.service.TrackingController

class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: WayPulseDatabase by lazy {
    Room.databaseBuilder(
      appContext,
      WayPulseDatabase::class.java,
      "waypulse.db",
    ).build()
  }

  val preferencesRepository: PreferencesRepository by lazy {
    PreferencesRepository(appContext.wayPulseDataStore)
  }

  val statusLogRepository: StatusLogRepository by lazy {
    StatusLogRepository(database.statusLogDao())
  }

  val batteryMonitor: BatteryMonitor by lazy {
    BatteryMonitor(appContext)
  }

  val osmAndClient: OsmAndClient by lazy {
    OsmAndClient()
  }

  val positionDispatcher: PositionDispatcher by lazy {
    PositionDispatcher(
      context = appContext,
      positionDao = database.positionDao(),
      preferencesRepository = preferencesRepository,
      statusLogRepository = statusLogRepository,
      osmAndClient = osmAndClient,
    )
  }

  val trackingController: TrackingController by lazy {
    TrackingController(
      context = appContext,
      preferencesRepository = preferencesRepository,
      statusLogRepository = statusLogRepository,
      positionDispatcher = positionDispatcher,
      batteryMonitor = batteryMonitor,
      osmAndClient = osmAndClient,
    )
  }

  val mdmRestrictionsManager: MdmRestrictionsManager by lazy {
    MdmRestrictionsManager(
      context = appContext,
      preferencesRepository = preferencesRepository,
      statusLogRepository = statusLogRepository,
    )
  }
}
