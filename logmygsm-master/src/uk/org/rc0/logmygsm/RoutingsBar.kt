
package uk.org.rc0.logmygsm

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class RoutingsBar(private val text_size: Int) {
    private val dest_arrow_1: Float
    private val dest_arrow_2: Float
    private val dest_text: Paint
    private val dest_backdrop: Paint
    private val dest_arrow: Paint
    private fun draw_arrow(c: Canvas, ox: Float, oy: Float, route: Waypoints.Routing?) {
        val ux: Float = route!!.ux
        val uy: Float = route!!.uy
        val xx0 = ox + dest_arrow_1 * ux
        val xx1 = ox - dest_arrow_1 * ux - dest_arrow_2 * uy
        val xx2 = ox - dest_arrow_1 * ux + dest_arrow_2 * uy
        val yy0 = oy + dest_arrow_1 * uy
        val yy1 = oy - dest_arrow_1 * uy + dest_arrow_2 * ux
        val yy2 = oy - dest_arrow_1 * uy - dest_arrow_2 * ux
        val path = Path()
        path.moveTo(xx0, yy0)
        path.lineTo(xx1, yy1)
        path.lineTo(xx2, yy2)
        c.drawPath(path, dest_arrow)
    }

    fun show_routings(c: Canvas, w: Int, h: Int, centre: Merc28?, button_size: Int) {
        val routes: Array<Waypoints.Routing?> = Logger.mWaypoints!!.get_routings(centre)
        if (routes != null) {
            val x0 = 0.0f
            val x1 = w.toFloat()
            val y0 = (h - button_size).toFloat()
            val y1 = h.toFloat()
            c.drawRect(x0, y0, x1, y1, dest_backdrop)
            if (routes.size == 1) {
                val distance: String
                distance = Util.pretty_distance(routes[0]!!.d)
                val swidth: Float = dest_text.measureText(distance)
                val ox: Float
                ox = if (routes[0]!!.ux >= 0) {
                    (w shr 1).toFloat() + 0.5f * swidth + dest_arrow_1
                } else {
                    (w shr 1).toFloat() - 0.5f * swidth - dest_arrow_1
                }
                val oy = (h - (button_size shr 1)) as Float
                c.drawText(distance, (w shr 1).toFloat(), (h - 16) as Float, dest_text)
                draw_arrow(c, ox, oy, routes[0])
            } else {
                // presumably 2
                val left: Waypoints.Routing?
                val right: Waypoints.Routing?
                if (routes[0]!!.ux > routes[1]!!.ux) {
                    left = routes[1]
                    right = routes[0]
                } else {
                    left = routes[0]
                    right = routes[1]
                }
                val distanceA: String
                val distanceB: String
                distanceA = Util.pretty_distance(left!!.d)
                distanceB = Util.pretty_distance(right!!.d)
                val distance = "$distanceA $distanceB"
                val swidth: Float = dest_text.measureText(distance)
                val oxA = (w shr 1).toFloat() - 0.5f * swidth - dest_arrow_1
                val oxB = (w shr 1).toFloat() + 0.5f * swidth + dest_arrow_1
                val oy = (h - (button_size shr 1)) as Float
                c.drawText(distance, (w shr 1).toFloat(), (h - 16) as Float, dest_text)
                draw_arrow(c, oxA, oy, left)
                draw_arrow(c, oxB, oy, right)
            }
        }
    }

    init {
        val height = 1.5f * text_size
        dest_arrow_1 = 0.3f * height
        dest_arrow_2 = 0.5f * dest_arrow_1
        dest_text = Paint()
        dest_text.setColor(Color.argb(0xc0, 0x00, 0x30, 0x10))
        dest_text.setTextSize(text_size)
        dest_text.setTextAlign(Paint.Align.CENTER)
        dest_text.setTypeface(Typeface.DEFAULT_BOLD)
        dest_backdrop = Paint()
        dest_backdrop.setColor(Color.argb(0x60, 0xff, 0xff, 0xff))
        dest_backdrop.setStyle(Paint.Style.FILL)
        dest_arrow = Paint()
        dest_arrow.setColor(Color.argb(0xc0, 0x00, 0x30, 0x10))
        dest_arrow.setStyle(Paint.Style.FILL)
    }
}