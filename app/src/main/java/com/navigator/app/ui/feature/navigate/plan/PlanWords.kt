package com.navigator.app.ui.feature.navigate.plan

import android.content.res.Resources
import com.navigator.app.R
import com.navigator.app.ride.DurationText

/** The words the pure route mappers put in their lines, so they live in strings.xml and the mappers stay JVM-testable. */
data class PlanWords(val duration: DurationText.Format, val arrive: String, val traffic: String) {
    companion object {
        val English = PlanWords(DurationText.Format.English, "arrive %1\$s", "+%1\$d min traffic")

        fun of(res: Resources) = PlanWords(
            DurationText.Format.of(res), res.getString(R.string.route_arrive_template), res.getString(R.string.route_delay_template),
        )
    }
}
