package com.navigator.app.app

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {
    @Test fun containerIsSharedAndServesRepositories() {
        val ctx = RuntimeEnvironment.getApplication()
        assertSame(ctx.appContainer, ctx.applicationContext.appContainer)
        assertTrue(ctx.appContainer.appScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
        assertTrue(ctx.appContainer.permissions.state.value.items.isNotEmpty())
    }
}
