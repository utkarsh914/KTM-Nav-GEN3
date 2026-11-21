package com.navigator.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import androidx.core.view.WindowCompat

/**
 * The one owner of the status/navigation-bar icon colour. The app theme sets it from the app's Day/Night; a
 * riding surface pushes an override that wins for as long as it is shown, however often the app theme
 * recomposes in the meantime. Main thread only.
 */
object SystemBarIcons {
    private val overrides = ArrayList<Any>()
    private val overrideDark = HashMap<Any, Boolean>()

    /** Whether the bars sit on a dark surface: the newest riding override if any, else the app's [appDark]. */
    fun resolve(appDark: Boolean): Boolean = overrides.lastOrNull()?.let { overrideDark[it] } ?: appDark

    fun push(token: Any, dark: Boolean) {
        if (token !in overrideDark) overrides += token
        overrideDark[token] = dark
    }

    fun pop(token: Any) {
        overrides.remove(token)
        overrideDark.remove(token)
    }

    internal fun apply(view: View, dark: Boolean) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window ?: return
        // Edge-to-edge is enforced, so only the icon appearance needs setting.
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
