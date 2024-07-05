
package uk.org.rc0.logmygsm

import android.content.Context
import android.util.AttributeSet
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class WaypointEditMap(_context: Context, _attrs: AttributeSet?) : Map(_context, _attrs) {
    fun add_waypoint() {
        Logger.mWaypoints!!.add(display_pos)
        invalidate()
    }

    fun delete_waypoint() {
        if (Logger.mWaypoints!!.delete(display_pos, pixel_shift)) {
            invalidate()
        }
    }

    fun delete_visible_waypoints() {
        val adjusted_pixel_shift: Int
        if (mScaled) {
            adjusted_pixel_shift = pixel_shift - 1
        } else {
            adjusted_pixel_shift = pixel_shift
        }
        if (Logger.mWaypoints!!.delete_visible(display_pos, adjusted_pixel_shift, getWidth(), getHeight())) {
            invalidate()
        }
    }

    fun delete_all_waypoints() {
        Logger.mWaypoints!!.delete_all()
        invalidate()
    }

    fun set_destination_waypoint() {
        Logger.mWaypoints!!.set_destination(display_pos, pixel_shift)
        invalidate()
    }

    fun add_landmark() {
        Logger.mLandmarks!!.add(display_pos)
        invalidate()
    }

    fun delete_landmark() {
        if (Logger.mLandmarks!!.delete(display_pos, pixel_shift)) {
            invalidate()
        }
    }

    fun cut_route() {
        Logger.mWaypoints!!.cut_route(display_pos)
        invalidate()
    }
}