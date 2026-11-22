package com.example

import com.example.data.local.PositionEntity
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import com.example.data.network.OsmAndClient
import com.example.data.network.OsmAndProtocol
import com.example.data.network.SendResult
import com.example.location.PositionFilter
import com.example.location.TrackingLocation
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OsmAndProtocolAndFilterTest {

  @Test
  fun `osmand url uses dot decimal separator regardless of device locale and converts speed to knots`() {
    val previousLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("es-ES"))
      val pos =
        PositionEntity(
          deviceId = "12345678",
          time = 1_763_740_800_000L,
          latitude = -34.6037,
          longitude = -58.3816,
          altitude = 25.4,
          speed = 10.0,
          course = 90.0,
          accuracy = 8.2,
          battery = 87.0,
          charging = false,
          mock = false,
          alarm = "sos",
        )
      val url = OsmAndProtocol.buildPositionUrl("http://demo.traccar.org:5055", pos)
      assertNotNull(url)
      assertTrue(url!!.contains("id=12345678"))
      assertTrue(url.contains("timestamp=1763740800"))
      assertTrue(url.contains("lat=-34.603700"))
      assertTrue(url.contains("lon=-58.381600"))
      assertTrue(url.contains("speed=19.44"))
      assertTrue(url.contains("batt=87"))
      assertTrue(url.contains("charge=false"))
      assertTrue(url.contains("alarm=sos"))
      assertFalse(url.contains(","))
    } finally {
      Locale.setDefault(previousLocale)
    }
  }

  @Test
  fun `preferences validation rejects invalid urls and clamps heartbeat 1 to 59 up to 60`() {
    assertTrue(TrackingPreferences.isValidServerUrl("http://demo.traccar.org:5055"))
    assertTrue(TrackingPreferences.isValidServerUrl("https://gps.example.com/osmand"))
    assertFalse(TrackingPreferences.isValidServerUrl("ftp://example.com:5055"))
    assertFalse(TrackingPreferences.isValidServerUrl("http://"))
    assertFalse(TrackingPreferences.isValidServerUrl(""))

    assertEquals(0, TrackingPreferences.normalizeHeartbeat(0))
    assertEquals(60, TrackingPreferences.normalizeHeartbeat(1))
    assertEquals(60, TrackingPreferences.normalizeHeartbeat(59))
    assertEquals(120, TrackingPreferences.normalizeHeartbeat(120))

    assertTrue(TrackingPreferences.isIntervalVisible(TrackingAccuracy.HIGHEST, 75))
    assertTrue(TrackingPreferences.isIntervalVisible(TrackingAccuracy.MEDIUM, 0))
    assertFalse(TrackingPreferences.isIntervalVisible(TrackingAccuracy.MEDIUM, 75))

    assertTrue(TrackingPreferences.isAngleVisible(TrackingAccuracy.HIGHEST))
    assertFalse(TrackingPreferences.isAngleVisible(TrackingAccuracy.HIGH))
  }

  @Test
  fun `position filter enforces distance interval and angle rules`() {
    val filter = PositionFilter()
    val prefs =
      TrackingPreferences(
        accuracy = TrackingAccuracy.HIGHEST,
        distance = 100,
        interval = 300,
        angle = 45,
      )

    val p1 =
      TrackingLocation(-34.6000, -58.3800, 10.0, 5.0, 0.0, 5.0, 1_000_000L, false, "fused")
    assertTrue(filter.shouldAccept(p1, prefs))

    // Too close, too soon, small angle -> reject
    val p2 =
      TrackingLocation(-34.6001, -58.3801, 10.0, 5.0, 10.0, 5.0, 1_010_000L, false, "fused")
    assertFalse(filter.shouldAccept(p2, prefs))

    // Bearing changed by >= 45 degrees -> accept
    val p3 =
      TrackingLocation(-34.6001, -58.3801, 10.0, 5.0, 50.0, 5.0, 1_020_000L, false, "fused")
    assertTrue(filter.shouldAccept(p3, prefs))

    // Interval >= 300s -> accept
    val p4 =
      TrackingLocation(-34.6001, -58.3801, 10.0, 5.0, 55.0, 5.0, 1_325_000L, false, "fused")
    assertTrue(filter.shouldAccept(p4, prefs))
  }

  @Test
  fun `osmand client handles 200 400 and 500 server responses`() = runTest {
    val server = MockWebServer()
    server.enqueue(MockResponse().setResponseCode(200))
    server.enqueue(MockResponse().setResponseCode(400))
    server.enqueue(MockResponse().setResponseCode(500))
    server.start()

    try {
      val baseUrl = server.url("/").toString().removeSuffix("/")
      val client = OsmAndClient()
      val pos =
        PositionEntity(
          deviceId = "12345678",
          time = 1_763_740_800_000L,
          latitude = 37.422,
          longitude = -122.084,
          altitude = 15.0,
          speed = 2.0,
          course = 180.0,
          accuracy = 5.0,
          battery = 90.0,
          charging = true,
          mock = false,
          alarm = null,
        )

      assertTrue(client.sendPosition(baseUrl, pos) is SendResult.Success)
      val r400 = client.sendPosition(baseUrl, pos)
      assertTrue(r400 is SendResult.HttpFailure && r400.statusCode == 400)
      val r500 = client.sendPosition(baseUrl, pos)
      assertTrue(r500 is SendResult.HttpFailure && r500.statusCode == 500)
    } finally {
      server.shutdown()
    }
  }
}
