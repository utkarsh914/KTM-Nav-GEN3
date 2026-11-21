package com.navigator.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity behind a (possibly wrapped) context, or null. */
internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
