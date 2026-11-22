package com.mysecret.app.backup

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKey
import javax.crypto.spec.PBEKeySpec
import com.mysecret.app.data.local.PrivateDao
import com.mysecret.app.security.PrivateMediaStore
import android.util.Base64

class EncryptedBackupManager(private val context: Context, private val dao: PrivateDao) {
    private val magic = "MYSECRET3".toByteArray(Charsets.US_ASCII)
    private val iterations = 310_000
    private val mediaStore = PrivateMediaStore(context)

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        require(password.size >= 10) { "A senha do backup deve ter pelo menos 10 caracteres." }
        val spec=PBEKeySpec(password,salt,iterations,256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        } finally { spec.clearPassword(); password.fill('\u0000') }
    }

    suspend fun exportTo(uri: Uri, password: CharArray) {
        val root=JSONObject().put("format",3).put("createdAt",System.currentTimeMillis())
        root.put("posts",JSONArray().apply {
            dao.allPosts().forEach { p -> put(JSONObject().put("title",p.title).put("body",p.body)
                .put("caption",p.caption).put("category",p.category).put("attachmentUris",p.attachmentUris)
                .put("createdAt",p.createdAt).put("updatedAt",p.updatedAt).put("favorite",p.favorite)) }
        })
        root.put("vault",JSONArray().apply {
            dao.allVaultItems().forEach { v -> put(JSONObject().put("title",v.title).put("content",v.content)
                .put("kind",v.kind).put("createdAt",v.createdAt).put("updatedAt",v.updatedAt)) }
        })
        root.put("albums",JSONArray().apply {
            dao.allAlbums().forEach { a -> put(JSONObject().put("name",a.name).put("description",a.description).put("createdAt",a.createdAt)) }
        })
        val mediaArray=JSONArray()
        for(m in dao.allMedia()) {
            val clearBytes=mediaStore.exportBytes(m.uri)
            require(clearBytes.size <= 50_000_000) { "Anexo muito grande para o backup." }
            mediaArray.put(JSONObject().put("oldRef",m.uri).put("displayName",m.displayName).put("mimeType",m.mimeType)
                .put("albumId",m.albumId).put("addedAt",m.addedAt)
                .put("bytes",Base64.encodeToString(clearBytes,Base64.NO_WRAP)))
            clearBytes.fill(0)
        }
        root.put("media",mediaArray)
        root.put("financeTransactions",JSONArray().apply {
            dao.allFinanceTransactions().forEach { t -> put(JSONObject().put("title",t.title).put("amountCents",t.amountCents)
                .put("type",t.type).put("category",t.category).put("occurredAt",t.occurredAt).put("note",t.note)) }
        })
        root.put("financeGoals",JSONArray().apply {
            dao.allFinanceGoals().forEach { g -> put(JSONObject().put("title",g.title).put("targetCents",g.targetCents)
                .put("savedCents",g.savedCents).put("dueAt",g.dueAt).put("createdAt",g.createdAt)) }
        })
        root.put("financeBills",JSONArray().apply {
            dao.allFinanceBills().forEach { b -> put(JSONObject().put("title",b.title).put("amountCents",b.amountCents)
                .put("dueAt",b.dueAt).put("paid",b.paid).put("category",b.category)) }
        })
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt))
        val encrypted=cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            DataOutputStream(raw).use { out ->
                out.write(magic); out.writeInt(iterations); out.writeInt(salt.size); out.write(salt)
                out.writeInt(cipher.iv.size); out.write(cipher.iv); out.writeInt(encrypted.size); out.write(encrypted)
            }
        } ?: error("Não foi possível criar o arquivo de backup.")
    }

    suspend fun importFrom(uri: Uri, password: CharArray) {
        val payload=context.contentResolver.openInputStream(uri)?.use { raw ->
            DataInputStream(raw).use { input ->
                val header=ByteArray(magic.size); input.readFully(header)
                require(header.contentEquals(magic)) { "Formato de backup não reconhecido." }
                val rounds=input.readInt(); require(rounds in 200_000..1_000_000) { "Parâmetros de backup inválidos." }
                val saltSize=input.readInt(); require(saltSize==16) { "Salt inválido." }
                val salt=ByteArray(saltSize); input.readFully(salt)
                val ivSize=input.readInt(); require(ivSize==12) { "Cabeçalho inválido." }
                val iv=ByteArray(ivSize); input.readFully(iv)
                val size=input.readInt(); require(size in 1..100_000_000) { "Arquivo de backup inválido ou grande demais." }
                val encrypted=ByteArray(size); input.readFully(encrypted)
                val spec=PBEKeySpec(password,salt,rounds,256)
                val secret=try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
                    finally { spec.clearPassword(); password.fill('\u0000') }
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,iv))
                cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            }
        } ?: error("Não foi possível abrir o backup.")
        val root=JSONObject(payload)
        require(root.optInt("format")==3) { "Versão de backup incompatível." }
        val posts=root.getJSONArray("posts")
        val vault=root.getJSONArray("vault")
        val albums=root.getJSONArray("albums")
        val media=root.optJSONArray("media") ?: JSONArray()
        val financeTransactions=root.optJSONArray("financeTransactions") ?: JSONArray()
        val financeGoals=root.optJSONArray("financeGoals") ?: JSONArray()
        val financeBills=root.optJSONArray("financeBills") ?: JSONArray()
        // Validate the complete payload before replacing the local data.
        for(i in 0 until posts.length()) require(posts.getJSONObject(i).has("title") && posts.getJSONObject(i).has("body"))
        for(i in 0 until vault.length()) require(vault.getJSONObject(i).has("title") && vault.getJSONObject(i).has("content"))
        for(i in 0 until albums.length()) require(albums.getJSONObject(i).has("name"))
        for(i in 0 until media.length()) {
            val encoded=media.getJSONObject(i).getString("bytes")
            val decoded=Base64.decode(encoded,Base64.NO_WRAP)
            require(decoded.size in 1..50_000_000) { "Anexo de backup inválido." }
            decoded.fill(0)
        }
        dao.clearPosts(); dao.clearVault(); dao.clearAlbums(); dao.clearMedia()
        dao.clearFinanceTransactions(); dao.clearFinanceGoals(); dao.clearFinanceBills()
        val mediaRefMap=mutableMapOf<String,String>()
        for(i in 0 until media.length()) {
            val m=media.getJSONObject(i)
            val clearBytes=Base64.decode(m.getString("bytes"),Base64.NO_WRAP)
            val privateReference=mediaStore.restoreBytes(clearBytes)
            clearBytes.fill(0)
            val oldRef=m.optString("oldRef")
            if(oldRef.isNotBlank()) mediaRefMap[oldRef]=privateReference
            dao.saveMedia(com.mysecret.app.data.local.MediaEntity(uri=privateReference,displayName=m.optString("displayName"),
                mimeType=m.optString("mimeType"),albumId=if(m.isNull("albumId")) null else m.optLong("albumId"),addedAt=m.optLong("addedAt")))
        }
        for(i in 0 until posts.length()) {
            val p=posts.getJSONObject(i)
            val remapped=p.optString("attachmentUris").split("|").filter { it.isNotBlank() }
                .map { mediaRefMap[it] ?: it }.joinToString("|")
            dao.savePost(com.mysecret.app.data.local.PostEntity(title=p.getString("title"),body=p.getString("body"),
                caption=p.optString("caption"),category=p.optString("category","Pessoal"),attachmentUris=remapped,
                createdAt=p.optLong("createdAt"),updatedAt=p.optLong("updatedAt"),favorite=p.optBoolean("favorite")))
        }
        for(i in 0 until vault.length()) {
            val v=vault.getJSONObject(i)
            dao.saveVaultItem(com.mysecret.app.data.local.VaultItemEntity(title=v.getString("title"),content=v.getString("content"),
                kind=v.optString("kind","Nota"),createdAt=v.optLong("createdAt"),updatedAt=v.optLong("updatedAt")))
        }
        for(i in 0 until albums.length()) {
            val a=albums.getJSONObject(i)
            dao.saveAlbum(com.mysecret.app.data.local.AlbumEntity(name=a.getString("name"),description=a.optString("description"),createdAt=a.optLong("createdAt")))
        }
        for(i in 0 until financeTransactions.length()) {
            val t=financeTransactions.getJSONObject(i)
            dao.saveFinanceTransaction(com.mysecret.app.data.local.FinanceTransactionEntity(title=t.getString("title"),amountCents=t.getLong("amountCents"),
                type=t.getString("type"),category=t.optString("category","Geral"),occurredAt=t.optLong("occurredAt"),note=t.optString("note")))
        }
        for(i in 0 until financeGoals.length()) {
            val g=financeGoals.getJSONObject(i)
            dao.saveFinanceGoal(com.mysecret.app.data.local.FinanceGoalEntity(title=g.getString("title"),targetCents=g.getLong("targetCents"),
                savedCents=g.optLong("savedCents"),dueAt=if(g.isNull("dueAt")) null else g.optLong("dueAt"),createdAt=g.optLong("createdAt")))
        }
        for(i in 0 until financeBills.length()) {
            val b=financeBills.getJSONObject(i)
            dao.saveFinanceBill(com.mysecret.app.data.local.FinanceBillEntity(title=b.getString("title"),amountCents=b.getLong("amountCents"),
                dueAt=b.getLong("dueAt"),paid=b.optBoolean("paid"),category=b.optString("category","Geral")))
        }
    }
}
