package com.xilonalpha.xilonai

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Offline, allowlisted Android intent actions. No accessibility automation,
 * shell commands, arbitrary URLs or silent external side effects.
 */
object XilonActions {
    enum class Kind { SETTINGS, MUSIC, WEB_SEARCH, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, VOLUME_UP, VOLUME_DOWN, SET_VOLUME, SHARE_MESSAGE }
    data class Proposal(val kind: Kind, val label: String, val explanation: String)

    fun propose(text: String): Proposal? {
        val q = text.trim().lowercase(java.util.Locale.ROOT)
        return when {
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
        if (proposal.kind in setOf(Kind.PLAY_PAUSE, Kind.NEXT_TRACK,
                Kind.PREVIOUS_TRACK, Kind.VOLUME_UP, Kind.VOLUME_DOWN, Kind.SET_VOLUME)) {
            return try {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (proposal.kind) {
                    Kind.SET_VOLUME -> {
                        val percent = Regex("(\\\\d{1,3})\\\\s*%").find(originalMessage)
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
                    else -> {
                        val key = when (proposal.kind) {
                            Kind.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            Kind.NEXT_TRACK -> KeyEvent.KEYCODE_MEDIA_NEXT
                            else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        }
                        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                    }
                }
                true // Dispatched to Android; playback outcome cannot be guaranteed.
            } catch (_: SecurityException) { false }
              catch (_: IllegalStateException) { false }
        }
        val intent = when (proposal.kind) {
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
