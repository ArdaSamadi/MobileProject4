// Copyright (c) 2012, 2013 Richard P. Curnow
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the <organization> nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package uk.org.rc0.logmygsm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.DashPathEffect
import android.util.FloatMath
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.Math
import java.util.ArrayList
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

// Storage for the the waypoints that the user can define
class Waypoints {
    // ---------------------------
    internal class Point : Merc28 {
        var zombie: Boolean
        var index: Int

        constructor(pos: Merc28) : super(pos) {
            zombie = false
            index = -1
        }

        constructor(x: Int, y: Int) : super(x, y) {
            zombie = false
            index = -1
        }
    }

    internal class Strut(var p0: Point, var p1: Point)

    private var points: ArrayList<Point>? = null
    private var struts // the edges that the user has deleted by hand
            : ArrayList<Strut>? = null
    private var destination: Point? = null
    private var mLinkages: Linkages? = null
    private val marker_paint: Paint
    private val thick_marker_paint: Paint
    private val track_paint: Paint
    private val strut_paint: Paint

    // ---------------------------
    fun save_state_to_file() {
        val dir = File("/sdcard/LogMyGsm/prefs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, TAIL)
        try {
            val bw = BufferedWriter(FileWriter(file))
            var n: Int = points.size()
            bw.write(String.format("%d\n", n))
            for (i in 0 until n) {
                bw.write(String.format("%d\n", points.get(i).X))
                bw.write(String.format("%d\n", points.get(i).Y))
            }
            if (destination == null) {
                bw.write("-1\n")
            } else {
                bw.write(String.format("%d\n", destination!!.index))
            }
            n = struts.size()
            bw.write(String.format("%d\n", n))
            for (i in 0 until n) {
                bw.write(String.format("%d\n", struts.get(i).p0.index))
                bw.write(String.format("%d\n", struts.get(i).p1.index))
            }
            bw.close()
        } catch (e: IOException) {
        }
    }

    private fun restore_state_from_file() {
        points = ArrayList<Point>()
        struts = ArrayList<Strut>()
        mLinkages = null
        val file = File("/sdcard/LogMyGsm/prefs/" + TAIL)
        var failed = false
        if (file.exists()) {
            try {
                val br = BufferedReader(FileReader(file))
                var line: String
                line = br.readLine()
                val n: Int = Integer.parseInt(line)
                for (i in 0 until n) {
                    line = br.readLine()
                    val x: Int = Integer.parseInt(line)
                    line = br.readLine()
                    val y: Int = Integer.parseInt(line)
                    points.add(Point(x, y))
                }
                line = br.readLine()
                val destination_index: Int = Integer.parseInt(line)
                destination = if (destination_index < 0 || destination_index >= points.size()) {
                    null
                } else {
                    points.get(destination_index)
                }
                line = br.readLine()
                val n_struts: Int = Integer.parseInt(line)
                for (i in 0 until n_struts) {
                    line = br.readLine()
                    val i0: Int = Integer.parseInt(line)
                    line = br.readLine()
                    val i1: Int = Integer.parseInt(line)
                    struts.add(Strut(points.get(i0), points.get(i1)))
                }
                br.close()
            } catch (e: IOException) {
                failed = true
            } catch (n: NumberFormatException) {
                failed = true
            }
        }
        if (failed) {
            points = ArrayList<Point>()
            struts = ArrayList<Strut>()
            destination = null
        }
        tidy()
    }

    // ---------------------------
    private fun tidy() {
        var n: Int
        // Bring the points array and everything referencing its entries back into a clean state
        if (destination != null && destination!!.zombie) {
            destination = null
        }
        val new_struts: ArrayList<Strut> = ArrayList<Strut>()
        n = struts.size()
        for (i in 0 until n) {
            val s: Strut = struts.get(i)
            if (!s.p0.zombie && !s.p1.zombie) {
                new_struts.add(s)
            }
        }
        struts = new_struts
        val new_points: ArrayList<Point> = ArrayList<Point>()
        n = points.size()
        var i = 0
        var new_index = 0
        while (i < n) {
            val p: Point = points.get(i)
            if (!p.zombie) {
                p.index = new_index++
                new_points.add(p)
            }
            i++
        }
        points = new_points
        mLinkages = null
    }

    // ---------------------------
    fun add(pos: Merc28) {
        points.add(Point(pos))
        tidy() // not very efficient!
    }

    private fun find_closest_point(pos: Merc28, pixel_shift: Int): Point? {
        var victim: Point? = null
        var closest: Int
        val n: Int = points.size()
        closest = 0
        for (i in 0 until n) {
            val p: Point = points.get(i)
            val dx: Int = p.X - pos.X shr pixel_shift
            val dy: Int = p.Y - pos.Y shr pixel_shift
            val d: Int = Math.abs(dx) + Math.abs(dy)
            if (victim == null ||
                    d < closest) {
                closest = d
                victim = p
            }
        }
        return victim
    }

    // Return value is true if a deletion successfully occurred, false if no point was
    // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
    fun delete(pos: Merc28, pixel_shift: Int): Boolean {
        val victim: Point?
        victim = find_closest_point(pos, pixel_shift)
        return if (victim == null) {
            false
        } else {
            victim.zombie = true
            tidy()
            true
        }
    }

    fun delete_visible(pos: Merc28, pixel_shift: Int, width: Int, height: Int): Boolean {
        val w2 = width shr 1
        val h2 = height shr 1
        val n: Int = points.size()
        var did_any = false
        // work from the top down so that the indices of the points still to do
        // stay the same.
        for (i in n - 1 downTo 0) {
            val p: Point = points.get(i)
            val dx: Int = p.X - pos.X shr pixel_shift
            val dy: Int = p.Y - pos.Y shr pixel_shift
            if (Math.abs(dx) < w2 &&
                    Math.abs(dy) < h2) {
                did_any = true
                p.zombie = true
            }
        }
        if (did_any) {
            tidy()
        }
        return did_any
    }

    fun delete_all() {
        val n: Int = points.size()
        for (i in 0 until n) {
            points.get(i).zombie = true
        }
        tidy()
    }

    // ---------------------------
    fun set_destination(pos: Merc28, pixel_shift: Int): Boolean {
        destination = find_closest_point(pos, pixel_shift)
        mLinkages = null // crude way to force recalculation of mesh + minumum distances
        return true
    }

    // ---------------------------
    // ---------------------------
    fun cut_route(pos: Merc28) {
        //Log.i(TAG, "Cutting a route");
        calculate_linkages()
        val indices: IntArray = mLinkages!!.nearest_edge(pos)
        if (indices != null) {
            //Log.i(TAG, "Indices are " + indices[0] + " and " + indices[1]);
            struts.add(Strut(points.get(indices[0]), points.get(indices[1])))
        }
        mLinkages = null
    }

    // pos is the position of the centre-screen
    fun draw(c: Canvas, t: Transform, do_show_track: Boolean) {
        var n: Int = points.size()
        for (i in 0 until n) {
            val p: Merc28 = points.get(i)
            val x: Int = t.X(p)
            val y: Int = t.Y(p)
            if (destination != null && i == destination!!.index) {
                c.drawCircle(x, y, RADIUS2.toFloat(), thick_marker_paint)
            }
            c.drawCircle(x, y, RADIUS.toFloat(), marker_paint)
            c.drawPoint(x, y, marker_paint)
        }
        n = struts.size()
        for (i in 0 until n) {
            val s: Strut = struts.get(i)
            val x0: Int = t.X(s.p0)
            val x1: Int = t.X(s.p1)
            val y0: Int = t.Y(s.p0)
            val y1: Int = t.Y(s.p1)
            c.drawLine(x0, y0, x1, y1, strut_paint)
        }
        if (do_show_track) {
            draw_track(c, t)
        }
    }

    // ---------------------------
    private fun calculate_linkages() {
        if (mLinkages == null) {
            mLinkages = Linkages(points, destination, struts)
        }
    }

    // ---------------------------
    private fun draw_track(c: Canvas, t: Transform) {
        calculate_linkages()
        val edges: Array<Linkages.Edge?> = mLinkages!!.get_edges()
        for (i in edges.indices) {
            val m0: Merc28 = edges[i]!!.m0
            val m1: Merc28 = edges[i]!!.m1
            c.drawLine(t.X(m0), t.Y(m0), t.X(m1), t.Y(m1), track_paint)
        }
    }

    // ---------------------------
    fun get_edges(): Array<Linkages.Edge?> {
        calculate_linkages()
        return mLinkages!!.get_edges()
    }

    // ---------------------------
    class Routing(here: Merc28?, there: Merc28?, onward_distance: Float) {
        // to return the direction to the next waypoint and the total
        // distance to the destination
        var ux: Float
        var uy // unit vector towards next waypoint
                : Float

        //
        // distance to destination in metres, through the mesh plus the
        // direct path from the given point to the waypoint
        var d: Float

        init {
            ux = (there!!.X - here!!.X) as Float
            uy = (there!!.Y - here!!.Y) as Float
            val scale: Float = 1.0f / FloatMath.sqrt(ux * ux + uy * uy)
            ux *= scale
            uy *= scale
            d = here!!.metres_away(there) as Float + onward_distance
        }
    }

    fun get_routings(pos: Merc28?): Array<Routing?>? {
        return mLinkages!!.get_routings(pos)
    }

    companion object {
        private const val TAG = "Waypoints"
        private const val TAIL = "waypoints.txt"

        // ---------------------------
        private const val RADIUS = 7
        private const val RADIUS2 = RADIUS + RADIUS
    }

    init {
        restore_state_from_file()
        marker_paint = Paint()
        marker_paint.setStrokeWidth(3)
        marker_paint.setColor(Color.argb(0xa0, 0x80, 0x00, 0x20))
        marker_paint.setStyle(Paint.Style.STROKE)
        thick_marker_paint = Paint()
        thick_marker_paint.setStrokeWidth(6)
        thick_marker_paint.setColor(Color.argb(0xa0, 0x80, 0x00, 0x20))
        thick_marker_paint.setStyle(Paint.Style.STROKE)
        track_paint = Paint()
        track_paint.setStrokeWidth(14)
        track_paint.setColor(Color.argb(0x38, 0x80, 0x00, 0x20))
        track_paint.setStyle(Paint.Style.STROKE)
        track_paint.setStrokeCap(Paint.Cap.ROUND)
        strut_paint = Paint()
        strut_paint.setStrokeWidth(1)
        strut_paint.setColor(Color.argb(0xe0, 0x80, 0x00, 0x20))
        strut_paint.setStyle(Paint.Style.STROKE)
        strut_paint.setStrokeCap(Paint.Cap.ROUND)
        val dpe = DashPathEffect(floatArrayOf(2.0f, 2.0f), 0.0f)
        strut_paint.setPathEffect(dpe)
    }
}