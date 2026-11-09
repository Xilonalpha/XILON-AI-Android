package com.xilonalpha.xilonai

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private, offline, per-model conversation storage. No network or cloud sync. */
object LocalConversationStore {
    private const val MAX_TURNS = 100
    private const val MAX_MESSAGE_CHARS = 16000
    private const val MAX_FILE_BYTES = 4 * 1024 * 1024

    private fun storage(context: Context, modelName: String): AtomicFile {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(modelName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "conversations")
        if (!directory.exists()) check(directory.mkdirs()) { "Cannot create conversation directory" }
        return AtomicFile(File(directory, "$digest.json"))
    }

    @Synchronized
    fun load(context: Context, modelName: String): List<Pair<String, String>> {
        val file = storage(context, modelName)
        if (!file.baseFile.exists()) return emptyList()
        if (file.baseFile.length() > MAX_FILE_BYTES) return emptyList()
        val bytes = file.openRead().use { it.readBytes() }
        if (bytes.size > MAX_FILE_BYTES) return emptyList()
        val array = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("turns")
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val user = item.optString("user")
            val assistant = item.optString("assistant")
            if (user.isBlank() || assistant.isBlank()) null else
                user.take(MAX_MESSAGE_CHARS) to assistant.take(MAX_MESSAGE_CHARS)
        }.takeLast(MAX_TURNS)
    }

    @Synchronized
    fun save(context: Context, modelName: String, turns: List<Pair<String, String>>) {
        val file = storage(context, modelName)
        val array = JSONArray()
        turns.takeLast(MAX_TURNS).forEach { (user, assistant) ->
            array.put(JSONObject().put("user", user.take(MAX_MESSAGE_CHARS))
                .put("assistant", assistant.take(MAX_MESSAGE_CHARS)))
        }
        val bytes = JSONObject().put("version", 1).put("turns", array)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES) { "Conversation history exceeds 4 MiB" }
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    fun clear(context: Context, modelName: String) {
        storage(context, modelName).delete()
    }
}
