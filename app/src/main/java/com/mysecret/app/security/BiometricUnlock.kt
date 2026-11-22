package com.mysecret.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricUnlock(private val activity: FragmentActivity) {
    private val alias = "my_secret_biometric_unlock_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generator.generateKey()
    }

    fun canUse(): Boolean = try {
        val manager = androidx.biometric.BiometricManager.from(activity)
        manager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) { false }

    fun authenticate(onResult: (Boolean, String?) -> Unit) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticatedCipher = result.cryptoObject?.cipher
                                ?: throw IllegalStateException("Autenticação criptográfica ausente.")
                            val sealed = authenticatedCipher.doFinal("MY_SECRET_UNLOCK".toByteArray(Charsets.UTF_8))
                            activity.getSharedPreferences("my_secret_bio", 0).edit()
                                .putString("proof", android.util.Base64.encodeToString(authenticatedCipher.iv + sealed, android.util.Base64.NO_WRAP))
                                .apply()
                            onResult(true, null)
                        } catch (e: Exception) { onResult(false, e.message) }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onResult(false, errString.toString())
                    }
                    override fun onAuthenticationFailed() { }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Desbloquear MY SECRET")
                    .setSubtitle("Confirme sua identidade para abrir o espaço privado")
                    .setNegativeButtonText("Usar PIN")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build(),
                BiometricPrompt.CryptoObject(cipher)
            )
        } catch (e: Exception) { onResult(false, e.message) }
    }
}
