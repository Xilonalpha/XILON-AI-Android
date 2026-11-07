package com.xilonalpha.xilonai

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
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
        setContent { XilonTheme { XilonHome() } }
    }
}

private val XilonPurple = Color(0xFF7657F7)
private val XilonDark = Color(0xFF111327)
private val XilonSurface = Color(0xFF1D2038)

@Composable
private fun XilonTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color(0xFFB9A7FF),
        onPrimary = Color(0xFF211451),
        background = XilonDark,
        onBackground = Color(0xFFF5F3FF),
        surface = XilonSurface,
        onSurface = Color(0xFFF5F3FF),
        surfaceVariant = Color(0xFF2A2D49),
        onSurfaceVariant = Color(0xFFC5C4D8),
        secondary = Color(0xFF75E0E6)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun XilonHome() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chat", "Modele", "Dispozitiv")
    val icons = listOf(Icons.Filled.ChatBubbleOutline, Icons.Filled.Memory, Icons.Filled.PhoneAndroid)
    Scaffold(containerColor = XilonDark, bottomBar = {
        NavigationBar(containerColor = XilonSurface) {
            tabs.forEachIndexed { index, name ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = { Icon(icons[index], contentDescription = name) },
                    label = { Text(name) }
                )
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF4733A4), Color(0xFF1D315C), XilonDark))
                ).padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF92F4F1),
                            modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                    Column {
                        Text("XILON AI", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text("Inteligență locală. Fără server AI.", color = Color(0xFFD5D0FF),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    when (selected) {
                        0 -> "Spațiul tău de conversație"
                        1 -> "Descoperă modele AI"
                        else -> "Capabilitățile dispozitivului"
                    },
                    style = MaterialTheme.typography.titleMedium, color = Color.White
                )
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                when (selected) {
                    0 -> InstalledModelsScreen()
                    1 -> ModelExplorer()
                    else -> DeviceInfo()
                }
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
    var searchText by remember { mutableStateOf("") }
    var hubQuery by remember { mutableStateOf("") }
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
                val encodedQuery = java.net.URLEncoder.encode(hubQuery.trim(), "UTF-8")
                val queryPart = if (encodedQuery.isBlank()) "" else "&search=$encodedQuery"
                val endpoint = "https://huggingface.co/api/models?filter=gguf&sort=$sort&direction=-1&limit=100$queryPart"
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
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        label = { Text("Caută modele pe Hugging Face") },
        placeholder = { Text("Ex: Qwen, Llama, Gemma") }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !loading, onClick = { hubQuery = searchText.trim(); refresh() }) {
            Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text("Caută online")
        }
        if (hubQuery.isNotBlank()) TextButton(enabled = !loading, onClick = {
            searchText = ""; hubQuery = ""; refresh()
        }) { Text("Resetează") }
    }
    Text("Rezultate Hugging Face pentru: ${hubQuery.ifBlank { "catalog general" }}. Compatibilitatea este estimată.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = newest, onClick = { newest = true }, label = { Text("Recente") })
        FilterChip(selected = !newest, onClick = { newest = false }, label = { Text("Populare") })
        TextButton(onClick = { refresh() }) { Text("Actualizează") }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text("Eroare: $it", color = MaterialTheme.colorScheme.error) }
    val visibleModels = models
    Text("${visibleModels.size} rezultate primite de la Hugging Face (maximum 100 per cerere)", style = MaterialTheme.typography.bodySmall)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visibleModels, key = { it.id }) { model ->
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
                    }) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text("Verifică fișierele GGUF") }
                    if (selectedModel == model.id) {
                        if (inspecting) CircularProgressIndicator()
                        candidateError?.let { Text("Eroare: $it") }
                        if (!inspecting && candidates.isEmpty() && candidateError == null) Text("Niciun fișier GGUF identificat.")
                        if (downloading) {
                            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { cancelDownload.set(true); downloadStatus = "Anulare solicitată..." }) { Text("Anulează descărcarea") }
                        }
                        if (downloadStatus.isNotBlank()) Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
                        val memoryManager = context.getSystemService(ActivityManager::class.java)
                        val availableRam = ActivityManager.MemoryInfo().also { memoryManager.getMemoryInfo(it) }.availMem
                        val recommendations = remember(candidates, availableRam) {
                            ModelRecommender.rank(candidates, availableRam)
                        }
                        recommendations.take(12).forEach { recommendation ->
                            val file = recommendation.candidate
                            val tierLabel = when (recommendation.tier) {
                                ModelRecommender.Tier.RECOMMENDED -> "Recomandat"
                                ModelRecommender.Tier.POSSIBLE -> "Posibil"
                                ModelRecommender.Tier.UNVERIFIED -> "Neverificat"
                                ModelRecommender.Tier.NOT_RECOMMENDED -> "Nerecomandat"
                            }
                            HorizontalDivider()
                            Text(file.filename)
                            Text("$tierLabel · Scor orientativ: ${recommendation.score}/100", style = MaterialTheme.typography.titleSmall)
                            Text(recommendation.explanation, style = MaterialTheme.typography.bodySmall)
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
                                }) { Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("Descarcă GGUF") }
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
    var maxResponseTokens by remember { mutableIntStateOf(16) }
    var response by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loadingModel by remember { mutableStateOf<String?>(null) }
    var loadDurationMs by remember { mutableStateOf<Long?>(null) }
    var generationDurationMs by remember { mutableStateOf<Long?>(null) }
    var outputTokenCount by remember { mutableStateOf<Int?>(null) }
    var nativeTimings by remember { mutableStateOf<LongArray?>(null) }
    var loadMemoryDeltaMiB by remember { mutableStateOf<Double?>(null) }
    var currentPssMiB by remember { mutableStateOf<Double?>(null) }
    var nativeHeapMiB by remember { mutableStateOf<Double?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importBusy = true
            importMessage = "Se copiază modelul în spațiul privat al aplicației…"
            thread(name = "gguf-local-import") {
                val result = runCatching { LocalModelImporter.import(context, uri) }
                (context as ComponentActivity).runOnUiThread {
                    importBusy = false
                    importMessage = result.fold(
                        onSuccess = { "Model importat: ${it.name}" },
                        onFailure = { "Import eșuat: ${it.message}" }
                    )
                    if (result.isSuccess) revision++
                }
            }
        }
    }
    val models = remember(revision) { InstalledModels.list(context) }
    if (activeModel.isNotBlank()) {
        Text("Conversație cu XILON AI", style = MaterialTheme.typography.titleLarge)
        Text("Model: " + activeModel.take(44) + if (activeModel.length > 44) "…" else "", style = MaterialTheme.typography.bodySmall)
        if (response.isNotBlank()) {
            Text("Ultimul răspuns", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(response, modifier = Modifier.padding(16.dp))
            }
        }
        if (busy && loadingModel == null) Text("XILON AI generează răspunsul local…")
    }
    Text("Modele instalate", style = MaterialTheme.typography.titleLarge)
    Text("Modelele GGUF complete pot fi încărcate local. Fișierele imatrix nu sunt modele de conversație.")
    Button(enabled = !importBusy && !busy, onClick = {
        filePicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
    }) { Text("Adaugă model GGUF din telefon") }
    if (importBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (importMessage.isNotBlank()) Text(importMessage, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { revision++ }) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reîmprospătează") }
    if (activeModel.isNotBlank()) {
        Text("Model activ: " + activeModel.take(44) + if (activeModel.length > 44) "…" else "")
        var showDiagnostics by remember { mutableStateOf(false) }
        TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
            Text(if (showDiagnostics) "Ascunde statisticile tehnice" else "Arată statisticile tehnice")
        }
        if (showDiagnostics) {
        loadDurationMs?.let { Text("Timp de încărcare: $it ms") }
        loadMemoryDeltaMiB?.let { Text("Variație memorie PSS la încărcare: %.1f MiB".format(java.util.Locale.ROOT, it)) }
        currentPssMiB?.let { Text("Memorie proces PSS: %.1f MiB".format(java.util.Locale.ROOT, it)) }
        nativeHeapMiB?.let { Text("Heap nativ alocat: %.1f MiB".format(java.util.Locale.ROOT, it)) }
        nativeTimings?.let { timings ->
            if (timings.size >= 3 && timings[0] >= 0 && timings[1] >= 0) {
                Text("Procesare prompt: ${timings[0]} ms · Decodare: ${timings[1]} ms")
                if (timings[1] > 0 && timings[2] > 0) {
                    Text("Decodare: %.1f tokenuri/s (%d tokenuri)".format(java.util.Locale.ROOT, timings[2] * 1000.0 / timings[1], timings[2]))
                }
            }
        }
        generationDurationMs?.let { elapsed ->
            Text("Timp ultima generare: $elapsed ms")
            val tokens = outputTokenCount
            if (tokens != null && tokens > 0 && elapsed > 0) {
                val throughput = tokens * 1000.0 / elapsed
                Text("Viteză aproximativă: %.1f tokenuri/s · %d tokenuri".format(java.util.Locale.ROOT, throughput, tokens))
                Text("Estimare end-to-end, include procesarea promptului; nu este benchmark de decodare pură.", style = MaterialTheme.typography.bodySmall)
            }
        }
        }
        Text("Istoric privat și recuperare locală a mesajelor relevante, fără sincronizare online.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !busy, onClick = {
            val modelName = activeModel
            busy = true
            thread(name = "clear-local-history") {
                val cleared = runCatching { LocalConversationStore.clear(context, modelName) }
                (context as ComponentActivity).runOnUiThread {
                    if (cleared.isSuccess) {
                        chatHistory = emptyList()
                        response = "Istoricul local pentru acest model a fost șters."
                    } else {
                        response = "Nu s-a putut șterge istoricul: ${cleared.exceptionOrNull()?.message}"
                    }
                    busy = false
                }
            }
        }) { Text("Șterge istoricul conversației") }
        Text("Lungimea răspunsului (telefonul a măsurat ~1,1 tokenuri/s)", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(16, 32, 64).forEach { count ->
                if (maxResponseTokens == count) {
                    Button(enabled = !busy, onClick = { maxResponseTokens = count }) { Text("$count") }
                } else {
                    OutlinedButton(enabled = !busy, onClick = { maxResponseTokens = count }) { Text("$count") }
                }
            }
        }
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Mesaj") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && prompt.isNotBlank(), onClick = {
            busy = true
            response = ""
            val submitted = prompt
            val completeHistorySnapshot = chatHistory
            val recentHistory = completeHistorySnapshot.takeLast(4)
            val recalledHistory = LocalMemoryRecall.recall(completeHistorySnapshot, submitted)
            val historySnapshot = recalledHistory + recentHistory
            val modelSnapshot = activeModel
            val outputLimit = maxResponseTokens
            thread(name = "local-inference") {
                try {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val conversation = ConversationBudget.fit(
                    history = historySnapshot,
                    tokenBudget = 2048 - outputLimit,
                    render = { turns ->
                        val roles = mutableListOf("system")
                        val contents = mutableListOf("You are a helpful assistant.")
                        turns.forEach { (question, answer) ->
                            roles.add("user"); contents.add(question)
                            roles.add("assistant"); contents.add(answer)
                        }
                        roles.add("user"); contents.add(submitted)
                        val native = runCatching {
                            LocalLlama.formatChat(roles.toTypedArray(), contents.toTypedArray())
                        }.getOrNull()
                        native ?: buildString {
                            append("You are a helpful assistant.\\n")
                            turns.forEach { (q, answer) ->
                                append("User: ").append(q).append("\\nAssistant: ").append(answer).append("\\n")
                            }
                            append("User: ").append(submitted).append("\\nAssistant:")
                        }
                    },
                    countTokens = { text -> LocalLlama.countTokens(text) }
                )
                val result = if (conversation == null)
                    Result.failure<String>(IllegalArgumentException("Mesajul depășește contextul disponibil."))
                else runCatching {
                    LocalLlama.generateStreaming(conversation, outputLimit) { partial ->
                        (context as ComponentActivity).runOnUiThread {
                            response = partial
                        }
                    }
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                val tokenCount = result.getOrNull()?.let { runCatching { LocalLlama.countTokens(it) }.getOrNull() }
                val timings = runCatching { LocalLlama.lastTimings() }.getOrNull()
                val memoryAfter = InferenceProfiler.snapshot()
                val nextHistory = if (result.isSuccess)
                    completeHistorySnapshot + (submitted to result.getOrThrow())
                else emptyList()
                val saved = if (result.isSuccess) runCatching {
                    LocalConversationStore.save(context, modelSnapshot, nextHistory)
                } else null
                (context as ComponentActivity).runOnUiThread {
                    generationDurationMs = elapsed
                    outputTokenCount = tokenCount
                    nativeTimings = timings
                    currentPssMiB = memoryAfter.totalPssKiB / 1024.0
                    nativeHeapMiB = memoryAfter.nativeHeapKiB / 1024.0
                    response = result.getOrElse { "Eroare: ${it.message}" }
                    if (result.isSuccess) {
                        chatHistory = nextHistory
                        prompt = ""
                        if (saved?.isFailure == true) response += "\\nAtenție: istoricul nu a putut fi salvat local."
                    }
                    busy = false
                }
                } catch (error: Exception) {
                    (context as ComponentActivity).runOnUiThread {
                        response = "Eroare la generarea offline: ${error.message ?: error.javaClass.simpleName}"
                        busy = false
                    }
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
    if (busy && loadingModel != null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Se încarcă ${loadingModel}…")
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
                    val analysis = ModelIntelligence.analyze(model.name)
                    val auxiliary = !analysis.runnableCandidate
                    if (auxiliary) Text(analysis.reason, color = MaterialTheme.colorScheme.error)
                    else Text("Cuantizare: ${analysis.quantization ?: "necunoscută"} · compatibilitate neconfirmată", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !busy && !auxiliary, onClick = {
                        busy = true
                        loadingModel = model.name
                        response = "Se încarcă modelul în memoria telefonului..."
                        thread(name = "llama-load") {
                            val memoryBefore = InferenceProfiler.snapshot()
                            val startedAt = android.os.SystemClock.elapsedRealtime()
                            val attempt = runCatching { LocalLlama.loadModel(model) }
                            val memoryAfter = InferenceProfiler.snapshot()
                            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                            val loaded = attempt.getOrDefault(false)
                            val storedHistory = if (loaded) runCatching { LocalConversationStore.load(context, model.name) }.getOrDefault(emptyList()) else emptyList()
                            (context as ComponentActivity).runOnUiThread {
                                loadDurationMs = if (loaded) elapsed else null
                                loadMemoryDeltaMiB = if (loaded) InferenceProfiler.deltaMiB(memoryBefore, memoryAfter) else null
                                currentPssMiB = memoryAfter.totalPssKiB / 1024.0
                                nativeHeapMiB = memoryAfter.nativeHeapKiB / 1024.0
                                generationDurationMs = null
                                outputTokenCount = null
                                nativeTimings = null
                                activeModel = if (loaded) model.name else ""
                                chatHistory = storedHistory
                                response = if (loaded) "Model încărcat. Poți trimite un mesaj." else "Încărcarea a eșuat: ${attempt.exceptionOrNull()?.message ?: "format incompatibil, model incomplet sau memorie insuficientă"}."
                                busy = false
                                loadingModel = null
                            }
                        }
                    }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (loadingModel == model.name) "Se încarcă…" else "Încarcă pentru chat") }
                    TextButton(onClick = { confirmDelete = model.name }) { Icon(Icons.Filled.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Șterge modelul") }
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
