package com.navigator.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile on a phone past onboarding: the critical journeys (tabs, a pushed page, search,
 * the map). `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.navigator.macrobenchmark.BaselineProfileGenerator`,
 * then copy the profile from the device (path in the test output) over `app/src/main/baseline-prof.txt`.
 * The checked-in file starts as package-wide rules so it works before the first collection.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun collect() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        tapTab("Rides")
        tapTab("Places")
        tapTab("Settings")
        runCatching { tap("About.*"); back() }
        tapTab("Navigate")
        runCatching { tap("Where to\\?"); back() }
        panAndZoomMap(seconds = 3)
    }
}
