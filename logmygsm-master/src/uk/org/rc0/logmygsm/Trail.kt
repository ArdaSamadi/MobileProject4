
package uk.org.rc0.logmygsm

import android.graphics.Canvas
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.ObjectInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.IOException
import java.util.ArrayList
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

// Meant to be instantiated as a member in the service
class Trail(the_logger: Logger) {
    private val mLogger: Logger
    private var recent: ArrayList<Merc28>? = null
    private var last_point: Merc28? = null
    private var n_old = 0
    private var x_old: IntArray?
    private var y_old: IntArray?
    private val mHistory: History

    internal inner class PointArray {
        var n: Int
        var x: IntArray?
        var y: IntArray?

        constructor() {
            n = 0
            x = null
            y = null
        }

        constructor(nn: Int, xx: IntArray?, yy: IntArray?) {
            n = nn
            if (n > 0) {
                x = xx
                y = yy
            } else {
                x = null
                y = null
            }
        }

        constructor(zz: ArrayList<Merc28?>) {
            n = zz.size()
            if (n > 0) {
                x = IntArray(n)
                y = IntArray(n)
                for (i in 0 until n) {
                    x!![i] = zz.get(i).X
                    y!![i] = zz.get(i).Y
                }
            } else {
                x = null
                y = null
            }
        }
    }

    internal inner class History {
        private var x0: Merc28? = null
        private var x1: Merc28? = null
        fun clear() {
            x0 = null
            x1 = null
        }

        fun add(x: Merc28): Double {
            x1 = x0
            x0 = Merc28(x)
            return if (x1 != null) {
                x0.metres_away(x1)
            } else {
                0.0
            }
        }

        fun estimated_position(): Merc28? {
            val xpred: Int
            val ypred: Int
            return if (x0 != null) {
                if (x1 != null) {
                    // x0 is newer, x1 is older
                    xpred = x0.X * 3 - x1.X shr 1
                    ypred = x0.Y * 3 - x1.Y shr 1
                    Merc28(xpred, ypred)
                } else {
                    x0
                }
            } else {
                null
            }
        }

        fun last_step(): Double {
            return if (x0 != null && x1 != null) {
                x0.metres_away(x1)
            } else {
                0.0
            }
        }

        init {
            clear()
        }
    }

    private fun init() {
        recent = ArrayList<Merc28>()
        last_point = null
        n_old = 0
        x_old = null
        y_old = null
    }

    fun clear() {
        init()
    }

