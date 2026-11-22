package com.mysecret.app.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PrivateMediaStore(private val context: Context) {
    private val alias="my_secret_media_key_v1"
    private val folder=File(context.filesDir,"private_media").apply { mkdirs() }

    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    suspend fun importUri(uri:Uri):String=withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não foi possível ler o arquivo.")
        require(bytes.size in 1..50_000_000) { "O arquivo está vazio ou excede 50 MB." }
        encryptAndSave(bytes)
    }

    suspend fun restoreBytes(bytes:ByteArray):String=withContext(Dispatchers.IO) {
        require(bytes.size in 1..50_000_000) { "Anexo de backup inválido." }
        encryptAndSave(bytes)
    }

    private fun encryptAndSave(bytes:ByteArray):String {
        val id=UUID.randomUUID().toString()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(bytes)
        DataOutputStream(File(folder,"$id.mys").outputStream()).use { out ->
            out.writeInt(cipher.iv.size);out.write(cipher.iv);out.writeInt(encrypted.size);out.write(encrypted)
        }
        bytes.fill(0)
        return "securemedia://$id"
    }

    suspend fun readBytes(reference:String):ByteArray=withContext(Dispatchers.IO) {
        val id=reference.removePrefix("securemedia://")
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Referência de mídia inválida." }
        val file=File(folder,"$id.mys")
        require(file.isFile && file.length() in 17..50_000_100) { "Arquivo privado não encontrado." }
        DataInputStream(file.inputStream()).use { input ->
            val ivSize=input.readInt();require(ivSize==12)
            val iv=ByteArray(ivSize);input.readFully(iv)
            val size=input.readInt();require(size in 1..50_000_000)
            val encrypted=ByteArray(size);input.readFully(encrypted)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            cipher.doFinal(encrypted)
        }
    }

    suspend fun openForViewing(reference:String,displayName:String):Uri=withContext(Dispatchers.IO) {
        val bytes=readBytes(reference)
        val safeName=displayName.replace(Regex("[^A-Za-z0-9._-]"),"_").take(80).ifBlank { "arquivo" }
        val tempFolder=File(context.cacheDir,"opened_media").apply { mkdirs() }
        tempFolder.listFiles()?.forEach { it.delete() }
        val file=File(tempFolder,safeName)
        file.writeBytes(bytes);bytes.fill(0)
        FileProvider.getUriForFile(context,"com.mysecret.app.fileprovider",file)
    }

    suspend fun delete(reference:String)=withContext(Dispatchers.IO) {
        val id=reference.removePrefix("securemedia://")
        if(id.matches(Regex("[0-9a-fA-F-]{36}"))) File(folder,"$id.mys").delete()
    }

    suspend fun exportBytes(reference:String):ByteArray=readBytes(reference)
}
