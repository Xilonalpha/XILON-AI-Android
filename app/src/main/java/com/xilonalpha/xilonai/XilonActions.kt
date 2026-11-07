package com.xilonalpha.xilonai

import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Offline, allowlisted Android intent actions. No accessibility automation,
 * shell commands, arbitrary URLs or silent external side effects.
 */
object XilonActions {
    enum class Kind { SETTINGS, MUSIC, WEB_SEARCH, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, VOLUME_UP, VOLUME_DOWN, SET_VOLUME, SHARE_MESSAGE, YOUTUBE_SEARCH, MEDIA_ACCESS, OPEN_APP }
    data class Proposal(val kind: Kind, val label: String, val explanation: String)

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "telegram" to "org.telegram.messenger",
        "maps" to "com.google.android.apps.maps"
    )

    private fun requestedApp(command: String): String? {
        val words = command.trim().lowercase(java.util.Locale.ROOT).split(Regex("\\s+"))
        if (words.size != 2 || words[0] !in setOf(
                "deschide", "porneste", "pornește", "lanseaza", "lansează", "open", "launch")) return null
        return appPackages[words[1]]
    }

    fun propose(text: String): Proposal? {
        val q = text.trim().lowercase(java.util.Locale.ROOT)
        return when {
            requestedApp(q) != null ->
                Proposal(Kind.OPEN_APP, "Deschide aplicația " + q.substringAfterLast(' '),
                    "Lansează aplicația instalată după confirmare; nu execută comenzi în interiorul ei.")
            listOf("activează controlul media", "activeaza controlul media",
                "permisiune control media", "enable media control")
                .any(q::contains) ->
                Proposal(Kind.MEDIA_ACCESS, "Configurează controlul media",
                    "Deschide setările Android pentru activarea manuală a accesului la notificări.")
            listOf("caută pe youtube:", "cauta pe youtube:", "youtube search:")
                .any(q::startsWith) ->
                Proposal(Kind.YOUTUBE_SEARCH, "Caută pe YouTube",
                    "Deschide rezultatele căutării în YouTube sau browser; nu pornește automat redarea.")
            listOf("trimite pe whatsapp:", "trimite prin whatsapp:",
                "share on whatsapp:", "mesaj whatsapp:")
                .any(q::startsWith) ->
                Proposal(Kind.SHARE_MESSAGE, "Pregătește mesaj WhatsApp",
                    "Deschide WhatsApp cu textul pregătit. Tu alegi destinatarul și confirmi trimiterea.")
            listOf("pauză muzica", "pauza muzica", "oprește muzica", "opreste muzica",
                "play pause", "pause music", "reia muzica", "reia muzică")
                .any(q::contains) ->
                Proposal(Kind.PLAY_PAUSE, "Redare / pauză", "Trimite comanda către sesiunea media activă, dacă Android permite.")
            listOf("melodia următoare", "melodia urmatoare", "următoarea melodie",
                "urmatoarea melodie", "next track")
                .any(q::contains) ->
                Proposal(Kind.NEXT_TRACK, "Melodia următoare", "Solicită trecerea la următoarea piesă.")
            listOf("melodia anterioară", "melodia anterioara", "piesa anterioară",
                "piesa anterioara", "previous track")
                .any(q::contains) ->
                Proposal(Kind.PREVIOUS_TRACK, "Melodia anterioară", "Solicită redarea piesei anterioare.")
            listOf("volum mai tare", "mărește volumul", "mareste volumul", "volume up")
                .any(q::contains) ->
                Proposal(Kind.VOLUME_UP, "Mărește volumul", "Crește volumul media cu o treaptă.")
            listOf("volum mai încet", "volum mai incet", "micșorează volumul",
                "micsoreaza volumul", "volume down")
                .any(q::contains) ->
                Proposal(Kind.VOLUME_DOWN, "Micșorează volumul", "Scade volumul media cu o treaptă.")
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
        if (proposal.kind in setOf(Kind.PLAY_PAUSE, Kind.NEXT_TRACK, Kind.PREVIOUS_TRACK)) {
            val command = when (proposal.kind) {
                Kind.PLAY_PAUSE -> XilonMediaSessions.Command.TOGGLE
                Kind.NEXT_TRACK -> XilonMediaSessions.Command.NEXT
                else -> XilonMediaSessions.Command.PREVIOUS
            }
            val result = runCatching { XilonMediaSessions.command(context, command) }.getOrNull()
            return result?.outcome == XilonMediaSessions.Outcome.DISPATCHED ||
                result?.outcome == XilonMediaSessions.Outcome.VERIFIED
        }
        if (proposal.kind in setOf(Kind.VOLUME_UP, Kind.VOLUME_DOWN, Kind.SET_VOLUME)) {
            return try {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (proposal.kind) {
                    Kind.SET_VOLUME -> {
                        val percent = Regex("""(\d{1,3})\s*%""").find(originalMessage)
                            ?.groupValues?.get(1)?.toIntOrNull() ?: return false
                        if (percent !in 0..100) return false
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                            (max * percent + 50) / 100, 0)
                    }
                    Kind.VOLUME_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, 0)
                    Kind.VOLUME_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, 0)
                    else -> return false
                }
                true // Dispatched to Android; playback outcome cannot be guaranteed.
            } catch (_: SecurityException) { false }
              catch (_: IllegalStateException) { false }
        }
        if (proposal.kind == Kind.OPEN_APP) {
            val packageName = requestedApp(originalMessage) ?: return false
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            return try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: android.content.ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        val intent = when (proposal.kind) {
            Kind.YOUTUBE_SEARCH -> {
                val query = originalMessage.substringAfter(':', "").trim()
                if (query.isBlank()) return false
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" +
                    java.net.URLEncoder.encode(query, "UTF-8")))
            }
            Kind.SHARE_MESSAGE -> {
                val message = originalMessage.substringAfter(':', "").trim()
                if (message.isBlank()) return false
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
            }
            Kind.PLAY_PAUSE, Kind.NEXT_TRACK, Kind.PREVIOUS_TRACK,
            Kind.VOLUME_UP, Kind.VOLUME_DOWN, Kind.SET_VOLUME -> return false
            Kind.OPEN_APP -> return false
            Kind.MEDIA_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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
