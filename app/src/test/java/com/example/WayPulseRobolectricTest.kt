package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PasswordService
import com.example.data.local.PositionEntity
import com.example.data.local.WayPulseDatabase
import com.example.remote.RemoteConfigAction
import com.example.remote.RemoteConfigParser
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WayPulseRobolectricTest {

  private lateinit var db: WayPulseDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, WayPulseDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `room position queue delivers fifo order`() = runTest {
    val dao = db.positionDao()
    dao.insert(
      PositionEntity(
        deviceId = "11112222",
        time = 1000L,
        latitude = 1.0,
        longitude = 2.0,
        altitude = 0.0,
        speed = 0.0,
        course = 0.0,
        accuracy = 5.0,
        battery = 80.0,
        charging = false,
        mock = false,
      )
    )
    dao.insert(
      PositionEntity(
        deviceId = "11112222",
        time = 2000L,
        latitude = 3.0,
        longitude = 4.0,
        altitude = 0.0,
        speed = 0.0,
        course = 0.0,
        accuracy = 5.0,
        battery = 80.0,
        charging = false,
        mock = false,
      )
    )

    assertEquals(2, dao.getCount())
    val first = dao.getOldest()!!
    assertEquals(1000L, first.time)
    dao.deleteById(first.id)

    val second = dao.getOldest()!!
    assertEquals(2000L, second.time)
  }

  @Test
  fun `password service hashes with salt and verifies correctly`() {
    val hash1 = PasswordService.hashPassword("s3cr3t")
    val hash2 = PasswordService.hashPassword("s3cr3t")
    assertNotEquals(hash1, hash2)
    assertFalse(hash1.contains("s3cr3t"))
    assertTrue(PasswordService.verifyPassword("s3cr3t", hash1))
    assertFalse(PasswordService.verifyPassword("wrong", hash1))
  }

  @Test
  fun `remote config parser handles actions and query keys`() {
    assertEquals(
      RemoteConfigAction.StartTracking,
      RemoteConfigParser.parseUriString("waypulse://control?action=start"),
    )
    assertEquals(
      RemoteConfigAction.StopTracking,
      RemoteConfigParser.parseUriString("traccar://control?action=stop"),
    )
    val parsed =
      RemoteConfigParser.parseUriString(
        "waypulse://config?id=87654321&url=https://gps.test.org&accuracy=highest&distance=50&buffer=true"
      )
    assertTrue(parsed is RemoteConfigAction.ApplySettings)
    val map = (parsed as RemoteConfigAction.ApplySettings).values
    assertEquals("87654321", map["id"])
    assertEquals("https://gps.test.org", map["url"])
    assertEquals("highest", map["accuracy"])
    assertEquals("50", map["distance"])
    assertEquals("true", map["buffer"])

    val multiProto =
      RemoteConfigParser.parseUriString(
        "waypulse://config?protocol=teltonika&transport=tcp&url=gps.fleet.io:5027&id=358901234567890"
      )
    assertTrue(multiProto is RemoteConfigAction.ApplySettings)
    val protoMap = (multiProto as RemoteConfigAction.ApplySettings).values
    assertEquals("teltonika", protoMap["protocol"])
    assertEquals("tcp", protoMap["transport"])
    assertEquals("gps.fleet.io:5027", protoMap["url"])
  }
}
