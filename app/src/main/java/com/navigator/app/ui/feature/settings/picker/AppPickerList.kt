package com.navigator.app.ui.feature.settings.picker

import com.navigator.app.R
import com.navigator.app.ui.feature.settings.UiText

data class AppEntry(val packageName: String, val label: String)

/** One block of the picker; [title] is null for a flat search result. */
data class PickerSection(val title: UiText?, val apps: List<AppEntry>)

/** ST11's ordering: the chosen apps first, then messaging and phone suggestions, then everything A–Z. Searching flattens it. */
object AppPickerList {
    /** Messaging and phone apps riders most often want on the dash. */
    val SUGGESTED: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.truecaller", "com.facebook.orca", "com.google.android.gm", "com.microsoft.office.outlook",
    )

    fun build(apps: List<AppEntry>, selected: Set<String>, query: String, suggested: Set<String> = SUGGESTED): List<PickerSection> {
        val sorted = apps.sortedBy { it.label.lowercase() }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = sorted.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            return if (hits.isEmpty()) emptyList() else listOf(PickerSection(null, hits))
        }
        val chosen = sorted.filter { it.packageName in selected }
        val hints = sorted.filter { it.packageName in suggested && it.packageName !in selected }
        val rest = sorted.filter { it.packageName !in selected && it.packageName !in suggested }
        return buildList {
            if (chosen.isNotEmpty()) add(PickerSection(UiText.Res(R.string.picker_selected), chosen))
            if (hints.isNotEmpty()) add(PickerSection(UiText.Res(R.string.picker_suggested), hints))
            if (rest.isNotEmpty()) add(PickerSection(UiText.Res(R.string.picker_all), rest))
        }
    }
}
