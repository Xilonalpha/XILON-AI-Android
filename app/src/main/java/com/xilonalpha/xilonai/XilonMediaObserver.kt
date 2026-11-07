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
 * Observes an authorized active media session without polling or reading
 * notifications. Call close() from the UI lifecycle to unregister callbacks.
 */
class XilonMediaObserver(
    private val context: Context,
    private val onUpdate: (XilonMediaSessions.Snapshot?) -> Unit
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var closed = false
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            detach()
            onUpdate(null)
        }
    }

    fun start(): Boolean {
        if (closed || !XilonMediaSessions.hasAccess(context)) return false
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return false
        val sessions = runCatching {
            manager.getActiveSessions(ComponentName(context, XilonMediaListener::class.java))
        }.getOrNull() ?: return false
        val selected = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        } ?: run {
            detach()
            onUpdate(null)
            return false
        }
        if (controller?.sessionToken != selected.sessionToken) {
            detach()
            controller = selected
            selected.registerCallback(callback, handler)
        }
        publish()
        return true
    }

    private fun publish() {
        if (closed) return
        val active = controller ?: return
        val state = when (active.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> "Redare"
            PlaybackState.STATE_PAUSED -> "Pauză"
            else -> "Necunoscut"
        }
        onUpdate(XilonMediaSessions.Snapshot(
            active.packageName,
            active.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            active.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            state
        ))
    }

    private fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
    }

    override fun close() {
        closed = true
        detach()
    }
}
