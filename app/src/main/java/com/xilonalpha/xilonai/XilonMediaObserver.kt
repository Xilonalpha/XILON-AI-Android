package com.xilonalpha.xilonai

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/**
 * Watches active Android media sessions after explicit notification-listener
 * authorization. Does not inspect notification contents or send data off-device.
 */
class XilonMediaObserver(
    private val context: Context,
    private val onUpdate: (XilonMediaSessions.Snapshot?) -> Unit
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val component = ComponentName(context, XilonMediaListener::class.java)
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var closed = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        if (!closed) select(sessions.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (!closed) refreshSelection()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (!closed) publish()
        }
        override fun onSessionDestroyed() {
            if (!closed) refreshSelection()
        }
    }

    fun start(): Boolean {
        if (closed || !XilonMediaSessions.hasAccess(context)) return false
        val service = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return false
        if (manager == null) {
            val registered = runCatching {
                service.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            }.isSuccess
            if (!registered) return false
            manager = service
        }
        refreshSelection()
        // Registration succeeded even if no player is currently active.
        return true
    }

    private fun refreshSelection() {
        if (closed) return
        val service = manager ?: return
        val sessions = runCatching { service.getActiveSessions(component) }
            .getOrNull() ?: run {
                detach()
                onUpdate(null)
                return
            }
        select(sessions)
    }

    private fun select(sessions: List<MediaController>) {
        if (closed) return
        val selected = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        }
        if (selected == null) {
            detach()
            onUpdate(null)
            return
        }
        if (controller?.sessionToken != selected.sessionToken) {
            detach()
            val registered = runCatching {
                selected.registerCallback(callback, handler)
            }.isSuccess
            if (!registered) {
                onUpdate(null)
                return
            }
            controller = selected
        }
        publish()
    }

    private fun publish() {
        if (closed) return
        val active = controller ?: return
        val state = when (active.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> "Redare"
            PlaybackState.STATE_PAUSED -> "Pauză"
            else -> "Necunoscut"
        }
        val metadata = active.metadata
        onUpdate(XilonMediaSessions.Snapshot(
            active.packageName,
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            state
        ))
    }

    private fun detach() {
        controller?.let { runCatching { it.unregisterCallback(callback) } }
        controller = null
    }

    override fun close() {
        if (closed) return
        closed = true
        manager?.let { runCatching { it.removeOnActiveSessionsChangedListener(sessionsListener) } }
        manager = null
        detach()
    }
}
