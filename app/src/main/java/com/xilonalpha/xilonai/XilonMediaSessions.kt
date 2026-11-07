package com.xilonalpha.xilonai

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

/**
 * Active media session controls. Requires the user to enable XILON's notification
 * listener in Android settings; never bypasses Android access controls.
 */
object XilonMediaSessions {
    enum class Command { TOGGLE, PLAY, PAUSE, NEXT, PREVIOUS }
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

    data class Snapshot(
        val packageName: String,
        val title: String?,
        val artist: String?,
        val state: String
    )

    /** Read-only snapshot of an active session, only after explicit user permission. */
    fun snapshot(context: Context): Snapshot? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val sessions = runCatching {
            manager.getActiveSessions(ComponentName(context, XilonMediaListener::class.java))
        }.getOrNull() ?: return null
        val controller = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        } ?: return null
        val state = when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> "Redare"
            PlaybackState.STATE_PAUSED -> "Pauză"
            else -> "Necunoscut"
        }
        val metadata = controller.metadata
        return Snapshot(
            controller.packageName,
            metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
            state
        )
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
                Command.PLAY -> controls.play()
                Command.PAUSE -> controls.pause()
                Command.NEXT -> controls.skipToNext()
                Command.PREVIOUS -> controls.skipToPrevious()
            }
        } catch (_: SecurityException) {
            return Result(Outcome.DENIED, "Comanda media a fost refuzată.")
        } catch (_: IllegalStateException) {
            return Result(Outcome.UNAVAILABLE, "Sesiunea media nu mai este activă.")
        }
        // Querying immediately can observe the old state; this is evidence only
        // if a different state is already visible, never proof of actual sound.
        val after = controller.playbackState?.state
        if (command in setOf(Command.TOGGLE, Command.PLAY, Command.PAUSE) && after != null && before != after &&
            after in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED)) {
            return Result(Outcome.VERIFIED,
                "Starea sesiunii ${controller.packageName} s-a schimbat: ${if (after == PlaybackState.STATE_PLAYING) "redare" else "pauză"}.")
        }
        // MediaSession state changes are asynchronous. Do not claim playback
        // succeeded merely because a transport control returned.
        return Result(Outcome.DISPATCHED,
            "Comanda a fost transmisă sesiunii media ${controller.packageName}; redarea nu este încă verificată.")
    }
}
