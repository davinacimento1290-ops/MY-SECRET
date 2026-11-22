package com.mysecret.app.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LocalAuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("my_secret_auth", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.contains("pin_hash")

    fun configurePin(pin: CharArray) {
        require(pin.size in 4..12 && pin.all(Char::isDigit)) { "O PIN deve ter de 4 a 12 números." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failed_attempts", 0)
            .commit()
        pin.fill('\u0000')
    }

    fun verifyPin(pin: CharArray): Boolean {
        if (!isConfigured()) return false
        val attempts = prefs.getInt("failed_attempts", 0)
        if (attempts >= 8) {
            pin.fill('\u0000')
            return false
        }
        val salt = Base64.decode(prefs.getString("pin_salt", "")!!, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", "")!!, Base64.NO_WRAP)
        val actual = derive(pin, salt)
        pin.fill('\u0000')
        val ok = java.security.MessageDigest.isEqual(expected, actual)
        prefs.edit().putInt("failed_attempts", if (ok) 0 else attempts + 1).apply()
        return ok
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
