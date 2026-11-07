package com.xilonalpha.xilonai

import android.content.Context
import android.media.AudioManager

/**
 * Only verifies changes observable through public Android APIs.
 * Other apps' playback and external activities remain unverified.
 */
object XilonActionVerifier {
    enum class State { VERIFIED, DISPATCHED_UNVERIFIED, FAILED }
    data class Result(val state: State, val message: String)

    fun execute(context: Context, step: XilonActionPlanner.Step): Result {
        val kind = step.proposal.kind
        if (kind in setOf(XilonActions.Kind.PLAY_PAUSE,
                XilonActions.Kind.NEXT_TRACK, XilonActions.Kind.PREVIOUS_TRACK)) {
            val command = when (kind) {
                XilonActions.Kind.PLAY_PAUSE -> XilonMediaSessions.Command.TOGGLE
                XilonActions.Kind.NEXT_TRACK -> XilonMediaSessions.Command.NEXT
                else -> XilonMediaSessions.Command.PREVIOUS
            }
            val result = runCatching {
                XilonMediaSessions.command(context, command)
            }.getOrElse {
                return Result(State.FAILED, "Sesiunea media nu a putut fi accesată.")
            }
            return when (result.outcome) {
                XilonMediaSessions.Outcome.VERIFIED -> Result(State.VERIFIED, result.description)
                XilonMediaSessions.Outcome.DISPATCHED -> Result(State.DISPATCHED_UNVERIFIED, result.description)
                else -> Result(State.FAILED, result.description)
            }
        }
        val volumeAction = kind in setOf(
            XilonActions.Kind.SET_VOLUME,
            XilonActions.Kind.VOLUME_UP,
            XilonActions.Kind.VOLUME_DOWN
        )
        val audio = if (volumeAction) runCatching {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }.getOrNull() else null
        val before = audio?.let { runCatching {
            it.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrNull() }
        val dispatched = runCatching {
            XilonActions.execute(context, step.proposal, step.argument)
        }.getOrDefault(false)
        if (!dispatched) return Result(State.FAILED, "Android a respins comanda.")
        if (!volumeAction || audio == null) return Result(
            State.DISPATCHED_UNVERIFIED,
            "Comandă trimisă. Rezultatul în aplicația externă nu poate fi confirmat."
        )
        val after = runCatching {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrNull() ?: return Result(
            State.DISPATCHED_UNVERIFIED, "Volumul nu a putut fi citit după comandă."
        )
        val max = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(0)
        if (max <= 0) return Result(State.DISPATCHED_UNVERIFIED,
            "Volumul media nu poate fi verificat.")
        val expected = when (kind) {
            XilonActions.Kind.SET_VOLUME -> {
                val percentage = Regex("""(\d{1,3})\s*%""").find(step.argument)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: return Result(State.DISPATCHED_UNVERIFIED, "Procentul nu poate fi verificat.")
                (max * percentage + 50) / 100
            }
            XilonActions.Kind.VOLUME_UP -> if (before != null) (before + 1).coerceAtMost(max) else null
            XilonActions.Kind.VOLUME_DOWN -> if (before != null) (before - 1).coerceAtLeast(0) else null
            else -> null
        }
        // Volume step size is device-specific; verify directional change instead of
        // assuming every adjustment is exactly one index.
        val verified = when (kind) {
            XilonActions.Kind.SET_VOLUME -> expected == after
            XilonActions.Kind.VOLUME_UP -> before != null && after >= before
            XilonActions.Kind.VOLUME_DOWN -> before != null && after <= before
            else -> false
        }
        return if (verified) Result(State.VERIFIED,
            "Volum media verificat: ${(after * 100 + max / 2) / max}%.")
        else Result(State.DISPATCHED_UNVERIFIED,
            "Comanda a fost trimisă, dar volumul observat nu confirmă schimbarea.")
    }
}
