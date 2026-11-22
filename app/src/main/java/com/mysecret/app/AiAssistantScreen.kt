package com.mysecret.app

import com.mysecret.app.data.local.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val AiPanel = Color(0xFF151A29)
private val AiAccent = Color(0xFF8B5CF6)
private val AiMuted = Color(0xFFA3ADC0)
private val AiWhite = Color(0xFFF7F7FB)

@Composable
fun AiAssistantScreen(
    posts: List<PostEntity>,
    media: List<MediaEntity>,
    vault: List<VaultItemEntity>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { AiApiSettings(context) }
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(settings.getKey()) }
    var prompt by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            "assistant" to "Olá! Sou a IA do secret. Configure uma API para conversar comigo. Não envie PIN, senhas ou códigos de recuperação."
        )
    }

    fun sendMessage() {
        val text = prompt.trim()
        if (text.isBlank() || sending) return
        if (apiKey.isBlank()) {
            showSettings = true
            status = "Configure sua API antes de enviar."
            return
        }
        prompt = ""
        messages.add("user" to text)
        sending = true
        status = ""
        scope.launch {
            try {
                val answer = OpenRouterClient.ask(apiKey, messages.map { it.first to it.second })
                messages.add("assistant" to answer)
            } catch (e: Exception) {
                messages.add("assistant" to (e.message ?: "Não foi possível conectar à API."))
            } finally {
                sending = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Assistente IA", color = AiWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(if (apiKey.isBlank()) "API não configurada" else "OpenRouter • modelo gratuito automático", color = AiMuted, fontSize = 11.sp)
            }
            OutlinedButton(onClick = { keyInput = ""; showSettings = true }) { Text("API") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = AiPanel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(11.dp)) {
                Text("Contexto local", color = AiAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "Este app tem \${posts.size} publicação(ões), \${media.size} arquivo(s) na galeria e \${vault.size} item(ns) no cofre. O conteúdo privado não é enviado automaticamente à API.",
                    color = AiMuted, fontSize = 11.sp
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { _, item ->
                val isUser = item.first == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF30234D) else AiPanel),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (isUser) "Você" else "secret IA", color = AiAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(item.second, color = AiWhite, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (sending) item { Text("A IA está respondendo…", color = AiMuted, fontSize = 12.sp) }
        }
        if (status.isNotBlank()) Text(status, color = AiMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                label = { Text("Digite sua mensagem") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() })
            )
            Button(
                enabled = !sending && prompt.isNotBlank(),
                onClick = { sendMessage() },
                modifier = Modifier.height(56.dp)
            ) { Text("Enviar") }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { if (!sending) showSettings = false },
            title = { Text("Configurar API da IA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Provedor: OpenRouter • API de chat", color = AiMuted, fontSize = 12.sp)
                    Text("Cole sua chave de API. Ela será salva criptografada neste aparelho.", color = AiMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Chave da API") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (apiKey.isNotBlank()) Text("Já existe uma chave salva neste aparelho.", color = Color(0xFF9BE7B0), fontSize = 11.sp)
                    Text("O uso gratuito tem limites e depende da disponibilidade dos modelos. Não compartilhe sua chave.", color = AiMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keyInput.trim().length < 12) {
                        status = "Cole uma chave de API válida."
                    } else {
                        try {
                            settings.saveKey(keyInput)
                            apiKey = settings.getKey()
                            keyInput = ""
                            showSettings = false
                            status = "Chave salva com criptografia neste aparelho."
                        } catch (e: Exception) {
                            status = e.message ?: "Não foi possível salvar a chave."
                        }
                    }
                }) { Text("Salvar chave") }
            },
            dismissButton = {
                Row {
                    if (apiKey.isNotBlank()) TextButton(onClick = {
                        settings.clearKey()
                        apiKey = ""
                        keyInput = ""
                        showSettings = false
                        status = "Chave removida do aparelho."
                    }) { Text("Remover") }
                    TextButton(onClick = { showSettings = false }) { Text("Fechar") }
                }
            }
        )
    }
}
