
package uk.org.rc0.logmygsm

import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

// A single map tile that will get fetched
internal class Target(var zoom: Int, var x: Int, var y: Int) {
    @Override
    fun equals(other: Object): Boolean {
        val o = other as Target
        return if (zoom == o.zoom &&
                x == o.x &&
                y == o.y) {
            true
        } else {
            false
        }
    }

    @Override
    override fun hashCode(): Int {
        var result = 51
        result += 23 * zoom
        result += 37 * x
        result += 43 * y
        return result
    }
}