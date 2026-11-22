package com.navigator.macrobenchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frame-time scenarios of the fluidity plan (overhaul/FLUID_PLAN.md F0), a-d and f. Scenario e (the
 * riding screen) needs a live trip: capture it by hand with `adb shell dumpsys gfxinfo com.navigator.ktm`.
 *
 * Assumes the phone is already past onboarding and pairing. The Rides tab needs at least one saved ride for
 * scenario c. Run: `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FluidityBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** a: tab Navigate -> Rides -> Navigate (the map tab is composed again on the way back). */
    @Test
    fun a_tabs() = rule.measureScenario(setup = { tapTab("Navigate") }) {
        tapTab("Rides")
        tapTab("Navigate")
    }

    /** b: push a Settings sub-page and Back (a list page over a list page). */
    @Test
    fun b_settingsPush() = rule.measureScenario(setup = { tapTab("Settings") }) {
        tap("About.*")
        back()
    }

    /** c: push Replay from a ride card (a map page) and Back. */
    @Test
    fun c_replayPush() = rule.measureScenario(setup = { tapTab("Rides") }) {
        tap(".* km · .*")
        back()
    }

    /** d: Where to? -> search -> Back. */
    @Test
    fun d_search() = rule.measureScenario(setup = { tapTab("Navigate") }) {
        tap("Where to\\?")
        back()
        // The search field opens focused: the first Back may only close the keyboard.
        if (!device.hasObject(By.text("Where to?"))) back()
    }

    /** f: pan the map for five seconds. */
    @Test
    fun f_mapPan() = rule.measureScenario(iterations = 5, setup = { tapTab("Navigate") }) {
        panMap()
    }
}
