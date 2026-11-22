package com.mysecret.app

import android.os.Bundle
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mysecret.app.security.BiometricUnlock
import com.mysecret.app.security.RecoveryCodeManager
import com.mysecret.app.security.PrivateMediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MySecretRoot() }
    }
}

@Composable
private fun MySecretRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { LocalAuthManager(context).apply { configureSecretAnswerIfMissing() } }
    val recovery = remember { RecoveryCodeManager(context) }
    val biometric = remember { BiometricUnlock(context as FragmentActivity) }
    var recoveryCode by remember { mutableStateOf(recovery.createIfMissing()) }
    var recoverMode by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var pendingSecondFactor by remember { mutableStateOf(false) }
    var secretAnswer by remember { mutableStateOf("") }
    var backgroundAt by remember { mutableLongStateOf(0L) }
    var externalPickerActive by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundAt = if (externalPickerActive) 0L else System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    if (!externalPickerActive && backgroundAt > 0L) {
                        if (System.currentTimeMillis() - backgroundAt >= 5L * 60L * 1000L) {
                            unlocked = false
                            pendingSecondFactor = false
                            secretAnswer = ""
                        }
                        backgroundAt = 0L
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isSetup by remember { mutableStateOf(!auth.isConfigured()) }
    MaterialTheme(colorScheme = darkColorScheme(primary=Accent, background=Night, surface=Panel)) {
        Surface(Modifier.fillMaxSize(), color=Night) {
            if (!unlocked) {
                Column(Modifier.fillMaxSize().padding(26.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                    Text("secret", color=White, fontSize=34.sp, fontWeight=FontWeight.Black)
                    Text(if (isSetup) "Crie seu acesso privado" else "Seu espaço pessoal", color=Muted, modifier=Modifier.padding(top=8.dp,bottom=28.dp))
                    if (pendingSecondFactor) {
                        Text("VERIFICAÇÃO ADICIONAL",color=Accent,fontSize=13.sp,fontWeight=FontWeight.Bold)
                        Text("Quantos anos você tem?",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))
                        OutlinedTextField(
                            value=secretAnswer,
                            onValueChange={if(it.length<=8 && it.all(Char::isDigit)) secretAnswer=it},
                            label={Text("Resposta secreta")},
                            singleLine=true,
                            visualTransformation=PasswordVisualTransformation(),
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
                            modifier=Modifier.fillMaxWidth().padding(top=10.dp)
                        )
                        if(error.isNotBlank()) Text(error,color=Color(0xFFFF8585),modifier=Modifier.padding(top=10.dp))
                        Button(onClick={
                            if (auth.verifySecretAnswer(secretAnswer.toCharArray())) {
                                unlocked=true
                                pendingSecondFactor=false
                                secretAnswer=""
                                error=""
                            } else {
                                secretAnswer=""
                                error=if(auth.secretRemainingLockMillis()>0L) "Senha secreta bloqueada temporariamente. Tente mais tarde." else "Resposta secreta incorreta."
                            }
                        },modifier=Modifier.fillMaxWidth().padding(top=18.dp),shape=RoundedCornerShape(14.dp)) { Text("Confirmar resposta") }
                        TextButton(onClick={pendingSecondFactor=false;secretAnswer="";pin="";error=""}) { Text("Voltar ao login") }
                    } else if(!recoverMode) {
                    OutlinedTextField(value=pin,onValueChange={if(it.length<=12 && it.all(Char::isDigit)) pin=it},label={Text("PIN de 4 a 12 números")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                    if(isSetup) {
                        OutlinedTextField(value=confirmPin,onValueChange={if(it.length<=12 && it.all(Char::isDigit)) confirmPin=it},label={Text("Confirme o PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.padding(top=10.dp))
                    }
                    if(error.isNotBlank()) Text(error,color=Color(0xFFFF8585),modifier=Modifier.padding(top=10.dp))
                    } else {
                        OutlinedTextField(value=recoveryInput,onValueChange={recoveryInput=it},label={Text("Código de recuperação")},singleLine=true)
                        OutlinedTextField(value=newPin,onValueChange={if(it.length<=12 && it.all(Char::isDigit)) newPin=it},label={Text("Novo PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                        Button(onClick={
                            if(recovery.verify(recoveryInput) && newPin.length in 4..12) {
                                auth.configurePin(newPin.toCharArray()); recoveryCode=recovery.rotate()
                                recoveryInput="";newPin="";recoverMode=false;unlocked=false;error="Novo PIN criado. Guarde seu novo código de recuperação."
                            } else error="Código inválido ou PIN fora do limite."
                        },modifier=Modifier.fillMaxWidth().padding(top=8.dp)) { Text("Redefinir PIN") }
                        TextButton(onClick={recoverMode=false}) { Text("Voltar") }
                    }
                    if (!pendingSecondFactor && !recoverMode) Button(onClick={
                        try {
                            if(isSetup) {
                                if(pin.length !in 4..12 || pin!=confirmPin) error="Confira se os PINs são iguais e têm 4 a 12 números."
                                else { auth.configurePin(pin.toCharArray()); isSetup=false; unlocked=false; error="Guarde o código de recuperação antes de entrar." }
                            } else {
                                if(auth.verifyPin(pin.toCharArray())) { pendingSecondFactor=true; error="" }
                                else error="PIN incorreto ou limite de tentativas atingido."
                            }
                            pin=""; confirmPin=""
                        } catch(e:Exception) { error=e.message ?: "Não foi possível configurar o acesso." }
                    },modifier=Modifier.fillMaxWidth().padding(top=18.dp),shape=RoundedCornerShape(14.dp)) { Text(if(isSetup) "Criar acesso" else "Desbloquear") }
                    if(!isSetup && !recoverMode && !pendingSecondFactor && biometric.canUse()) OutlinedButton(onClick={
                        biometric.authenticate { ok, msg -> if(ok) { pendingSecondFactor=true; error="" } else if(!msg.isNullOrBlank() && !msg.contains("cancel",true)) error=msg }
                    },modifier=Modifier.fillMaxWidth().padding(top=8.dp)) { Text("Desbloquear com biometria") }
                    if(!isSetup && !recoverMode && !pendingSecondFactor) TextButton(onClick={recoverMode=true;error=""}) { Text("Esqueci meu PIN") }
                    if(recoveryCode!=null && !pendingSecondFactor) Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("CÓDIGO DE RECUPERAÇÃO — GUARDE EM LOCAL SEGURO",color=Color(0xFFFFD180),fontSize=11.sp,fontWeight=FontWeight.Bold)
                            Text(recoveryCode!!,color=White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=6.dp))
                            TextButton(onClick={recoveryCode=null}) { Text("Já guardei") }
                        }
                    }
                    Text("Os dados ficam no armazenamento local do aparelho.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=20.dp))
                }
            } else {
                PrivateWorkspace(
                    onLock={unlocked=false;pin="";pendingSecondFactor=false;secretAnswer=""},
                    onExternalPickerState={ externalPickerActive = it }
                )
            }
        }
    }
}

@Composable
private fun PrivateWorkspace(onLock: () -> Unit, onExternalPickerState: (Boolean) -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val database=remember { PrivateDatabase.get(context) }
    val repository=remember { PrivateRepository(database.privateDao()) }
    val mediaStore=remember { PrivateMediaStore(context) }
    val posts by repository.posts.collectAsStateWithLifecycle(initialValue=emptyList())
    val favorites by repository.favorites.collectAsStateWithLifecycle(initialValue=emptyList())
    val vault by repository.vault.collectAsStateWithLifecycle(initialValue=emptyList())
    val albums by repository.albums.collectAsStateWithLifecycle(initialValue=emptyList())
    val media by repository.media.collectAsStateWithLifecycle(initialValue=emptyList())
    val scope= rememberCoroutineScope()
    var tab by remember { mutableStateOf("Feed") }
    var search by remember { mutableStateOf("") }
    val searched by repository.search(search).collectAsStateWithLifecycle(initialValue=emptyList())
    var showPost by remember { mutableStateOf(false) }
    var showVault by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var backupManager by remember { mutableStateOf<EncryptedBackupManager?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var backupMode by remember { mutableStateOf("") }
    LaunchedEffect(database) { backupManager=EncryptedBackupManager(context,database.privateDao()) }
    val mediaPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onExternalPickerState(false)
        uris.forEach { uri ->
            try {
                try { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) { }
                val displayName=context.contentResolver.query(uri,null,null,null,null)?.use { cursor ->
                    val index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(index>=0 && cursor.moveToFirst()) cursor.getString(index) else "Arquivo"
                } ?: "Arquivo"
                val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
                scope.launch { try { repository.addMedia(mediaStore.importUri(uri),displayName,mime);notice="Arquivo adicionado à galeria." } catch(e:Exception) { notice=e.message ?: "Falha ao adicionar." } }
            } catch(e:Exception) { notice="Não foi possível manter acesso ao arquivo." }
        }
    }
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        onExternalPickerState(false)
        if(uri!=null) scope.launch { try { backupManager?.exportTo(uri,backupPassword.toCharArray()); notice="Backup criptografado exportado." } catch(e:Exception) { notice=e.message ?: "Falha ao exportar backup." };backupPassword="";backupPasswordConfirm="" }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onExternalPickerState(false)
        if(uri!=null) { restoreUri=uri; confirmRestore=true }
    }

    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text("secret",color=White,fontSize=26.sp,fontWeight=FontWeight.Black)
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
                PostList(list,repository,scope,mediaStore,{notice=it})
            }
            "Favoritos" -> {
                Text("Seus favoritos",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=14.dp))
                val list=(if(search.isBlank()) favorites else searched.filter { it.favorite })
                PostList(list,repository,scope,mediaStore,{notice=it})
            }
            "Galeria" -> {
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Galeria privada",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Button(onClick={onExternalPickerState(true);mediaPicker.launch(arrayOf("*/*"))}) { Text("Adicionar") }
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item { Text("Álbuns",color=Accent,fontWeight=FontWeight.Bold) }
                    if(albums.isEmpty()) item { EmptyCard("Sem álbuns","Crie um álbum para organizar sua coleção.") }
                    items(albums,key={it.id}) { album -> Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel)) {
                        Column(Modifier.padding(14.dp)) { Text(album.name,color=White,fontWeight=FontWeight.Bold);Text(album.description,color=Muted) }
                    } }
                    item { Text("Fotos, vídeos e documentos",color=Accent,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp)) }
                    if(media.isEmpty()) item { EmptyCard("Galeria vazia","Adicione arquivos usando o seletor seguro do Android.") }
                    items(media,key={it.id+1000000L}) { item -> Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel)) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(item.displayName,color=White,fontWeight=FontWeight.Bold);Text(item.mimeType,color=Muted,fontSize=11.sp) }
                            TextButton(onClick={scope.launch { try { val preview=mediaStore.openForViewing(item.uri,item.displayName);context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(preview,item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch(e:Exception) { notice=e.message ?: "Não foi possível abrir o arquivo." } }}) { Text("Abrir") }
                            TextButton(onClick={scope.launch { try { mediaStore.delete(item.uri);repository.deleteMedia(item);notice="Arquivo privado excluído." } catch(e:Exception) { notice="Falha ao excluir arquivo." } }}) { Text("Excluir") }
                        }
                    } }
                    item { OutlinedButton(onClick={showAlbum=true},modifier=Modifier.fillMaxWidth()) { Text("Criar álbum") } }
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
                        Text("Biometria vinculada à operação criptográfica, código de recuperação e bloqueio automático ao sair do app.",color=Muted,modifier=Modifier.padding(top=8.dp),fontSize=12.sp)
                    }
                }
                Text("Backup criptografado",color=White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=8.dp))
                Text("Backup AES-GCM protegido por senha definida por você; pode ser restaurado em outro aparelho.",color=Muted,fontSize=12.sp)
                Button(onClick={backupMode="export";backupPassword="";backupPasswordConfirm=""},modifier=Modifier.padding(top=8.dp)) { Text("Exportar backup") }
                OutlinedButton(onClick={backupMode="import";backupPassword="";onExternalPickerState(true);importLauncher.launch(arrayOf("application/octet-stream","application/*"))},modifier=Modifier.padding(top=8.dp)) { Text("Restaurar backup") }
                TextButton(onClick=onLock) { Text("Bloquear aplicativo") }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Armazenamento local • secret",color=Muted,fontSize=11.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=10.dp))
    }
    if(backupMode=="export") PasswordDialog("Criar senha do backup",backupPassword,backupPasswordConfirm,{backupPassword=it},{backupPasswordConfirm=it},{
        if(backupPassword.length<10 || backupPassword!=backupPasswordConfirm) notice="As senhas devem ser iguais e ter pelo menos 10 caracteres."
        else { backupMode="";onExternalPickerState(true);exportLauncher.launch("secret_backup.mys") }
    },{backupMode=""})
    if(backupMode=="import" && confirmRestore && restoreUri!=null) PasswordDialog("Senha do backup",backupPassword,backupPassword,{backupPassword=it},{backupPassword=it},{
        val uri=restoreUri;confirmRestore=false;restoreUri=null;backupMode=""
        if(uri!=null) scope.launch { try { backupManager?.importFrom(uri,backupPassword.toCharArray());notice="Backup restaurado." } catch(e:Exception) { notice=e.message ?: "Senha incorreta ou backup inválido." };backupPassword="" }
    },{backupMode="";confirmRestore=false;restoreUri=null})
    if(showPost) PostDialog(onDismiss={showPost=false},onExternalPickerState=onExternalPickerState,onSave={title,body,category,caption,attachments->
        scope.launch { try {
            val secureRefs=attachments.split("|").filter { it.isNotBlank() }.map { raw ->
                val uri=Uri.parse(raw)
                val name=context.contentResolver.query(uri,null,null,null,null)?.use { cursor ->
                    val index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(index>=0 && cursor.moveToFirst()) cursor.getString(index) else "Anexo"
                } ?: "Anexo"
                val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
                val secure=mediaStore.importUri(uri)
                repository.addMedia(secure,name,mime)
                secure
            }
            repository.addPost(title,body,category,caption,secureRefs.joinToString("|"));notice="Publicação salva com anexos criptografados."
        } catch(e:Exception) { notice=e.message ?: "Falha ao salvar." } }
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
private fun PostList(list:List<PostEntity>,repository:PrivateRepository,scope:kotlinx.coroutines.CoroutineScope,mediaStore:PrivateMediaStore,onNotice:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
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
                    val attached=post.attachmentUris.split("|").filter { it.isNotBlank() }
                    attached.forEachIndexed { index, rawUri ->
                        TextButton(onClick={scope.launch {
                            try {
                                val uri=mediaStore.openForViewing(rawUri,"anexo")
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch(e:Exception) { onNotice("Não foi possível abrir o anexo.") }
                        }}) { Text("Abrir anexo ${index+1}") }
                    }
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
private fun PostDialog(onDismiss:()->Unit,onExternalPickerState:(Boolean)->Unit,onSave:(String,String,String,String,String)->Unit) {
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pessoal") }; var caption by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<android.net.Uri>()) }
    val context=androidx.compose.ui.platform.LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        onExternalPickerState(false)
        selected.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) { /* O acesso temporário ainda permite copiar o arquivo ao salvar. */ } }
        attachments=selected
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nova publicação")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Título")},singleLine=true)
            OutlinedTextField(body,{body=it},label={Text("Texto ou conteúdo")},minLines=3)
            OutlinedTextField(caption,{caption=it},label={Text("Legenda (opcional)")})
            OutlinedTextField(category,{category=it},label={Text("Categoria")},singleLine=true)
            OutlinedButton(onClick={onExternalPickerState(true);picker.launch(arrayOf("*/*"))}) { Text("Anexar fotos/documentos (${attachments.size})") }
        }
    },confirmButton={TextButton(onClick={onSave(title,body,category,caption,attachments.joinToString("|") { it.toString() })}) { Text("Salvar") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar") }})
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

@Composable
private fun PasswordDialog(title:String,password:String,confirmation:String,onPassword:(String)->Unit,onConfirmation:(String)->Unit,onConfirm:()->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(password,onPassword,label={Text("Senha (mínimo 10 caracteres)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        if(title.contains("Criar")) OutlinedTextField(confirmation,onConfirmation,label={Text("Confirme a senha")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
    }},confirmButton={TextButton(onClick=onConfirm) { Text(if(title.contains("Criar")) "Continuar" else "Restaurar") }},dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar") }})
}
