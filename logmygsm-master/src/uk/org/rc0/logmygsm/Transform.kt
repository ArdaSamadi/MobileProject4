
package uk.org.rc0.logmygsm

import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class Transform(_base: Merc28?, _w: Int, _h: Int, _pixel_shift: Int) {
    var base: Merc28?
    var w2: Int
    var h2: Int
    var pixel_shift: Int
    fun X(p: Merc28?): Int {
        return w2 + (p!!.X - base!!.X shr pixel_shift)
    }

    fun Y(p: Merc28?): Int {
        return h2 + (p!!.Y - base!!.Y shr pixel_shift)
    }

    init {
        base = _base
        w2 = _w shr 1
        h2 = _h shr 1
        pixel_shift = _pixel_shift
    }
}