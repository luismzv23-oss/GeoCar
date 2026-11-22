package com.example.data.local

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PasswordService {
  private const val SALT_BYTES = 16
  private val secureRandom = SecureRandom()

  fun hashPassword(plainPassword: String): String {
    val salt = ByteArray(SALT_BYTES)
    secureRandom.nextBytes(salt)
    val digest = sha256(salt, plainPassword)
    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    val digestB64 = Base64.encodeToString(digest, Base64.NO_WRAP)
    return "$saltB64:$digestB64"
  }

  fun verifyPassword(plainPassword: String, storedHash: String?): Boolean {
    if (storedHash.isNullOrBlank()) return true
    val parts = storedHash.split(":")
    if (parts.size != 2) return false
    return try {
      val salt = Base64.decode(parts[0], Base64.NO_WRAP)
      val expected = Base64.decode(parts[1], Base64.NO_WRAP)
      val actual = sha256(salt, plainPassword)
      MessageDigest.isEqual(expected, actual)
    } catch (_: Exception) {
      false
    }
  }

  private fun sha256(salt: ByteArray, password: String): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(salt)
    md.update(password.toByteArray(Charsets.UTF_8))
    return md.digest()
  }
}
