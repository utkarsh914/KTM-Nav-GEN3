package com.navigator.app.ui.map

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/**
 * The first value at once, every later one only after it has held still for [settleMs], and never the value already
 * delivered. A panel that resizes every frame (lanes appearing, text wrapping) then re-pads the map once, not per frame.
 */
internal fun <T> Flow<T>.settled(settleMs: Long): Flow<T> = flow {
    var first = true
    emitAll(
        distinctUntilChanged()
            .transformLatest { value ->
                if (first) first = false else delay(settleMs)
                emit(value)
            }
            .distinctUntilChanged(),
    )
}
