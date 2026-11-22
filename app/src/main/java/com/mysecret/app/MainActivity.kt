package com.mysecret.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.app.backup.EncryptedBackupManager
import com.mysecret.app.data.local.*
import com.mysecret.app.data.repository.PrivateRepository
import com.mysecret.app.security.LocalAuthManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Night = Color(0xFF080A12)
private val Panel = Color(0xFF151A29)
private val Accent = Color(0xFF8B5CF6)
private val Muted = Color(0xFFA3ADC0)
private val White = Color(0xFFF7F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MySecretRoot() }
    }
}

@Composable
private fun MySecretRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { LocalAuthManager(context) }
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isSetup by remember { mutableStateOf(!auth.isConfigured()) }
    MaterialTheme(colorScheme = darkColorScheme(primary=Accent, background=Night, surface=Panel)) {
        Surface(Modifier.fillMaxSize(), color=Night) {
            if (!unlocked) {
                Column(Modifier.fillMaxSize().padding(26.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                    Text("MY SECRET", color=White, fontSize=34.sp, fontWeight=FontWeight.Black)
                    Text(if (isSetup) "Crie seu acesso privado" else "Seu espaço pessoal", color=Muted, modifier=Modifier.padding(top=8.dp,bottom=28.dp))
                    OutlinedTextField(value=pin,onValueChange={if(it.length<=12 && it.all(Char::isDigit)) pin=it},label={Text("PIN de 4 a 12 números")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                    if(isSetup) {
                        OutlinedTextField(value=confirmPin,onValueChange={if(it.length<=12 && it.all(Char::isDigit)) confirmPin=it},label={Text("Confirme o PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.padding(top=10.dp))
                    }
                    if(error.isNotBlank()) Text(error,color=Color(0xFFFF8585),modifier=Modifier.padding(top=10.dp))
                    Button(onClick={
                        try {
                            if(isSetup) {
                                if(pin.length !in 4..12 || pin!=confirmPin) error="Confira se os PINs são iguais e têm 4 a 12 números."
                                else { auth.configurePin(pin.toCharArray()); isSetup=false; unlocked=true; error="" }
                            } else {
                                if(auth.verifyPin(pin.toCharArray())) { unlocked=true; error="" }
                                else error="PIN incorreto ou limite de tentativas atingido."
                            }
                            pin=""; confirmPin=""
                        } catch(e:Exception) { error=e.message ?: "Não foi possível configurar o acesso." }
                    },modifier=Modifier.fillMaxWidth().padding(top=18.dp),shape=RoundedCornerShape(14.dp)) { Text(if(isSetup) "Criar acesso" else "Desbloquear") }
                    Text("Os dados ficam no armazenamento local do aparelho.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=20.dp))
                }
            } else {
                PrivateWorkspace(onLock={unlocked=false;pin=""})
            }
        }
    }
}

@Composable
private fun PrivateWorkspace(onLock: () -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val database=remember { PrivateDatabase.get(context) }
    val repository=remember { PrivateRepository(database.privateDao()) }
    val posts by repository.posts.collectAsStateWithLifecycle(initialValue=emptyList())
    val favorites by repository.favorites.collectAsStateWithLifecycle(initialValue=emptyList())
    val vault by repository.vault.collectAsStateWithLifecycle(initialValue=emptyList())
    val albums by repository.albums.collectAsStateWithLifecycle(initialValue=emptyList())
    val scope= rememberCoroutineScope()
    var tab by remember { mutableStateOf("Feed") }
    var search by remember { mutableStateOf("") }
    val searched by repository.search(search).collectAsStateWithLifecycle(initialValue=emptyList())
    var showPost by remember { mutableStateOf(false) }
    var showVault by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var backupManager by remember { mutableStateOf<EncryptedBackupManager?>(null) }
    LaunchedEffect(database) { backupManager=EncryptedBackupManager(context,database.privateDao()) }

    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text("MY SECRET",color=White,fontSize=26.sp,fontWeight=FontWeight.Black)
                Text("Seu espaço privado",color=Muted,fontSize=12.sp)
            }
            TextButton(onClick=onLock) { Text("Bloquear") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Feed","Favoritos","Galeria","Cofre","Ajustes").forEach { item ->
                FilterChip(selected=tab==item,onClick={tab=item},label={Text(item,fontSize=11.sp)})
            }
        }
        if(tab=="Feed" || tab=="Favoritos") {
            OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth().padding(top=10.dp),singleLine=true,label={Text("Pesquisar no seu conteúdo")})
        }
        if(notice.isNotBlank()) {
            Text(notice,color=Color(0xFF9BE7B0),fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
        }
        when(tab) {
            "Feed" -> {
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Publicações",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Button(onClick={showPost=true}) { Text("Nova +") }
                }
                val list=if(search.isBlank()) posts else searched
                PostList(list,repository,scope,{notice=it})
            }
            "Favoritos" -> {
                Text("Seus favoritos",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=14.dp))
                val list=(if(search.isBlank()) favorites else searched.filter { it.favorite })
                PostList(list,repository,scope,{notice=it})
            }
            "Galeria" -> {
                Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Álbuns",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Button(onClick={showAlbum=true}) { Text("Novo álbum") }
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(albums.isEmpty()) item { EmptyCard("Sua galeria começa aqui","Crie álbuns para organizar suas coleções.") }
                    items(albums,key={it.id}) { album -> Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("▣  "+album.name,color=White,fontWeight=FontWeight.Bold)
                            if(album.description.isNotBlank()) Text(album.description,color=Muted,modifier=Modifier.padding(top=4.dp))
                            Text(DateFormat.getDateInstance().format(Date(album.createdAt)),color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
                        }
                    } }
                }
            }
            "Cofre" -> {
                Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Cofre privado",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Button(onClick={showVault=true}) { Text("Adicionar +") }
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(vault.isEmpty()) item { EmptyCard("Seu cofre está vazio","Guarde notas, links, informações e listas.") }
                    items(vault,key={it.id}) { item ->
                        Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(item.title,color=White,fontWeight=FontWeight.Bold)
                                    TextButton(onClick={scope.launch { repository.deleteVault(item); notice="Item excluído." }}) { Text("Excluir") }
                                }
                                Text(item.kind,color=Accent,fontSize=11.sp)
                                Text(item.content,color=Muted,modifier=Modifier.padding(top=8.dp))
                            }
                        }
                    }
                }
            }
            else -> {
                Text("Configurações",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=16.dp))
                Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Segurança local",color=White,fontWeight=FontWeight.Bold)
                        Text("PIN protegido por PBKDF2. Banco de dados criptografado com SQLCipher e chave protegida pelo Android Keystore.",color=Muted,modifier=Modifier.padding(top=6.dp))
                        Text("Biometria e recuperação segura ainda não foram integradas.",color=Color(0xFFFFCC80),modifier=Modifier.padding(top=8.dp),fontSize=12.sp)
                    }
                }
                Text("Backup criptografado",color=White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=8.dp))
                Text("Nesta versão, o backup só pode ser restaurado no aparelho que possui a chave de backup original.",color=Muted,fontSize=12.sp)
                Button(onClick={notice="Use o seletor de arquivos na próxima atualização para exportar o backup."},modifier=Modifier.padding(top=8.dp)) { Text("Exportar backup — em integração") }
                TextButton(onClick=onLock) { Text("Bloquear aplicativo") }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Armazenamento local • MY SECRET",color=Muted,fontSize=11.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=10.dp))
    }
    if(showPost) PostDialog(onDismiss={showPost=false},onSave={title,body,category,caption->
        scope.launch { try { repository.addPost(title,body,category,caption); notice="Publicação salva."; } catch(e:Exception) { notice=e.message ?: "Falha ao salvar." } }
        showPost=false
    })
    if(showVault) VaultDialog(onDismiss={showVault=false},onSave={title,body,kind->
        scope.launch { try { repository.addVaultItem(title,body,kind); notice="Item salvo no cofre."; } catch(e:Exception) { notice=e.message ?: "Falha ao salvar." } }
        showVault=false
    })
    if(showAlbum) AlbumDialog(onDismiss={showAlbum=false},onSave={name,description->
        scope.launch { try { repository.addAlbum(name,description); notice="Álbum criado."; } catch(e:Exception) { notice=e.message ?: "Falha ao criar álbum." } }
        showAlbum=false
    })
}