    // Move to subclass of service
    fun save_state_to_file() {
        gather()
        val dir = File("/sdcard/LogMyGsm/prefs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "trail.dat")
        try {
            val oos = ObjectOutputStream(BufferedOutputStream(FileOutputStream(file)))
            oos.writeInt(n_old)
            oos.writeObject(x_old)
            oos.writeObject(y_old)
            oos.close()
        } catch (e: Exception) {
        }
    }

    fun restore_state_from_file() {
        val file = File("/sdcard/LogMyGsm/prefs/trail.dat")
        var failed = false
        init()
        if (file.exists()) {
            try {
                val ois = ObjectInputStream(FileInputStream(file))
                n_old = ois.readInt()
                x_old = ois.readObject()
                y_old = ois.readObject()
                ois.close()
            } catch (e: IOException) {
                failed = true
            } catch (e: ClassNotFoundException) {
                failed = true
            }
        }
        if (failed) {
            init()
        }
        mLogger.announce(String.format("Loaded %d trail points", n_old))
    }

    // Skip points that are too close together to ever be visible on the map display
    fun add_point(p: Merc28): Double {
        val result: Double
        result = mHistory.add(p)
        var do_add = true
        if (last_point != null) {
            // 4 is (28 - (16+8)), i.e. the pixel size at zoom level = 16.
            // 3 is (28 - (17+8)), i.e. the pixel size at zoom level = 17.
            // Also, round it.
            val sx: Int = (p.X - last_point.X shr 2) + 1 shr 1
            val sy: Int = (p.Y - last_point.Y shr 2) + 1 shr 1
            val manhattan: Int = Math.abs(sx) + Math.abs(sy)
            if (manhattan < splot_gap) {
                do_add = false
            }
        }
        if (do_add) {
            recent.add(Merc28(p))
            last_point = Merc28(p)
        }
        return result
    }

    // Internal
    // Keep alternate points from the history, but don't keep point index[0].
    // This guarantees that the oldest data eventually has to decay away, even if
    // the user never clears the trail.  So e.g.
    // out[0,1,2] = in[1,3,5] or in[2,4,6] for n_old = 6 or 7 respectively.
    private fun decimate() {
        val n_new = n_old shr 1
        var xi: Int
        var xo: Int
        val x_new = IntArray(n_new)
        val y_new = IntArray(n_new)
        xi = n_old - 1
        xo = n_new - 1
        while (xi > 0) {
            x_new[xo] = x_old!![xi]
            y_new[xo] = y_old!![xi]
            xi -= 2
            xo--
        }
        n_old = n_new
        x_old = x_new
        y_old = y_new
    }

    private fun gather() {
        // accumulate the 'recent' history onto the 'old' arrays
        val n_recent: Int = recent.size()
        if (n_recent > 0) {

            // TODO : if n_old is too large, do some data reduction here (either
            // thin out the old stuff, or toss the earlier half of it)
            val n_new = n_old + n_recent
            val x_new = IntArray(n_new)
            val y_new = IntArray(n_new)
            if (n_old > 0) {
                System.arraycopy(x_old, 0, x_new, 0, n_old)
                System.arraycopy(y_old, 0, y_new, 0, n_old)
            }
            for (i in 0 until n_recent) {
                x_new[i + n_old] = recent.get(i).X
                y_new[i + n_old] = recent.get(i).Y
            }
            n_old = n_new
            x_old = x_new
            y_old = y_new
            while (n_old > MAX_HISTORY) {
                decimate()
            }
            recent = ArrayList<Merc28>()
            // leave last_point alone
        } else {
            // leave 'old' arrays as they were
        }
    }

    // If this is being requested, it's because the tile cache is being rebuilt, so it's a good time
    // to accumulate the recent points onto the historical list
    fun get_historical(): PointArray {
        return PointArray(n_old, x_old, y_old)
    }

    fun get_estimated_position(): Merc28? {
        return mHistory.estimated_position()
    }

    internal class Upto {
        var lx: Int
        var ly: Int
        var next: Int
        var parity: Int

        init {
            lx = -256
            ly = -256
            next = 0
            parity = 0
        }
    }

    fun draw_recent_trail(c: Canvas, xnw: Int, ynw: Int, pixel_shift: Int, upto: Upto) {
        val n: Int = recent.size()
        for (i in upto.next until n) {
            val p: Merc28 = recent.get(i)
            val sx: Int = p.X - xnw shr pixel_shift
            val sy: Int = p.Y - ynw shr pixel_shift
            var do_add = true
            val manhattan: Int = Math.abs(sx - upto.lx) + Math.abs(sy - upto.ly)
            if (manhattan < splot_gap) {
                do_add = false
            }
            if (do_add) {
                // Don't even bother invoking the library if we're off-screen.
                // // Loose bounds to allow for
                if (sx >= MIN_CENTRE && sy >= MIN_CENTRE && sx < MAX_CENTRE && sy < MAX_CENTRE) {
                    TileStore.render_dot(c, sx.toFloat(), sy.toFloat(), upto.parity)
                    upto.parity = upto.parity xor 1
                    upto.lx = sx
                    upto.ly = sy
                }
            }
        }
        upto.next = n
        return
    }

    fun n_recent(): Int {
        return recent.size()
    }

    companion object {
        // If this is too large, it makes tiling too slow
        // If too low, the historical trail starts to decay too soon.
        private const val MAX_HISTORY = 12 * 1024
        const val splot_gap = 10
        const val splot_radius = 5.0f
        private const val TAG = "Trail"

        // OK unless we ever decide to use HUGE spots for the trail
        const val MIN_CENTRE = -5
        const val MAX_CENTRE = 256 - MIN_CENTRE
    }

    init {
        mLogger = the_logger
        mHistory = History()
        restore_state_from_file()
    }
}