package com.mysecret.app.backup

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.mysecret.app.data.local.PrivateDao

class EncryptedBackupManager(private val context: Context, private val dao: PrivateDao) {
    private val alias = "my_secret_backup_key"
    private val magic = "MYSECRET1".toByteArray(Charsets.US_ASCII)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }

    suspend fun exportTo(uri: Uri) {
        val root = JSONObject()
        root.put("format", 1)
        root.put("posts", JSONArray().apply {
            dao.allPosts().forEach { p -> put(JSONObject()
                .put("title",p.title).put("body",p.body).put("caption",p.caption)
                .put("category",p.category).put("attachmentUris",p.attachmentUris)
                .put("createdAt",p.createdAt).put("updatedAt",p.updatedAt).put("favorite",p.favorite)) }
        })
        root.put("vault", JSONArray().apply {
            dao.allVaultItems().forEach { v -> put(JSONObject()
                .put("title",v.title).put("content",v.content).put("kind",v.kind)
                .put("createdAt",v.createdAt).put("updatedAt",v.updatedAt)) }
        })
        root.put("albums", JSONArray().apply {
            dao.allAlbums().forEach { a -> put(JSONObject()
                .put("name",a.name).put("description",a.description).put("createdAt",a.createdAt)) }
        })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            DataOutputStream(raw).use { out ->
                out.write(magic); out.writeInt(cipher.iv.size); out.write(cipher.iv)
                out.writeInt(encrypted.size); out.write(encrypted)
            }
        } ?: error("Não foi possível criar o arquivo de backup.")
    }

    suspend fun importFrom(uri: Uri) {
        val payload = context.contentResolver.openInputStream(uri)?.use { raw ->
            DataInputStream(raw).use { input ->
                val header = ByteArray(magic.size); input.readFully(header)
                require(header.contentEquals(magic)) { "Arquivo não é um backup MY SECRET válido." }
                val ivSize = input.readInt(); require(ivSize == 12) { "Cabeçalho de backup inválido." }
                val iv = ByteArray(ivSize); input.readFully(iv)
                val size = input.readInt(); require(size in 1..50_000_000) { "Backup muito grande ou inválido." }
                val encrypted = ByteArray(size); input.readFully(encrypted)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            }
        } ?: error("Não foi possível abrir o backup.")
        val root = JSONObject(payload)
        require(root.optInt("format") == 1) { "Versão de backup não suportada." }
        dao.clearPosts(); dao.clearVault(); dao.clearAlbums()
        val posts = root.getJSONArray("posts")
        for (i in 0 until posts.length()) {
            val p=posts.getJSONObject(i)
            dao.savePost(com.mysecret.app.data.local.PostEntity(
                title=p.getString("title"), body=p.getString("body"), caption=p.optString("caption"),
                category=p.optString("category","Pessoal"), attachmentUris=p.optString("attachmentUris"),
                createdAt=p.optLong("createdAt"), updatedAt=p.optLong("updatedAt"), favorite=p.optBoolean("favorite")))
        }
        val vault = root.getJSONArray("vault")
        for (i in 0 until vault.length()) {
            val v=vault.getJSONObject(i)
            dao.saveVaultItem(com.mysecret.app.data.local.VaultItemEntity(
                title=v.getString("title"),content=v.getString("content"),kind=v.optString("kind","Nota"),
                createdAt=v.optLong("createdAt"),updatedAt=v.optLong("updatedAt")))
        }
        val albums = root.getJSONArray("albums")
        for (i in 0 until albums.length()) {
            val a=albums.getJSONObject(i)
            dao.saveAlbum(com.mysecret.app.data.local.AlbumEntity(
                name=a.getString("name"),description=a.optString("description"),createdAt=a.optLong("createdAt")))
        }
    }
}
