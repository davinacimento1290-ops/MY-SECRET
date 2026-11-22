package com.mysecret.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF090B12)
private val Panel = Color(0xFF151A27)
private val Accent = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9AA4B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MySecretApp() }
    }
}

@Composable
private fun MySecretApp() {
    var pin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Feed") }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent,
        background = Night,
        surface = Panel
    )) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            if (!unlocked) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("MY SECRET", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Seu espaço pessoal e privado", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text("Crie seu PIN inicial") },
                        supportingText = { Text("Configuração inicial demonstrativa — autenticação segura ainda será integrada.") },
                        singleLine = true
                    )
                    Button(
                        onClick = { if (pin.length >= 4) unlocked = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Continuar") }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("MY SECRET", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("Tudo o que é seu, em um só lugar.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Feed", "Galeria", "Cofre").forEach { item ->
                            FilterChip(selected == item, onClick = { selected = item }, label = { Text(item) })
                        }
                    }
                    when (selected) {
                        "Feed" -> {
                            Text("Seu espaço privado", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 18.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(18.dp)) {
                                    Text("Comece sua coleção", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Suas publicações, fotos e documentos aparecerão aqui.", color = Muted, modifier = Modifier.padding(top = 6.dp))
                                    Button(onClick = { }, modifier = Modifier.padding(top = 14.dp)) { Text("Nova publicação") }
                                }
                            }
                        }
                        "Galeria" -> EmptySection("Galeria", "Seus álbuns privados serão organizados aqui.")
                        else -> EmptySection("Cofre privado", "Notas, links, documentos e listas em um só lugar.")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Armazenamento local • MY SECRET", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun EmptySection(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Text(description, color = Muted, modifier = Modifier.padding(18.dp))
        }
    }
}
