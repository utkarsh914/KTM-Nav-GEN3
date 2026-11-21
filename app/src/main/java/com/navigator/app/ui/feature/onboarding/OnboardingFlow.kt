package com.navigator.app.ui.feature.onboarding

import com.navigator.app.data.settings.NavEngine

/** The first-run steps in order. Pairing (ON6 to ON9) and Ready (ON10) are their own routes after these. */
enum class OnboardingStep { WELCOME, ENGINE, TERMS, NAME, PERMISSIONS }

/** The pure rules for which steps a rider sees and how they move between them. */
object OnboardingFlow {
    /**
     * Without Google's navigation (no API key, or no Play services) there is no engine to choose: mirror mode, no terms.
     * The terms step is only for Plan in this app, and only until Google has them once.
     */
    fun steps(engineAvailable: Boolean, engine: NavEngine, termsAccepted: Boolean): List<OnboardingStep> = buildList {
        add(OnboardingStep.WELCOME)
        if (engineAvailable) {
            add(OnboardingStep.ENGINE)
            if (engine == NavEngine.IN_APP && !termsAccepted) add(OnboardingStep.TERMS)
        }
        add(OnboardingStep.NAME)
        add(OnboardingStep.PERMISSIONS)
    }

    /** By order, not position, so it still works when the current step has just left the list (terms declined). */
    fun next(steps: List<OnboardingStep>, current: OnboardingStep): OnboardingStep? = steps.firstOrNull { it.ordinal > current.ordinal }

    fun previous(steps: List<OnboardingStep>, current: OnboardingStep): OnboardingStep? = steps.lastOrNull { it.ordinal < current.ordinal }

    /**
     * (position, total) for the progress bar (ON1 to ON10): Engine, Name, Permissions and Pairing count; the welcome and
     * Google's terms do not (the terms sit under Engine). Null on the welcome itself.
     */
    fun progress(steps: List<OnboardingStep>, current: OnboardingStep): Pair<Int, Int>? {
        if (current == OnboardingStep.WELCOME) return null
        val counted = steps.filter { it != OnboardingStep.WELCOME && it != OnboardingStep.TERMS }
        val position = counted.count { it.ordinal <= current.ordinal }.coerceAtLeast(1)
        return position to counted.size + 1
    }

    /** The bar on the pairing screens: the last segment of the same bar. */
    fun pairingProgress(engineAvailable: Boolean): Pair<Int, Int> = (if (engineAvailable) 4 else 3).let { it to it }
}
