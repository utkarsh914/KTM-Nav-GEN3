package com.navigator.app.ui.feature.onboarding

import com.navigator.app.data.settings.NavEngine
import com.navigator.app.ui.feature.onboarding.OnboardingStep.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFlowTest {
    @Test fun withoutAnEngineChoiceThereIsNoEngineOrTermsStep() {
        assertEquals(listOf(WELCOME, NAME, PERMISSIONS), OnboardingFlow.steps(false, NavEngine.MIRROR, false))
        assertEquals(listOf(WELCOME, NAME, PERMISSIONS), OnboardingFlow.steps(false, NavEngine.IN_APP, false))
    }

    @Test fun inAppNavigationAsksForGoogleTermsOnce() {
        assertEquals(listOf(WELCOME, ENGINE, TERMS, NAME, PERMISSIONS), OnboardingFlow.steps(true, NavEngine.IN_APP, false))
        assertEquals(listOf(WELCOME, ENGINE, NAME, PERMISSIONS), OnboardingFlow.steps(true, NavEngine.IN_APP, true))
    }

    @Test fun mirrorModeSkipsTheTerms() {
        assertEquals(listOf(WELCOME, ENGINE, NAME, PERMISSIONS), OnboardingFlow.steps(true, NavEngine.MIRROR, false))
    }

    @Test fun nextAndPreviousFollowTheList() {
        val steps = OnboardingFlow.steps(true, NavEngine.IN_APP, false)
        assertEquals(ENGINE, OnboardingFlow.next(steps, WELCOME))
        assertEquals(TERMS, OnboardingFlow.next(steps, ENGINE))
        assertNull(OnboardingFlow.next(steps, PERMISSIONS))
        assertNull(OnboardingFlow.previous(steps, WELCOME))
        assertEquals(TERMS, OnboardingFlow.previous(steps, NAME))
    }

    @Test fun leavingTheTermsStepAfterItDroppedOutOfTheListStillMovesOn() {
        val mirror = OnboardingFlow.steps(true, NavEngine.MIRROR, false)
        assertEquals(NAME, OnboardingFlow.next(mirror, TERMS))
        assertEquals(ENGINE, OnboardingFlow.previous(mirror, TERMS))
    }

    @Test fun progressStartsAfterTheWelcome() {
        val steps = OnboardingFlow.steps(true, NavEngine.IN_APP, false)
        assertNull(OnboardingFlow.progress(steps, WELCOME))
        assertEquals(1 to 4, OnboardingFlow.progress(steps, ENGINE))
        assertEquals(4 to 4, OnboardingFlow.progress(steps, PERMISSIONS))
        assertEquals(1 to 2, OnboardingFlow.progress(OnboardingFlow.steps(false, NavEngine.MIRROR, true), NAME))
    }
}
