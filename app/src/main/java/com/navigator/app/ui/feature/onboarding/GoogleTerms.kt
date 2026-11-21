package com.navigator.app.ui.feature.onboarding

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.TermsAndConditionsCheckOption
import com.google.android.libraries.navigation.TermsAndConditionsUIParams
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.theme.KtmColors

/** Google's navigation terms (ON3): the SDK shows its own dialog; only the colours are ours. */
object GoogleTerms {
    fun accepted(context: Context): Boolean {
        GoogleNavSdkController.ensureApiKey()
        return NavigationApi.areTermsAccepted(context.applicationContext as Application)
    }

    fun show(activity: Activity, appName: String, colors: KtmColors, accentText: androidx.compose.ui.graphics.Color, onAnswer: (Boolean) -> Unit) {
        GoogleNavSdkController.ensureApiKey()
        val params = TermsAndConditionsUIParams.builder()
            .setBackgroundColor(colors.surf.toArgb())
            .setTitleColor(colors.fg.toArgb())
            .setMainTextColor(colors.fg2.toArgb())
            .setAcceptButtonTextColor(accentText.toArgb())
            .setCancelButtonTextColor(colors.fg2.toArgb())
            .build()
        NavigationApi.showTermsAndConditionsDialog(
            activity, appName, appName, params, { accepted -> onAnswer(accepted) }, TermsAndConditionsCheckOption.ENABLED,
        )
    }
}
