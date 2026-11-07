package com.xilonalpha.xilonai

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class HubModel(val id: String, val downloads: Long, val likes: Long, val updated: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { XilonHome() } }
    }
}

@Composable
private fun XilonHome() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chat", "Modele", "Dispozitiv")
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { index, name ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Text((index + 1).toString()) },
                    label = { Text(name) }
                )
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("XILON AI", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            when (selected) {
                0 -> InstalledModelsScreen()
                1 -> ModelExplorer()
                else -> DeviceInfo()
            }
        }
    }
}

@Composable
private fun ModelExplorer() {
    var models by remember { mutableStateOf<List<HubModel>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var newest by remember { mutableStateOf(true) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<GgufCandidate>>(emptyList()) }
    var candidateError by remember { mutableStateOf<String?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    val cancelDownload = remember { AtomicBoolean(false) }
    val context = LocalContext.current
    fun refresh() {
        if (loading) return
        loading = true
        error = null
        thread(name = "hf-catalog") {
            try {
                val sort = if (newest) "lastModified" else "downloads"
                val endpoint = "https://huggingface.co/api/models?filter=gguf&sort=$sort&direction=-1&limit=50"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode != 200) throw java.io.IOException("HTTP ${connection.responseCode}")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    val parsed = (0 until array.length()).mapNotNull { i ->
                        val item = array.optJSONObject(i) ?: return@mapNotNull null
                        val id = item.optString("id")
                        if (id.isBlank()) null else HubModel(
                            id, item.optLong("downloads"), item.optLong("likes"),
                            item.optString("lastModified")
                        )
                    }
                    (context as ComponentActivity).runOnUiThread { models = parsed; loading = false }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                (context as ComponentActivity).runOnUiThread {
                    error = e.message ?: "Eroare de rețea"
                    loading = false
                }
            }
        }
    }
    LaunchedEffect(newest) { refresh() }
    Text("Catalog GGUF Hugging Face", style = MaterialTheme.typography.titleMedium)
    Text("Catalog online. Estimarea RAM este orientativă; suportul llama.cpp nu este verificat.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = newest, onClick = { newest = true }, label = { Text("Recente") })
        FilterChip(selected = !newest, onClick = { newest = false }, label = { Text("Populare") })
        TextButton(onClick = { refresh() }) { Text("Actualizează") }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text("Eroare: $it", color = MaterialTheme.colorScheme.error) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(models, key = { it.id }) { model ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(model.id, style = MaterialTheme.typography.titleSmall)
                    Text("Descărcări: ${model.downloads} · Aprecieri: ${model.likes}")
                    Text("Actualizat: ${model.updated.take(10)}")
                    Text("Compatibilitate: neverificată", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selectedModel = model.id
                        candidates = emptyList()
                        candidateError = null
                        inspecting = true
                        thread(name = "gguf-inspection") {
                            try {
                                val result = ModelCompatibility.candidates(context, model.id)
                                (context as ComponentActivity).runOnUiThread {
                                    if (selectedModel == model.id) { candidates = result; inspecting = false }
                                }
                            } catch (e: Exception) {
                                (context as ComponentActivity).runOnUiThread {
                                    if (selectedModel == model.id) { candidateError = e.message; inspecting = false }
                                }
                            }
                        }
                    }) { Text("Verifică fișierele GGUF") }
                    if (selectedModel == model.id) {
                        if (inspecting) CircularProgressIndicator()
                        candidateError?.let { Text("Eroare: $it") }
                        if (!inspecting && candidates.isEmpty() && candidateError == null) Text("Niciun fișier GGUF identificat.")
                        if (downloading) {
                            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { cancelDownload.set(true); downloadStatus = "Anulare solicitată..." }) { Text("Anulează descărcarea") }
                        }
                        if (downloadStatus.isNotBlank()) Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
                        candidates.take(12).forEach { file ->
                            HorizontalDivider()
                            Text(file.filename)
                            Text("Dimensiune: ${file.sizeBytes / (1024L * 1024L)} MiB; RAM estimat: ${file.estimatedRequiredRamBytes / (1024L * 1024L)} MiB")
                            Text("${file.status}: ${file.reason}", style = MaterialTheme.typography.bodySmall)
                            if (file.status == CompatibilityStatus.ESTIMATED && file.sizeBytes > 0) {
                                Button(enabled = !downloading, onClick = {
                                    downloading = true
                                    cancelDownload.set(false)
                                    downloadStatus = "Pornire descărcare..."
                                    downloadProgress = 0f
                                    thread(name = "gguf-download") {
                                        try {
                                            val result = GgufDownloader.download(context, model.id, file.filename, file.sizeBytes, expectedSha256 = file.expectedSha256, onProgress = { current, total ->
                                                (context as ComponentActivity).runOnUiThread {
                                                    downloadProgress = current.toFloat() / total.toFloat()
                                                    downloadStatus = "$current / $total bytes"
                                                }
                                            }, isCancelled = { cancelDownload.get() })
                                            (context as ComponentActivity).runOnUiThread {
                                                downloadStatus = "Salvat: ${result.file.name} (SHA-256: ${result.sha256.take(12)}...)"
                                                downloading = false
                                            }
                                        } catch (e: Exception) {
                                            (context as ComponentActivity).runOnUiThread {
                                                downloadStatus = "Eroare: ${e.message}"
                                                downloading = false
                                            }
                                        }
                                    }
                                }) { Text("Descarcă GGUF") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfo() {
    val context = LocalContext.current
    val manager = context.getSystemService(ActivityManager::class.java)
    val memory = remember { ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) } }
    val freeStorage = remember { StatFs(context.filesDir.absolutePath).availableBytes }
    Text("Producător: ${Build.MANUFACTURER}")
    Text("Model: ${Build.MODEL}")
    Text("Android: ${Build.VERSION.RELEASE}")
    Text("Arhitecturi: ${Build.SUPPORTED_ABIS.joinToString()}")
    Text("RAM total: ${memory.totalMem / (1024L * 1024L)} MiB")
    Text("RAM disponibil la deschidere: ${memory.availMem / (1024L * 1024L)} MiB")
    Text("Spațiu liber: ${freeStorage / (1024L * 1024L)} MiB")
}

@Composable
private fun InstalledModelsScreen() {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var activeModel by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val models = remember(revision) { InstalledModels.list(context) }
    Text("Modele instalate", style = MaterialTheme.typography.titleLarge)
    Text("Modelele GGUF complete pot fi încărcate local. Fișierele imatrix nu sunt modele de conversație.")
    OutlinedButton(onClick = { revision++ }) { Text("Reîmprospătează") }
    if (activeModel.isNotBlank()) {
        Text("Model activ: $activeModel")
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Mesaj") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && prompt.isNotBlank(), onClick = {
            busy = true
            val submitted = prompt
            val conversation = buildString {
                append("You are a helpful assistant.\\n")
                chatHistory.takeLast(4).forEach { (q, a) -> append("User: ").append(q).append("\\nAssistant: ").append(a).append("\\n") }
                append("User: ").append(submitted).append("\\nAssistant:")
            }
            thread(name = "local-inference") {
                val result = runCatching { LocalLlama.generate(conversation, 128) }
                (context as ComponentActivity).runOnUiThread {
                    response = result.getOrElse { "Eroare: ${it.message}" }
                    if (result.isSuccess) chatHistory = chatHistory + (submitted to response)
                    busy = false
                }
            }
        }) { Text("Generează offline") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(response)
        if (chatHistory.isNotEmpty()) {
            Text("Istoric conversație", style = MaterialTheme.typography.titleMedium)
            chatHistory.takeLast(6).forEach { (question, answer) ->
                Text("Tu: $question")
                Text("AI: $answer")
            }
        }
    }
    if (activeModel.isBlank() && response.isNotBlank()) Text(response)
    if (models.isEmpty()) {
        Text("Niciun model instalat. Deschide fila Modele pentru descărcare.")
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(models, key = { it.absolutePath }) { model ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleSmall)
                    Text("Dimensiune: ${model.length() / (1024L * 1024L)} MiB")
                    val header = remember(model.absolutePath) {
                        runCatching { NativeGgufProbe.inspect(model) }.getOrNull()
                    }
                    Text(if (header != null) "GGUF v${header.version} · ${header.tensorCount} tensori · ${header.metadataCount} metadate" else "GGUF: antet invalid sau bibliotecă nativă indisponibilă")
                    val auxiliary = model.name.contains("imatrix", ignoreCase = true) ||
                        model.name.contains("importance_matrix", ignoreCase = true)
                    if (auxiliary) Text("Fișier auxiliar imatrix: nu este un model AI executabil.", color = MaterialTheme.colorScheme.error)
                    Button(enabled = !busy && !auxiliary, onClick = {
                        busy = true
                        response = "Se încarcă modelul în memoria telefonului..."
                        thread(name = "llama-load") {
                            val attempt = runCatching { LocalLlama.loadModel(model) }
                            val loaded = attempt.getOrDefault(false)
                            (context as ComponentActivity).runOnUiThread {
                                activeModel = if (loaded) model.name else ""
                                chatHistory = emptyList()
                                response = if (loaded) "Model încărcat. Poți trimite un mesaj." else "Încărcarea a eșuat: ${attempt.exceptionOrNull()?.message ?: "format incompatibil, model incomplet sau memorie insuficientă"}."
                                busy = false
                            }
                        }
                    }) { Text("Încarcă pentru chat") }
                    TextButton(onClick = { confirmDelete = model.name }) { Text("Șterge modelul") }
                }
            }
        }
    }
    confirmDelete?.let { filename ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Ștergere model") },
            text = { Text("Ștergi definitiv fișierul $filename de pe telefon?") },
            confirmButton = {
                TextButton(onClick = {
                    if (filename == activeModel) {
                        LocalLlama.unload()
                        activeModel = ""
                        chatHistory = emptyList()
                    }
                    InstalledModels.delete(context, filename)
                    confirmDelete = null
                    revision++
                }) { Text("Șterge") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Anulează") } }
        )
    }
}