@Composable
private fun PostList(list:List<PostEntity>,repository:PrivateRepository,scope:kotlinx.coroutines.CoroutineScope,onNotice:(String)->Unit) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(list.isEmpty()) item { EmptyCard("Nada por aqui ainda","Crie uma publicação ou ajuste sua pesquisa.") }
        items(list,key={it.id}) { post ->
            Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(post.title,color=White,fontWeight=FontWeight.Bold)
                        TextButton(onClick={scope.launch { repository.toggleFavorite(post); onNotice(if(post.favorite) "Removido dos favoritos." else "Adicionado aos favoritos.") }}) { Text(if(post.favorite) "★" else "☆",fontSize=20.sp) }
                    }
                    Text(post.category,color=Accent,fontSize=11.sp)
                    if(post.body.isNotBlank()) Text(post.body,color=Muted,modifier=Modifier.padding(top=8.dp))
                    if(post.caption.isNotBlank()) Text(post.caption,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(post.createdAt)),color=Muted,fontSize=10.sp)
                        TextButton(onClick={scope.launch { repository.deletePost(post); onNotice("Publicação excluída.") }}) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(title:String,description:String) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title,color=White,fontWeight=FontWeight.Bold)
            Text(description,color=Muted,modifier=Modifier.padding(top=6.dp))
        }
    }
}

@Composable
private fun PostDialog(onDismiss:()->Unit,onSave:(String,String,String,String)->Unit) {
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pessoal") }; var caption by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nova publicação")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Título")},singleLine=true)
            OutlinedTextField(body,{body=it},label={Text("Texto ou conteúdo")},minLines=3)
            OutlinedTextField(caption,{caption=it},label={Text("Legenda (opcional)")})
            OutlinedTextField(category,{category=it},label={Text("Categoria")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={onSave(title,body,category,caption)}) { Text("Salvar") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar") }})
}

@Composable
private fun VaultDialog(onDismiss:()->Unit,onSave:(String,String,String)->Unit) {
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var kind by remember { mutableStateOf("Nota") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Novo item do cofre")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Título")},singleLine=true)
            OutlinedTextField(kind,{kind=it},label={Text("Tipo: nota, link, documento...")},singleLine=true)
            OutlinedTextField(body,{body=it},label={Text("Conteúdo")},minLines=3)
        }
    },confirmButton={TextButton(onClick={onSave(title,body,kind)}) { Text("Salvar") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar") }})
}

@Composable
private fun AlbumDialog(onDismiss:()->Unit,onSave:(String,String)->Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Criar álbum")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true)
            OutlinedTextField(description,{description=it},label={Text("Descrição")})
        }
    },confirmButton={TextButton(onClick={onSave(name,description)}) { Text("Criar") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar") }})
}
