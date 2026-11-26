package com.navigator.app.ui.map

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The value a callback-style API hands over later (the SDK posts it to the main thread even when it already has it).
 * The caller's coroutine waits for it, so a page that stops being active meanwhile is cancelled and takes nothing, and a
 * delivery after that is ignored. Checking "am I still active" inside the callback instead would always fail: by then
 * the coroutine that asked has finished.
 */
internal suspend fun <T> awaitDelivery(register: ((T) -> Unit) -> Unit): T = suspendCancellableCoroutine { c ->
    register { value -> if (c.isActive) c.resume(value) }
}
