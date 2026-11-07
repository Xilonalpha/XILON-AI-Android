package com.xilonalpha.xilonai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Offline, allowlisted Android intent actions. No accessibility automation,
 * shell commands, arbitrary URLs or silent external side effects.
 */
object XilonActions {
    enum class Kind { SETTINGS, MUSIC, WEB_SEARCH }
    data class Proposal(val kind: Kind, val label: String, val explanation: String)

    fun propose(text: String): Proposal? {
        val q = text.trim().lowercase(java.util.Locale.ROOT)
        return when {
            listOf("deschide setările", "deschide setarile", "open settings")
                .any(q::contains) ->
                Proposal(Kind.SETTINGS, "Deschide setările Android", "Deschide ecranul de setări al telefonului.")
            listOf("deschide muzica", "deschide muzică", "open music player")
                .any(q::contains) ->
                Proposal(Kind.MUSIC, "Deschide aplicația de muzică", "Pornește aplicația muzicală disponibilă; nu garantează redarea.")
            listOf("caută pe web", "cauta pe web", "search the web")
                .any(q::contains) ->
                Proposal(Kind.WEB_SEARCH, "Deschide căutarea web", "Căutarea folosește browserul și poate necesita internet.")
            else -> null
        }
    }

    fun execute(context: Context, proposal: Proposal, originalMessage: String): Boolean {
        val intent = when (proposal.kind) {
            Kind.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            Kind.MUSIC -> Intent("android.intent.action.MUSIC_PLAYER")
            Kind.WEB_SEARCH -> {
                val q = originalMessage.replace(Regex("(?i)caută pe web|cauta pe web|search the web"), "").trim()
                if (q.isBlank()) return false
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(q, "UTF-8")))
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
