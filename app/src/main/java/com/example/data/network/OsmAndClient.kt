package com.example.data.network

import com.example.data.local.PositionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class SendResult {
  data class Success(val statusCode: Int) : SendResult()
  data class HttpFailure(val statusCode: Int, val message: String) : SendResult()
  data class NetworkFailure(val error: String) : SendResult()
  data object InvalidUrl : SendResult()
}

class OsmAndClient(
  private val okHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
) {

  suspend fun sendPosition(serverUrl: String, position: PositionEntity): SendResult =
    withContext(Dispatchers.IO) {
      val requestUrl = OsmAndProtocol.buildPositionUrl(serverUrl, position)
        ?: return@withContext SendResult.InvalidUrl

      val request = Request.Builder().url(requestUrl).get().build()

      try {
        okHttpClient.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            SendResult.Success(response.code)
          } else {
            SendResult.HttpFailure(response.code, response.message)
          }
        }
      } catch (e: IOException) {
        SendResult.NetworkFailure(e.message ?: "IO error")
      } catch (e: Exception) {
        SendResult.NetworkFailure(e.message ?: "Unexpected network error")
      }
    }

  suspend fun registerNotificationToken(
    serverUrl: String,
    deviceId: String,
    token: String,
  ): Boolean =
    withContext(Dispatchers.IO) {
      val cleanUrl = serverUrl.trim()
      if (cleanUrl.isEmpty() || token.isBlank()) return@withContext false
      val body =
        FormBody.Builder()
          .add("id", deviceId)
          .add("notificationToken", token)
          .build()
      val request = Request.Builder().url(cleanUrl).post(body).build()
      try {
        okHttpClient.newCall(request).execute().use { it.isSuccessful }
      } catch (_: Exception) {
        false
      }
    }
}
