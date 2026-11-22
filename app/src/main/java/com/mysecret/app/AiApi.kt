package com.mysecret.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiApiSettings(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secret_ai_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(): String = prefs.getString("openrouter_key", "").orEmpty()
    fun saveKey(value: String) {
        check(prefs.edit().putString("openrouter_key", value.trim()).commit()) {
            "Não foi possível salvar a chave da API."
        }
    }
    fun clearKey() { prefs.edit().remove("openrouter_key").apply() }
}

object OpenRouterClient {
    suspend fun ask(apiKey: String, messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Adicione sua chave da API nas configurações da IA." }
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 45000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/davinacimento1290-ops/MY-SECRET")
            connection.setRequestProperty("X-Title", "secret Android")
            val payload = JSONObject()
                .put("model", "openrouter/free")
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put(
                        "content",
                        "Você é o assistente do aplicativo secret. Responda em português brasileiro, com clareza e sem inventar dados. Você não controla o aparelho nem executa ações por conta própria. Quando o usuário pedir uma ação no app, explique que só pode realizá-la se existir uma função explícita no aplicativo. Nunca peça PIN, senha do backup ou códigos de recuperação."
                    ))
                    messages.takeLast(16).forEach { (role, content) ->
                        put(JSONObject().put("role", role).put("content", content))
                    }
                })
                .put("temperature", 0.6)
                .put("max_tokens", 900)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "A API respondeu com erro HTTP $status.")
            }
            val root = JSONObject(response)
            val answer = root.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            answer.takeIf { it.isNotBlank() } ?: "A IA não retornou uma resposta. Tente novamente."
        } finally {
            connection.disconnect()
        }
    }
}
