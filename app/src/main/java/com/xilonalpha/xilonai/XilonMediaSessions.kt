package com.xilonalpha.xilonai

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.text.TextUtils

/**
 * Active media session controls. Requires the user to enable XILON's notification
 * listener in Android settings; never bypasses Android access controls.
 */
object XilonMediaSessions {
    enum class Command { TOGGLE, NEXT, PREVIOUS }
    enum class Outcome { VERIFIED, DISPATCHED, UNAVAILABLE, DENIED }
    data class Result(val outcome: Outcome, val description: String)

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners").orEmpty()
        val component = ComponentName(context, XilonMediaListener::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun command(context: Context, command: Command): Result {
        if (!hasAccess(context)) return Result(Outcome.DENIED,
            "Activează accesul XILON la notificări din setările Android pentru controlul sesiunilor media.")
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return Result(Outcome.UNAVAILABLE,
            "Managerul sesiunilor media nu este disponibil.")
        val sessions = try {
            manager.getActiveSessions(ComponentName(context, XilonMediaListener::class.java))
        } catch (_: SecurityException) {
            return Result(Outcome.DENIED, "Android nu permite accesul la sesiunile media.")
        }
        val controller = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        } ?: return Result(Outcome.UNAVAILABLE, "Nu există o sesiune media activă compatibilă.")
        val before = controller.playbackState?.state
        val controls = controller.transportControls
        try {
            when (command) {
                Command.TOGGLE -> if (before == PlaybackState.STATE_PLAYING)
                    controls.pause() else controls.play()
                Command.NEXT -> controls.skipToNext()
                Command.PREVIOUS -> controls.skipToPrevious()
            }
        } catch (_: SecurityException) {
            return Result(Outcome.DENIED, "Comanda media a fost refuzată.")
        } catch (_: IllegalStateException) {
            return Result(Outcome.UNAVAILABLE, "Sesiunea media nu mai este activă.")
        }
        // MediaSession state changes are asynchronous. Do not claim playback
        // succeeded merely because a transport control returned.
        return Result(Outcome.DISPATCHED,
            "Comanda a fost transmisă sesiunii media ${controller.packageName}; redarea nu este încă verificată.")
    }
}
