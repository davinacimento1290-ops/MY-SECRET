package com.mysecret.app.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class RecoveryCodeManager(context: Context) {
    private val prefs=context.getSharedPreferences("my_secret_recovery",Context.MODE_PRIVATE)

    fun createIfMissing(): String? {
        if(prefs.contains("recovery_hash")) return null
        val bytes=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val code=bytes.joinToString("") { "%02X".format(it) }
        val hash=MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
        check(prefs.edit().putString("recovery_hash",Base64.encodeToString(hash,Base64.NO_WRAP)).commit())
        return code.chunked(4).joinToString("-")
    }

    fun verify(code:String):Boolean {
        val normalized=code.filter { it.isLetterOrDigit() }.uppercase()
        if(normalized.length!=32) return false
        val expected=Base64.decode(prefs.getString("recovery_hash","") ?: return false,Base64.NO_WRAP)
        val actual=MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return MessageDigest.isEqual(expected,actual)
    }

    fun rotate():String {
        val bytes=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val code=bytes.joinToString("") { "%02X".format(it) }
        val hash=MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
        check(prefs.edit().putString("recovery_hash",Base64.encodeToString(hash,Base64.NO_WRAP)).commit())
        return code.chunked(4).joinToString("-")
    }
}
