package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.data.model.TrackingAccuracy
import com.example.data.model.TrackingPreferences
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface LocationEngine {
  val engineName: String
  fun startUpdates(prefs: TrackingPreferences, onLocation: (TrackingLocation) -> Unit)
  fun stopUpdates()
  suspend fun fetchSingleLocation(prefs: TrackingPreferences): TrackingLocation?
}

class FusedLocationEngine(private val context: Context) : LocationEngine {
  override val engineName: String = "Fused"
  private val client = LocationServices.getFusedLocationProviderClient(context)
  private var callback: LocationCallback? = null

  @SuppressLint("MissingPermission")
  override fun startUpdates(prefs: TrackingPreferences, onLocation: (TrackingLocation) -> Unit) {
    stopUpdates()
    val priority = mapPriority(prefs.accuracy)
    val intervalMs = computeIntervalMs(prefs)
    val minDistance = if (prefs.accuracy == TrackingAccuracy.HIGHEST) 0f else prefs.distance.toFloat()

    val request =
      LocationRequest.Builder(priority, intervalMs)
        .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(1_000L))
        .setMinUpdateDistanceMeters(minDistance)
        .build()

    val cb =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          result.lastLocation?.let { loc ->
            onLocation(TrackingLocation.fromAndroidLocation(loc))
          }
        }
      }
    callback = cb
    client.requestLocationUpdates(request, cb, Looper.getMainLooper())
  }

  override fun stopUpdates() {
    callback?.let { client.removeLocationUpdates(it) }
    callback = null
  }

  @SuppressLint("MissingPermission")
  override suspend fun fetchSingleLocation(prefs: TrackingPreferences): TrackingLocation? =
    withTimeoutOrNull(12_000L) {
      suspendCancellableCoroutine { cont ->
        client
          .getCurrentLocation(mapPriority(prefs.accuracy), null)
          .addOnSuccessListener { loc ->
            if (loc != null && cont.isActive) {
              cont.resume(TrackingLocation.fromAndroidLocation(loc))
            } else if (cont.isActive) {
              client.lastLocation
                .addOnSuccessListener { last ->
                  if (cont.isActive) {
                    cont.resume(last?.let { TrackingLocation.fromAndroidLocation(it) })
                  }
                }
                .addOnFailureListener {
                  if (cont.isActive) cont.resume(null)
                }
            }
          }
          .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
          }
      }
    }

  private fun mapPriority(accuracy: TrackingAccuracy): Int =
    when (accuracy) {
      TrackingAccuracy.HIGHEST,
      TrackingAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
      TrackingAccuracy.MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
      TrackingAccuracy.LOW -> Priority.PRIORITY_LOW_POWER
    }

  private fun computeIntervalMs(prefs: TrackingPreferences): Long {
    val seconds =
      if (TrackingPreferences.isIntervalVisible(prefs.accuracy, prefs.distance)) {
        prefs.interval
      } else {
        30
      }
    return (seconds.coerceAtLeast(1)) * 1000L
  }
}

class PlatformLocationEngine(private val context: Context) : LocationEngine {
  override val engineName: String = "LocationManager"
  private val locationManager =
    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  private var activeListener: LocationListener? = null

  @SuppressLint("MissingPermission")
  override fun startUpdates(prefs: TrackingPreferences, onLocation: (TrackingLocation) -> Unit) {
    stopUpdates()
    val provider = chooseProvider(prefs.accuracy)
    val intervalMs =
      if (TrackingPreferences.isIntervalVisible(prefs.accuracy, prefs.distance)) {
        prefs.interval.coerceAtLeast(1) * 1000L
      } else {
        15_000L
      }
    val minDistance =
      if (prefs.accuracy == TrackingAccuracy.HIGHEST) 0f else prefs.distance.toFloat()

    val listener =
      object : LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
          onLocation(TrackingLocation.fromAndroidLocation(location))
        }

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
      }

    activeListener = listener
    if (locationManager.allProviders.contains(provider)) {
      locationManager.requestLocationUpdates(
        provider,
        intervalMs,
        minDistance,
        listener,
        Looper.getMainLooper(),
      )
    }
    if (provider != LocationManager.NETWORK_PROVIDER &&
      locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)
    ) {
      locationManager.requestLocationUpdates(
        LocationManager.NETWORK_PROVIDER,
        intervalMs.coerceAtLeast(30_000L),
        minDistance,
        listener,
        Looper.getMainLooper(),
      )
    }
  }

  override fun stopUpdates() {
    activeListener?.let { locationManager.removeUpdates(it) }
    activeListener = null
  }

  @SuppressLint("MissingPermission")
  override suspend fun fetchSingleLocation(prefs: TrackingPreferences): TrackingLocation? {
    val provider = chooseProvider(prefs.accuracy)
    val fresh =
      withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<TrackingLocation?> { cont ->
          val listener =
            object : LocationListener {
              override fun onLocationChanged(location: android.location.Location) {
                locationManager.removeUpdates(this)
                if (cont.isActive) cont.resume(TrackingLocation.fromAndroidLocation(location))
              }

              @Deprecated("Deprecated in Android API")
              override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
          cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
          val targetProvider =
            if (locationManager.allProviders.contains(provider)) {
              provider
            } else {
              LocationManager.NETWORK_PROVIDER
            }
          if (locationManager.allProviders.contains(targetProvider)) {
            locationManager.requestLocationUpdates(
              targetProvider,
              0L,
              0f,
              listener,
              Looper.getMainLooper(),
            )
          } else if (cont.isActive) {
            cont.resume(null)
          }
        }
      }
    if (fresh != null) return fresh

    val fallback =
      listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .firstNotNullOfOrNull { p ->
          runCatching { locationManager.getLastKnownLocation(p) }.getOrNull()
        }
    return fallback?.let { TrackingLocation.fromAndroidLocation(it) }
  }

  private fun chooseProvider(accuracy: TrackingAccuracy): String =
    when (accuracy) {
      TrackingAccuracy.HIGHEST,
      TrackingAccuracy.HIGH -> LocationManager.GPS_PROVIDER
      TrackingAccuracy.MEDIUM,
      TrackingAccuracy.LOW ->
        if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
          LocationManager.NETWORK_PROVIDER
        } else {
          LocationManager.GPS_PROVIDER
        }
    }
}

object LocationEngineFactory {
  fun create(context: Context, preferPlatformProviders: Boolean): LocationEngine {
    val playServicesAvailable =
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
        ConnectionResult.SUCCESS
    return if (preferPlatformProviders || !playServicesAvailable) {
      PlatformLocationEngine(context)
    } else {
      FusedLocationEngine(context)
    }
  }
}
