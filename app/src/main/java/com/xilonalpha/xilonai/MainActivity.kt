package com.xilonalpha.xilonai

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Build

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
    val tabs = listOf("Chat", "Modele", "Dispozitiv", "Instrumente")
    val icons = listOf(Icons.Filled.ChatBubbleOutline, Icons.Filled.Memory, Icons.Filled.PhoneAndroid, Icons.Filled.Build)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("XILON AI", modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.headlineSmall)
                tabs.forEachIndexed { index, name ->
                    NavigationDrawerItem(
                        label = { Text(name) },
                        selected = selected == index,
                        onClick = {
                            selected = index
                            drawerScope.launch { drawerState.close() }
                        },
                        icon = { Icon(icons[index], contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
    Scaffold(containerColor = XilonDark) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF4733A4), Color(0xFF1D315C), XilonDark))
                ).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Deschide meniul lateral", tint = Color.White)
                    }
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
                Spacer(Modifier.height(6.dp))
                Text(
                    when (selected) {
                        0 -> "Spațiul tău de conversație"
                        1 -> "Descoperă modele AI"
                        2 -> "Capabilitățile dispozitivului"
                        else -> "Instrumente și setări"
                    },
                    style = MaterialTheme.typography.titleMedium, color = Color.White
                )
            }
            // Keep chat composed while browsing tabs: loaded GGUF state and active
            // generation must not be forgotten when navigating away from Chat.
            Box(Modifier.fillMaxSize()) {
                Box(if (selected == 0 || selected == 3) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                    InstalledModelsScreen(toolsVisible = selected == 3)
                }
                if (selected == 1) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { ModelExplorer() }
                } else if (selected == 2) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { DeviceInfo() }
                }
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
private fun InstalledModelsScreen(toolsVisible: Boolean) {
    val context = LocalContext.current
    var speechStatus by remember { mutableStateOf("") }
    val speech = remember(context) { XilonSpeech(context) { speechStatus = it } }
    DisposableEffect(speech) { onDispose { speech.close() } }
    val voice = remember(context) { XilonVoice(context) }
    var voiceStatus by remember { mutableStateOf("") }
    DisposableEffect(voice) { onDispose { voice.stop() } }
    var revision by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var activeModel by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var prompt by remember { mutableStateOf("") }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    var maxResponseTokens by remember { mutableIntStateOf(512) }
    var preferFastAnswers by remember { mutableStateOf(true) }
    var response by remember { mutableStateOf("") }
    var htmlExportStatus by remember { mutableStateOf("") }
    var pendingHtml by remember { mutableStateOf<String?>(null) }
    var pendingObj by remember { mutableStateOf<String?>(null) }
    var pendingGlb by remember { mutableStateOf<ByteArray?>(null) }
    var glbExportStatus by remember { mutableStateOf("") }
    val glbSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("model/gltf-binary")
    ) { uri ->
        val bytes = pendingGlb
        glbExportStatus = if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Nu pot deschide fișierul GLB.")
            }.fold(
                onSuccess = { "Modelul GLB a fost salvat." },
                onFailure = { "Export GLB eșuat: ${it.message}" }
            )
        } else "Export GLB anulat."
        pendingGlb = null
    }
    var objExportStatus by remember { mutableStateOf("") }
    val objSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val mesh = pendingObj
        objExportStatus = if (uri != null && mesh != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(mesh.toByteArray(Charsets.UTF_8))
                } ?: error("Nu pot deschide fișierul OBJ.")
            }.fold(
                onSuccess = { "Geometria OBJ a fost salvată." },
                onFailure = { "Export OBJ eșuat: ${it.message}" }
            )
        } else "Export OBJ anulat."
        pendingObj = null
    }
    var previewHtml by remember { mutableStateOf<String?>(null) }
    previewHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { previewHtml = null },
            title = { Text("Previzualizare HTML locală") },
            text = {
                Column {
                    Text("Ține apăsat pe scena Three.js pentru export GLB cu materiale și texturi acceptate de GLTFExporter. Previzualizare locală WebGL/HTML. Accesul la rețea și fișiere este blocat. Three.js clasic este inclus local; alte biblioteci și module externe nu se încarcă.")
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        XilonHtmlPreview(html, onSceneGlb = { bytes, error ->
                            if (bytes != null) {
                                pendingGlb = bytes
                                glbSaver.launch("xilon-scena-three.glb")
                            } else {
                                glbExportStatus = error ?: "Scena nu poate fi exportată."
                            }
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewHtml = null }) { Text("Închide") }
            }
        )
    }
    val htmlSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val html = pendingHtml
        htmlExportStatus = if (uri != null && html != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(html.toByteArray(Charsets.UTF_8))
                } ?: error("Nu pot deschide fișierul.")
            }.fold(
                onSuccess = { "Fișier HTML salvat. Deschide-l într-un browser compatibil." },
                onFailure = { "Salvarea a eșuat: ${it.message}" }
            )
        } else "Salvarea a fost anulată."
        pendingHtml = null
    }
    var busy by remember { mutableStateOf(false) }
    val stopRequested = remember { AtomicBoolean(false) }
    var loadingModel by remember { mutableStateOf<String?>(null) }
    var loadDurationMs by remember { mutableStateOf<Long?>(null) }
    var generationDurationMs by remember { mutableStateOf<Long?>(null) }
    var generationStartedAt by remember { mutableLongStateOf(0L) }
    var generationElapsedMs by remember { mutableLongStateOf(0L) }
    var generatedCharacters by remember { mutableIntStateOf(0) }
    var rawCharacters by remember { mutableIntStateOf(0) }
    LaunchedEffect(busy, generationStartedAt) {
        while (busy && generationStartedAt > 0L) {
            generationElapsedMs = android.os.SystemClock.elapsedRealtime() - generationStartedAt
            kotlinx.coroutines.delay(500L)
        }
    }
    var outputTokenCount by remember { mutableStateOf<Int?>(null) }
    var nativeTimings by remember { mutableStateOf<LongArray?>(null) }
    var loadMemoryDeltaMiB by remember { mutableStateOf<Double?>(null) }
    var currentPssMiB by remember { mutableStateOf<Double?>(null) }
    var nativeHeapMiB by remember { mutableStateOf<Double?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    var showModelManager by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showGenerationDetails by remember { mutableStateOf(false) }
    val showAdvancedTools = toolsVisible
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                voice.listen(onText = { prompt = it }, onStatus = { voiceStatus = it })
            }.onFailure { voiceStatus = "Microfon indisponibil: ${it.message}" }
        } else voiceStatus = "Permisiunea pentru microfon a fost refuzată."
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importBusy = true
            importMessage = "Se copiază modelul în spațiul privat al aplicației…"
            thread(name = "gguf-local-import") {
                val result = runCatching {
                    LocalModelImporter.import(context, uri) { copied, total ->
                        (context as ComponentActivity).runOnUiThread {
                            importMessage = if (total != null && total > 0) {
                                "Import GGUF: ${(copied * 100 / total).coerceIn(0, 100)}% (${copied / 1048576} MiB)"
                            } else {
                                "Import GGUF: ${copied / 1048576} MiB copiați"
                            }
                        }
                    }
                }
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
    val chatScrollState = rememberScrollState()
    LaunchedEffect(chatHistory.size, response.length, activeModel) {
        if (activeModel.isNotBlank()) chatScrollState.scrollTo(chatScrollState.maxValue)
    }
    val actionPlan = remember(prompt) { XilonActionPlanner.plan(prompt) }
    var actionFeedback by remember { mutableStateOf("") }
    var mediaSnapshot by remember { mutableStateOf<XilonMediaSessions.Snapshot?>(null) }
    var mediaStatus by remember { mutableStateOf("") }
    val mediaObserver = remember(context) {
        XilonMediaObserver(context) { mediaSnapshot = it }
    }
    DisposableEffect(mediaObserver) { onDispose { mediaObserver.close() } }
    val taskDecision = remember(prompt) { XilonCore.classify(prompt) }
    val suggestedModel = remember(revision, prompt) {
        XilonCore.suggestModel(taskDecision.task, InstalledModels.list(context).map { it.name })
    }
    val models = remember(revision) { InstalledModels.list(context) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(chatScrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

    if (!toolsVisible) {
    if (activeModel.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("XILON AI", style = MaterialTheme.typography.titleMedium)
                Text("AI local · " + activeModel.take(32),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        }
        if (chatHistory.isEmpty() && response.isBlank() && !busy) {
            Column(Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text("Cu ce te pot ajuta?", style = MaterialTheme.typography.headlineSmall)
                Text("Conversația rămâne pe dispozitiv.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        chatHistory.takeLast(12).forEach { (question, answer) ->
            XilonChatBubble(question, isUser = true)
            XilonChatBubble(answer, isUser = false)
        }
        if (busy && pendingPrompt != null) {
            XilonChatBubble(pendingPrompt!!, isUser = true)
        }
        if (response.isNotBlank() && (busy || chatHistory.lastOrNull()?.second != response)) {
            XilonChatBubble(response, isUser = false)
        }
        // Surface the generated scene directly in chat, not only inside hidden 3D tools.
        val readySceneHtml = if (!busy) {
            XilonHtmlExport.extract(response)
                ?: chatHistory.lastOrNull()?.second?.let { XilonHtmlExport.extract(it) }
        } else null
        if (readySceneHtml != null) {
            Button(onClick = { previewHtml = readySceneHtml }) {
                Text("Deschide scena 3D generată")
            }
        }
    }
        if (showAdvancedTools) {
        var show3dTools by remember { mutableStateOf(false) }
        LaunchedEffect(taskDecision.task) {
            if (taskDecision.task == XilonCore.Task.THREE_D) show3dTools = true
        }
        TextButton(onClick = { show3dTools = !show3dTools }) {
            Text(if (show3dTools) "Ascunde instrumentele 3D" else "Instrumente 3D și export")
        }
        if (show3dTools) {
        OutlinedButton(onClick = {
            previewHtml = XilonOffline3dDemo.html
        }) { Text("Testează cubul 3D offline") }
        OutlinedButton(onClick = { previewHtml = XilonThreeDemo.html }) {
            Text("Testează Three.js offline")
        }
        OutlinedButton(onClick = { previewHtml = XilonThreeModuleDemo.html }) {
            Text("Testează Three.js ES module")
        }
        val exportableHtml = XilonHtmlExport.extract(response)
            ?: chatHistory.lastOrNull()?.second?.let { XilonHtmlExport.extract(it) }
        if (!busy && exportableHtml != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pendingHtml = exportableHtml
                    htmlSaver.launch("xilon-scena-3d.html")
                }) { Text("Salvează HTML") }
                OutlinedButton(onClick = { previewHtml = exportableHtml }) {
                    Text("Previzualizare 3D")
                }
            }
        }
        val exportableObj = XilonObjExport.extract(response)
            ?: chatHistory.lastOrNull()?.second?.let { XilonObjExport.extract(it) }
        if (!busy && exportableObj != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pendingObj = exportableObj
                    objSaver.launch("xilon-model-3d.obj")
                }) { Text("Salvează OBJ") }
                OutlinedButton(onClick = {
                    runCatching { XilonGlbExport.fromObj(exportableObj) }
                        .onSuccess { bytes ->
                            pendingGlb = bytes
                            glbSaver.launch("xilon-model-3d.glb")
                        }
                        .onFailure { glbExportStatus = "Conversie GLB eșuată: ${it.message}" }
                }) { Text("Salvează GLB") }
            }
        }
        if (glbExportStatus.isNotBlank()) Text(glbExportStatus, style = MaterialTheme.typography.bodySmall)
        if (objExportStatus.isNotBlank()) Text(objExportStatus,
            style = MaterialTheme.typography.bodySmall)
        if (htmlExportStatus.isNotBlank()) Text(htmlExportStatus,
            style = MaterialTheme.typography.bodySmall)
        }
        }
        if (busy && loadingModel == null && response.isBlank()) {
            Text("XILON AI pregătește răspunsul…",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showAdvancedTools && activeModel.isNotBlank()) {

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
        TextButton(onClick = { showChatSettings = !showChatSettings }) {
            Text(if (showChatSettings) "Ascunde opțiunile conversației" else "Opțiuni conversație și memorie")
        }
        if (showChatSettings) {
        Text("Memorie locală privată", style = MaterialTheme.typography.labelMedium)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = preferFastAnswers, onCheckedChange = { preferFastAnswers = it })
            Text("Răspuns rapid pentru Qwen3 (fără raționament extins)")
        }
        Text("Lungimea răspunsului", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(256, 512, 1024, 1536).forEach { count ->
                if (maxResponseTokens == count) {
                    Button(enabled = !busy, onClick = { maxResponseTokens = count }) { Text("$count") }
                } else {
                    OutlinedButton(enabled = !busy, onClick = { maxResponseTokens = count }) { Text("$count") }
                }
            }
        }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (showAdvancedTools) {
    HorizontalDivider()
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showModelManager = !showModelManager }
    ) { Text(if (showModelManager) "Ascunde gestionarea modelelor" else "Gestionează modelele GGUF") }
    if (showModelManager) {
    HorizontalDivider()
    Text("Gestionare modele", style = MaterialTheme.typography.titleMedium)
    Text("Modele instalate", style = MaterialTheme.typography.titleLarge)
    Text("Modelele GGUF complete pot fi încărcate local. Fișierele imatrix nu sunt modele de conversație.")
    Button(enabled = !importBusy && !busy, onClick = {
        filePicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
    }) { Text("Adaugă model GGUF din telefon") }
    if (importBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (importMessage.isNotBlank()) Text(importMessage, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { revision++ }) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reîmprospătează") }
    if (busy && loadingModel != null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Se încarcă ${loadingModel}…")
    }
    if (activeModel.isBlank() && response.isNotBlank()) Text(response)
    if (models.isEmpty()) {
        Text("Niciun model instalat. Deschide fila Modele pentru descărcare.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
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
                                if (loaded) showModelManager = false
                                response = if (loaded) "" else "Încărcarea a eșuat: ${attempt.exceptionOrNull()?.message ?: "format incompatibil, model incomplet sau memorie insuficientă"}."
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
    }
        }
    }
        // The voice/action composer is available even without a loaded GGUF model.
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val access = runCatching { XilonMediaSessions.hasAccess(context) }.getOrDefault(false)
                val observing = if (access) mediaObserver.start() else false
                mediaStatus = when {
                    !access -> "Acces media neactivat. Spune «activează controlul media» pentru a deschide setările."
                    !observing -> "Monitorizarea nu a putut fi activată. Verifică permisiunea Android."
                    else -> "Monitorizare activă: XILON urmărește inclusiv apariția și schimbarea sesiunilor media."
                }
            }) { Text("Verifică redarea") }
            if (mediaSnapshot != null) Text(mediaSnapshot!!.state,
                style = MaterialTheme.typography.labelSmall)
        }
        mediaSnapshot?.let { snapshot ->
            Text("Media: ${snapshot.title ?: "Titlu indisponibil"} · ${snapshot.artist ?: snapshot.packageName}",
                style = MaterialTheme.typography.bodySmall)
        }
        if (mediaStatus.isNotBlank()) Text(mediaStatus,
            style = MaterialTheme.typography.labelSmall)
        if (response.isNotBlank() && !busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("XILON AI", response))
                }) { Text("Copiază") }
                OutlinedButton(onClick = { speech.speak(response) }) {
                    Text("Citește răspunsul offline")
                }
                TextButton(onClick = { speech.stop() }) { Text("Oprește vocea") }
            }
            if (speechStatus.isNotBlank()) Text(speechStatus,
                style = MaterialTheme.typography.labelSmall)
        }
        if (actionFeedback.isNotBlank()) Text(actionFeedback,
            style = MaterialTheme.typography.bodySmall)
        if (prompt.isNotBlank()) {
            Text("XILON Core: ${taskDecision.explanation}", style = MaterialTheme.typography.labelSmall)
            if (suggestedModel != null && suggestedModel != activeModel) {
                Text("Model sugerat (orientativ): " + suggestedModel.take(42),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
                }
            }
        }
        if (activeModel.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = { Text("Întreabă XILON AI…") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            maxLines = 5
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(enabled = !busy, onClick = {
                if (!voice.available()) {
                    voiceStatus = "Acest Android nu oferă recunoaștere vocală complet locală."
                } else if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    runCatching {
                        voice.listen(onText = { prompt = it }, onStatus = { voiceStatus = it })
                    }.onFailure { voiceStatus = "Eroare voce: ${it.message}" }
                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("🎙 Voce") }
            if (voiceStatus.isNotBlank()) {
                Text(voiceStatus, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        if (actionFeedback.isNotBlank()) {
            Text(actionFeedback, style = MaterialTheme.typography.bodySmall)
        }
        if (activeModel.isBlank()) Text("Comenzi Android disponibile fără model GGUF. Pentru chat, încarcă un model.", style = MaterialTheme.typography.labelSmall)
        Button(enabled = !busy && prompt.isNotBlank() &&
            (actionPlan != null || activeModel.isNotBlank()), onClick = {
            if (actionPlan != null) {
                // Submitting the command is authorization to dispatch allowlisted actions.
                // External apps still own their final send/permission UI.
                val report = XilonActionPlanner.executeWithReport(context, actionPlan)
                val summary = if (report.allDispatched)
                    "Trimise: ${report.dispatched}/${report.total}; verificate: ${report.verified}. "
                else "Oprit la pasul ${report.failedStep}; trimise: ${report.dispatched}/${report.total}. "
                actionFeedback = summary + report.details.joinToString("\n")
                prompt = ""
            } else {
            stopRequested.set(false)
            generationStartedAt = android.os.SystemClock.elapsedRealtime()
            generationElapsedMs = 0L
            generatedCharacters = 0
            rawCharacters = 0
            busy = true
            response = ""
            val submitted = prompt
            val submittedTask = XilonCore.classify(submitted).task
            pendingPrompt = submitted
            prompt = ""
            val completeHistorySnapshot = chatHistory
            val recentHistory = completeHistorySnapshot.takeLast(4)
            val recalledHistory = LocalMemoryRecall.recall(completeHistorySnapshot, submitted)
            val historySnapshot = recalledHistory + recentHistory
            val modelSnapshot = activeModel
            val fastAnswerSnapshot = preferFastAnswers
            val outputLimit = maxResponseTokens
            thread(name = "local-inference") {
                try {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val conversation = ConversationBudget.fit(
                    history = historySnapshot,
                    tokenBudget = 4096 - outputLimit,
                    render = { turns ->
                        val roles = mutableListOf("system")
                        val systemInstruction = if (submittedTask == XilonCore.Task.THREE_D)
                            "You are XILON AI, an offline 3D coding assistant. For a 3D creation request, output a complete standalone HTML document with a Three.js scene using JavaScript and procedural geometry. Include camera, lights, controls, and an animation loop. Use locally available Three.js; do not import remote scripts, models, textures, or network resources. For a human face, clearly label the result as a stylized procedural approximation, not a photorealistic scan. Output code rather than promising a rendered model."
                        else "You are XILON AI, a helpful offline assistant. Give the final answer directly and concisely. If your chat template supports optional reasoning, prefer the non-thinking mode for ordinary requests. Never include internal reasoning or role delimiters in the final answer."
                        val contents = mutableListOf(systemInstruction)
                        turns.forEach { (question, answer) ->
                            roles.add("user"); contents.add(question)
                            roles.add("assistant"); contents.add(answer)
                        }
                        val qwen3Fast = fastAnswerSnapshot && modelSnapshot.lowercase(java.util.Locale.ROOT).contains("qwen3")
                        val modelPrompt = if (qwen3Fast) submitted + "\n/no_think" else submitted
                        roles.add("user"); contents.add(modelPrompt)
                        val native = runCatching {
                            LocalLlama.formatChat(roles.toTypedArray(), contents.toTypedArray())
                        }.getOrNull()
                        native ?: buildString {
                            append(systemInstruction).append("\\n")
                            turns.forEach { (q, answer) ->
                                append("User: ").append(q).append("\\nAssistant: ").append(answer).append("\\n")
                            }
                            append("User: ").append(modelPrompt).append("\\nAssistant:")
                        }
                    },
                    countTokens = { text -> LocalLlama.countTokens(text) }
                )
                var lastUiUpdateAt = 0L
                val result = if (conversation == null)
                    Result.failure<String>(IllegalArgumentException("Mesajul depășește contextul disponibil."))
                else runCatching {
                    LocalLlama.generateStreaming(conversation, outputLimit) { partial ->
                        // Avoid flooding Compose's main thread with one full-string
                        // update per decoded token on slower Android devices.
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUiUpdateAt >= 150L || stopRequested.get()) {
                            lastUiUpdateAt = now
                            val visible = XilonResponseBoundary.clean(partial)
                            val rawLength = partial.length
                            (context as ComponentActivity).runOnUiThread {
                                rawCharacters = rawLength
                                if (response != visible) response = visible
                                generatedCharacters = visible.length
                            }
                        }
                    }
                }
                val cleanedResult = result.map { XilonResponseBoundary.clean(it) }
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                val tokenCount = cleanedResult.getOrNull()?.let { runCatching { LocalLlama.countTokens(it) }.getOrNull() }
                val timings = runCatching { LocalLlama.lastTimings() }.getOrNull()
                val memoryAfter = InferenceProfiler.snapshot()
                val completed = cleanedResult.isSuccess && !stopRequested.get()
                val nextHistory = if (completed)
                    completeHistorySnapshot + (submitted to cleanedResult.getOrThrow())
                else emptyList()
                val saved = if (completed) runCatching {
                    LocalConversationStore.save(context, modelSnapshot, nextHistory)
                } else null
                (context as ComponentActivity).runOnUiThread {
                    generationDurationMs = elapsed
                    outputTokenCount = tokenCount
                    nativeTimings = timings
                    currentPssMiB = memoryAfter.totalPssKiB / 1024.0
                    nativeHeapMiB = memoryAfter.nativeHeapKiB / 1024.0
                    rawCharacters = maxOf(rawCharacters, cleanedResult.getOrNull()?.length ?: 0)
                    response = if (stopRequested.get()) "Generare oprită. Text parțial:\\n" + cleanedResult.getOrDefault(response) else cleanedResult.getOrElse { "Eroare: ${it.message}" }
                    if (completed) {
                        chatHistory = nextHistory
                        prompt = ""
                        if (saved?.isFailure == true) response += "\\nAtenție: istoricul nu a putut fi salvat local."
                    }
                    pendingPrompt = null
                    busy = false
                }
                } catch (error: Exception) {
                    (context as ComponentActivity).runOnUiThread {
                        response = "Eroare la generarea offline: ${error.message ?: error.javaClass.simpleName}"
                        pendingPrompt = null
                        busy = false
                    }
                }
            }
            }
        }) { Text("Trimite  ↑") }
        if (busy && loadingModel == null) {
            val seconds = generationElapsedMs / 1000L
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("XILON generează · ${seconds / 60}m ${seconds % 60}s",
                    style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { showGenerationDetails = !showGenerationDetails }) {
                    Text(if (showGenerationDetails) "Ascunde detalii" else "Detalii")
                }
                TextButton(onClick = { stopRequested.set(true); LocalLlama.requestStop() }) {
                    Text("Oprește")
                }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (showGenerationDetails) {
                Text("Text brut: $rawCharacters · Răspuns vizibil: $generatedCharacters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@Composable
private fun XilonChatBubble(message: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(message, modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("XILON AI", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 26.dp, end = 8.dp))
            }
        }
    }
}
