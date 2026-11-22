package com.mysecret.app.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LocalAuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("my_secret_auth", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.contains("pin_hash")

    @Synchronized
    fun configurePin(pin: CharArray) {
        require(pin.size in 4..12 && pin.all(Char::isDigit)) { "O PIN deve ter de 4 a 12 números." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        check(prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failed_attempts", 0)
            .putLong("lock_until", 0L)
            .commit()) { "Não foi possível salvar a configuração de acesso." }
        pin.fill('\u0000')
    }

    @Synchronized
    fun verifyPin(pin: CharArray): Boolean {
        if (!isConfigured()) { pin.fill('\u0000'); return false }
        val now = System.currentTimeMillis()
        val lockUntil = prefs.getLong("lock_until", 0L)
        if (now < lockUntil) { pin.fill('\u0000'); return false }
        val attempts = prefs.getInt("failed_attempts", 0)
        val salt = Base64.decode(prefs.getString("pin_salt", "")!!, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", "")!!, Base64.NO_WRAP)
        val actual = derive(pin, salt)
        pin.fill('\u0000')
        val ok = java.security.MessageDigest.isEqual(expected, actual)
        if (ok) {
            prefs.edit().putInt("failed_attempts", 0).putLong("lock_until", 0L).apply()
        } else {
            val next = attempts + 1
            val until = if (next >= 8) now + 30L * 60L * 1000L else 0L
            prefs.edit().putInt("failed_attempts", if (next >= 8) 0 else next).putLong("lock_until", until).apply()
        }
        return ok
    }

    @Synchronized
    fun configureSecretAnswerIfMissing() {
        if (prefs.contains("secret_hash")) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive("9090".toCharArray(), salt)
        check(prefs.edit()
            .putString("secret_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("secret_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("secret_failed_attempts", 0)
            .putLong("secret_lock_until", 0L)
            .commit()) { "Não foi possível configurar a senha secreta adicional." }
    }

    @Synchronized
    fun verifySecretAnswer(answer: CharArray): Boolean {
        if (!prefs.contains("secret_hash")) configureSecretAnswerIfMissing()
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("secret_lock_until", 0L)) {
            answer.fill(0.toChar())
            return false
        }
        val salt = Base64.decode(prefs.getString("secret_salt", "")!!, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("secret_hash", "")!!, Base64.NO_WRAP)
        val actual = derive(answer, salt)
        answer.fill(0.toChar())
        val ok = java.security.MessageDigest.isEqual(expected, actual)
        if (ok) {
            prefs.edit().putInt("secret_failed_attempts", 0).putLong("secret_lock_until", 0L).apply()
        } else {
            val attempts = prefs.getInt("secret_failed_attempts", 0) + 1
            val until = if (attempts >= 8) now + 30L * 60L * 1000L else 0L
            prefs.edit()
                .putInt("secret_failed_attempts", if (attempts >= 8) 0 else attempts)
                .putLong("secret_lock_until", until)
                .apply()
        }
        return ok
    }

    fun secretRemainingLockMillis(): Long =
        (prefs.getLong("secret_lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun remainingLockMillis(): Long = (prefs.getLong("lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
