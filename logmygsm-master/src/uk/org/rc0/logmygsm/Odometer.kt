
package uk.org.rc0.logmygsm

import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class Odometer {
    private var xlast: Merc28? = null

    // Distance from starting point to xlast
    private var distance_ref = 0.0

    // Distance from starting point to current point (may go down as well as up!)
    private var distance = 0.0
    fun get_metres_covered(): Double {
        return distance
    }

    fun reset() {
        distance_ref = 0.0
        distance = 0.0
        xlast = null
    }

    fun append(xnew: Merc28) {
        if (xlast != null) {
            val step: Double
            step = xlast.metres_away(xnew)
            if (step > THRESHOLD) {
                distance_ref += step
                distance = distance_ref
                xlast = xnew
            } else {
                // Maintain 'best' estimate of distance on every update
                distance = distance_ref + step
            }
        } else {
            xlast = xnew
        }
    }

    companion object {
        // The amount by which the position has to move to increment the distance
        // travelled and replace the reference point.  If too low, noise whilst
        // standing still will get added and make the estimated distance too large.
        // If too high, small features in the track could get dropped (e.g. corners
        // will be cut and the tips of U-turns missed). These would make the
        // estimated distance too small.
        //
        // There's nothing much we can do to avoid side-to-side noise along a
        // straight track.
        const val THRESHOLD = 5.0
    }
}