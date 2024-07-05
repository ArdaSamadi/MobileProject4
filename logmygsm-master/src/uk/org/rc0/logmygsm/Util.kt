
package uk.org.rc0.logmygsm

import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object Util {
    const val M_PER_MILE = 1609.3f
    const val M_PER_10MILES = 10.0f * M_PER_MILE
    const val M_PER_100MILES = 100.0f * M_PER_MILE
    const val MILE_PER_M = 1.0f / M_PER_MILE
    fun pretty_distance(d: Float): String {
        val result: String
        if (d < 10000.0f) {
            result = String.format("%4dm", d.toInt())
        } else if (d < M_PER_10MILES) {
            result = String.format("%.2fmi", d * MILE_PER_M)
        } else {
            result = String.format("%.1fmi", d * MILE_PER_M)
        }
        return result
    }
}