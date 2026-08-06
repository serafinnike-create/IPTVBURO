package com.lucasserafin94.iptvburo.domain.model

/**
 * Whether the Assinaturas area may be shown at all, and in what state.
 *
 * The rule this exists to keep is that a visible feature must be backed by something real. A
 * "Assinaturas" entry in the sidebar promises the app can answer "where can I watch this"; with no
 * provider wired up it answers nothing, and a dead entry is worse than an absent one.
 *
 * It is deliberately a value the shell computes rather than a flag someone sets: the states below
 * follow from what is actually configured, so there is no way to turn the screen on by declaring it
 * on.
 */
enum class StreamingDiscoveryCapability {
    /**
     * No discovery provider is configured. The area is not offered anywhere in the UI.
     *
     * This is the state every shipping build is in until a real catalogue is connected in a later
     * phase.
     */
    UNAVAILABLE,

    /**
     * Only the synthetic fixture is available, so results are made up.
     *
     * The screen may be shown, but every title it lists carries the DEMO marking, because content
     * presented as a real availability answer when it is invented would be a lie told in the
     * product's own voice.
     */
    DEMO_ONLY,

    /** A real catalogue is configured and answers may be presented as genuine. */
    AVAILABLE,
    ;

    /** Whether the Assinaturas entry appears in navigation. */
    val isVisible: Boolean
        get() = this != UNAVAILABLE

    /**
     * Whether every result must be labelled DEMO.
     *
     * True for [DEMO_ONLY]. The label is not decoration and must not be suppressed by a theme or a
     * compact layout: it is the only thing distinguishing an invented answer from a real one.
     */
    val requiresDemoLabel: Boolean
        get() = this == DEMO_ONLY

    companion object {
        /**
         * The capability implied by what is configured.
         *
         * [hasRealProvider] wins over [hasFixtureProvider]: once a genuine catalogue answers, the
         * fixture is a development aid rather than the source of what the user sees.
         */
        fun of(
            hasRealProvider: Boolean,
            hasFixtureProvider: Boolean = false,
        ): StreamingDiscoveryCapability =
            when {
                hasRealProvider -> AVAILABLE
                hasFixtureProvider -> DEMO_ONLY
                else -> UNAVAILABLE
            }
    }
}
