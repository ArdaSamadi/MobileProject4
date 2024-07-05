
package uk.org.rc0.logmygsm

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Color
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
class Landmarks {
    private var points: ArrayList<Merc28>? = null
    private val white_paint: Paint
    private val black_paint: Paint
    private val red_paint: Paint

    // ---------------------------
    fun save_state_to_file() {
        val dir = File("/sdcard/LogMyGsm/prefs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, TAIL)
        try {
            val bw = BufferedWriter(FileWriter(file))
            val n: Int = points.size()
            bw.write(String.format("%d\n", n))
            for (i in 0 until n) {
                bw.write(String.format("%d\n", points.get(i).X))
                bw.write(String.format("%d\n", points.get(i).Y))
            }
            bw.close()
        } catch (e: IOException) {
        }
    }

    private fun restore_state_from_file() {
        points = ArrayList<Merc28>()
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
                    points.add(Merc28(x, y))
                }
                br.close()
            } catch (e: IOException) {
                failed = true
            } catch (n: NumberFormatException) {
                failed = true
            }
        }
        if (failed) {
            points = ArrayList<Merc28>()
        }
    }

    // ---------------------------
    fun add(pos: Merc28) {
        points.add(Merc28(pos))
    }

    private fun find_closest_point(pos: Merc28, pixel_shift: Int): Int {
        var victim: Int
        var closest: Int
        val n: Int = points.size()
        victim = -1
        closest = 0
        for (i in 0 until n) {
            val dx: Int = points.get(i).X - pos.X shr pixel_shift
            val dy: Int = points.get(i).Y - pos.Y shr pixel_shift
            val d: Int = Math.abs(dx) + Math.abs(dy)
            if (victim < 0 ||
                    d < closest) {
                closest = d
                victim = i
            }
        }
        return victim
    }

    // Return value is true if a deletion successfully occurred, false if no point was
    // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
    fun delete(pos: Merc28, pixel_shift: Int): Boolean {
        val victim: Int
        victim = find_closest_point(pos, pixel_shift)
        return if (victim < 0) {
            false
        } else {
            points.remove(victim)
            true
        }
    }

    // pos is the position of the centre-screen
    fun draw(c: Canvas, t: Transform) {
        val n: Int = points.size()
        for (i in 0 until n) {
            val p: Merc28 = points.get(i)
            val x: Int = t.X(p)
            val y: Int = t.Y(p)
            val pa = Path()
            pa.moveTo(x, y)
            pa.lineTo(x + a2, y + a1)
            pa.lineTo(x + a1, y + a2)
            pa.lineTo(x - a1, y - a2)
            pa.lineTo(x - a2, y - a1)
            pa.lineTo(x, y)
            pa.lineTo(x - a1, y + a2)
            pa.lineTo(x - a2, y + a1)
            pa.lineTo(x + a2, y - a1)
            pa.lineTo(x + a1, y - a2)
            pa.close()
            c.drawPath(pa, white_paint)
            pa.reset()
            pa.moveTo(x, y)
            pa.lineTo(x + a2, y + a1)
            pa.lineTo(x + a2, y - a1)
            pa.lineTo(x - a2, y + a1)
            pa.lineTo(x - a2, y - a1)
            pa.lineTo(x, y)
            pa.lineTo(x - a1, y - a2)
            pa.lineTo(x + a1, y - a2)
            pa.lineTo(x - a1, y + a2)
            pa.lineTo(x + a1, y + a2)
            pa.close()
            c.drawPath(pa, black_paint)
            c.drawCircle(x, y, RADIUS, red_paint)
        }
    }

    companion object {
        private const val TAG = "Landmarks"
        private const val TAIL = "landmarks.txt"

        // ---------------------------
        private const val RADIUS = 12
        private const val a1 = 0.38f * RADIUS.toFloat()
        private const val a2 = 0.92f * RADIUS.toFloat()
    }

    init {
        restore_state_from_file()
        black_paint = Paint()
        black_paint.setColor(Color.argb(0xc0, 0x00, 0x00, 0x00))
        black_paint.setStyle(Paint.Style.FILL)
        white_paint = Paint()
        white_paint.setColor(Color.argb(0xc0, 0xff, 0xff, 0xff))
        white_paint.setStyle(Paint.Style.FILL)
        red_paint = Paint()
        red_paint.setColor(Color.argb(0xff, 0xff, 0x00, 0x00))
        red_paint.setStyle(Paint.Style.STROKE)
        red_paint.setStrokeWidth(2)
    }
}