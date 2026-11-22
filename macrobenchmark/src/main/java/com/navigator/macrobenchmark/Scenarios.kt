package com.navigator.macrobenchmark

import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val PACKAGE = "com.navigator.ktm"
private const val WAIT_MS = 3_000L

/** `-Pandroid.testInstrumentationRunnerArguments.compilation=none|default|full` (default: baseline profile applied). */
fun compilationFromArguments(): CompilationMode =
    when (InstrumentationRegistry.getArguments().getString("compilation")) {
        "none" -> CompilationMode.None()
        "full" -> CompilationMode.Full()
        else -> CompilationMode.DEFAULT
    }

fun MacrobenchmarkRule.measureScenario(
    iterations: Int = 10,
    setup: MacrobenchmarkScope.() -> Unit = {},
    block: MacrobenchmarkScope.() -> Unit,
) = measureRepeated(
    packageName = PACKAGE,
    metrics = listOf(FrameTimingMetric()),
    compilationMode = compilationFromArguments(),
    startupMode = StartupMode.WARM,
    iterations = iterations,
    setupBlock = {
        pressHome()
        startActivityAndWait()
        setup()
    },
    measureBlock = block,
)

/** Taps the first element whose text matches; fails the iteration if the app is not past onboarding. */
fun MacrobenchmarkScope.tap(text: String) {
    val node = device.wait(Until.findObject(By.text(Pattern.compile(text))), WAIT_MS)
        ?: error("'$text' not on screen: finish onboarding and pairing on this phone first")
    node.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.tapTab(label: String) {
    val node = device.wait(Until.findObject(By.text(label).clickable(true)), WAIT_MS)
        ?: device.wait(Until.findObject(By.text(label)), WAIT_MS)
        ?: error("tab '$label' not on screen")
    node.click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.panAndZoomMap(seconds: Int = 5) {
    val w = device.displayWidth
    val h = device.displayHeight
    val end = System.currentTimeMillis() + seconds * 1_000L
    var flip = false
    while (System.currentTimeMillis() < end) {
        val from = Point(w / 2 + if (flip) -w / 4 else w / 4, h / 2)
        val to = Point(w / 2 + if (flip) w / 4 else -w / 4, h / 2)
        device.swipe(from.x, from.y, to.x, to.y, 24)
        flip = !flip
    }
    device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
}
