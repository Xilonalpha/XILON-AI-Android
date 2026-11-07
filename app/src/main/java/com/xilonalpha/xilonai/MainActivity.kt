package com.xilonalpha.xilonai

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { XilonHome() }
 }
}

@Composable
private fun XilonHome() {
 var selected by remember { mutableIntStateOf(0) }
 val tabs = listOf("Chat", "Modele", "Dispozitiv")
 Scaffold(bottomBar = {
  NavigationBar {
   tabs.forEachIndexed { index, name ->
    NavigationBarItem(selected = selected == index, onClick = { selected = index }, icon = { Text((index + 1).toString()) }, label = { Text(name) })
   }
  }
 }) { padding ->
  Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text("XILON AI", style = MaterialTheme.typography.headlineMedium)
   when (selected) {
    0 -> Text("Chat local: integrarea llama.cpp urmează.")
    1 -> Text("Catalog Hugging Face: filtrarea compatibilității urmează.")
    else -> DeviceInfo()
   }
  }
 }
}

@Composable
private fun DeviceInfo() {
 val context = LocalContext.current
 val manager = context.getSystemService(ActivityManager::class.java)
 val memory = remember { ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) } }
 Text("Producător: ${Build.MANUFACTURER}")
 Text("Model: ${Build.MODEL}")
 Text("Android: ${Build.VERSION.RELEASE}")
 Text("Arhitecturi: ${Build.SUPPORTED_ABIS.joinToString()}")
 Text("RAM total: ${memory.totalMem / (1024L * 1024L)} MiB")
 Text("RAM disponibil acum: ${memory.availMem / (1024L * 1024L)} MiB")
}
