package com.lucasserafin94.iptvburo.domain.model

/**
 * How many speakers the player should feed, and whether to simulate space on headphones.
 *
 * ## Why this is not simply "7.1 mode"
 *
 * A stereo track sent to eight speakers is an *upmix*: the sound occupies more of them, but no
 * information is created. Labelling that "7.1" tells the customer they are hearing something they
 * are not, so the names here describe the **output** being asked for, and the app shows the track's
 * real channel count separately.
 *
 * Headphones are the honest exception. VLC's binaural filter genuinely places sound around the
 * listener using two channels, and the difference is audible — so it is offered as its own mode
 * rather than hidden inside a speaker count.
 *
 * ## The failure that matters
 *
 * Asking for more speakers than the sound card is configured for can *silence* playback rather than
 * improve it. [SYSTEM] therefore exists and is the default: it means "do not interfere", which is
 * the only choice guaranteed to produce sound on every machine.
 */
enum class AudioOutputMode(
    /** Value for VLC's `--directx-audio-speaker`, or null when the system default is left alone. */
    val directXSpeaker: String?,
    /** Whether the binaural headphone filter is enabled. */
    val binaural: Boolean = false,
) {
    /** Whatever Windows is configured for. Always safe, and the default for that reason. */
    SYSTEM(directXSpeaker = null),

    STEREO(directXSpeaker = "Stereo"),

    /** Upmix to six speakers. Honest name: it spreads the sound, it does not add detail. */
    SURROUND_51(directXSpeaker = "5.1"),

    /** Upmix to eight speakers. */
    SURROUND_71(directXSpeaker = "7.1"),

    /**
     * Two channels, positioned around the listener.
     *
     * The one mode that genuinely adds something to a stereo track, because the effect it simulates
     * is exactly what two speakers strapped to a head cannot otherwise produce.
     */
    HEADPHONES(directXSpeaker = "Stereo", binaural = true),
    ;

    /**
     * The VLC arguments for this mode.
     *
     * Empty for [SYSTEM]: passing nothing is materially different from passing a default, because
     * any explicit speaker setting overrides a working Windows configuration.
     */
    fun vlcArguments(): List<String> =
        buildList {
            directXSpeaker?.let { speaker -> add("--directx-audio-speaker=$speaker") }
            if (binaural) {
                add("--audio-filter=headphone")
                // Compensates for the fact that headphones sit against the ears rather than across
                // a room; without it the effect is present but muddy.
                add("--headphone-compensate")
            }
        }

    companion object {
        /**
         * The modes worth offering for a track with [channels] channels.
         *
         * A track that already carries 5.1 is not offered an upmix to 5.1 — there is nothing to
         * upmix — but it is still offered headphones, because binaural rendering of a real 5.1 mix
         * is the case that effect was designed for.
         */
        fun optionsFor(channels: Int): List<AudioOutputMode> =
            buildList {
                add(SYSTEM)
                add(STEREO)
                if (channels <= 2) add(SURROUND_51)
                if (channels <= 6) add(SURROUND_71)
                add(HEADPHONES)
            }
    }
}
