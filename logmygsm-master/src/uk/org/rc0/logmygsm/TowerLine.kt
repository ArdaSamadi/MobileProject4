
package uk.org.rc0.logmygsm

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.FileInputStream
import java.lang.Math
import java.util.HashMap
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object TowerLine {
    private var lut: HashMap<String, Merc28>? = null
    private var line_paint: Array<Paint?>
    private var thin_line_paint: Array<Paint?>
    private var text_paint: Paint? = null
    var simOperator: String? = null
    var text_name: String? = null
    var binary_name: String? = null
    private const val PREFS_DIR = "/sdcard/LogMyGsm/prefs/"
    private const val STEM = "cidxy_"
    private const val TEXT = ".txt"
    private const val BINARY = ".dat"
    private const val TAG = "TowerLine"
    private var tmp_pos: Merc28? = null
    private var mActive = false
    private const val N_PAINTS = 3
    private val opacity = intArrayOf(176, 128, 64)
    private val thin_opacity = intArrayOf(192, 128, 64)
    private val line_widths = intArrayOf(8, 4, 4)
    private val thin_line_widths = intArrayOf(2, 1, 1)

    // -------------------------
    private fun setup_filenames(_app_context: Context) {
        val tm: TelephonyManager
        val simOperator: String
        tm = _app_context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        simOperator = tm.getSimOperator()
        text_name = String(PREFS_DIR + STEM + simOperator + TEXT)
        binary_name = String(PREFS_DIR + STEM + simOperator + BINARY)
    }

    private fun load_lut_from_text() {
        lut = HashMap<String, Merc28>()
        val file = File(text_name)
        var n = 0
        if (file.exists()) {
            try {
                val br = BufferedReader(FileReader(file))
                var line: String
                line = br.readLine()
                n = Integer.parseInt(line)
                for (i in 0 until n) {
                    line = br.readLine()
                    val cid: Int = Integer.parseInt(line)
                    line = br.readLine()
                    val lac: Int = Integer.parseInt(line)
                    line = br.readLine()
                    val x: Int = Integer.parseInt(line)
                    line = br.readLine()
                    val y: Int = Integer.parseInt(line)
                    lut.put("$lac,$cid", Merc28(x, y))
                }
                br.close()
            } catch (e: IOException) {
            } catch (e: NumberFormatException) {
            }
        }
    }

    private fun write_lut_to_binary() {
        val file = File(binary_name)
        try {
            val oos = ObjectOutputStream(BufferedOutputStream(FileOutputStream(file)))
            val n: Int = lut.size()
            oos.writeInt(n)
            if (n > 0) {
                for (key in lut.keySet()) {
                    oos.writeObject(key)
                    val temp: Merc28 = lut.get(key)
                    oos.writeInt(temp.X)
                    oos.writeInt(temp.Y)
                }
                //Log.i(TAG, "Wrote " + n + " entries to binary cidxy file");
            }
            oos.close()
        } catch (e: IOException) {
            //Log.i(TAG, "binary write excepted : " + e.getClass().getName() + " : " + e.getMessage());
        }
    }

    private fun read_lut_from_binary() {
        val file = File(binary_name)
        var i = -1
        lut = HashMap<String, Merc28>()
        try {
            val ois = ObjectInputStream(FileInputStream(file))
            val n_obj: Int = ois.readInt()
            //Log.i(TAG, n_obj + " objects in file header");
            i = 0
            while (i < n_obj) {
                val key = ois.readObject() as String
                val X: Int = ois.readInt()
                val Y: Int = ois.readInt()
                //Log.i(TAG, "KEY:" + key + " X:" + X + "Y:" + Y);
                lut.put(key, Merc28(X, Y))
                i++
            }
            ois.close()
            //Log.i(TAG, "Read " + n_obj + " entries from binary cidxy file");
        } catch (e: IOException) {
            //Log.i(TAG, "binary read excepted after " + i + " entries : " + e.getClass().getName() + " : " + e.getMessage());
        } catch (e: ClassNotFoundException) {
            //Log.i(TAG, "binary read got class not found : " + e.getClass().getName() + " : " + e.getMessage());
        }
    }

    private fun load_tower_xy_data() {
        val text = File(text_name)
        val binary = File(binary_name)
        if (!binary.exists() ||
                binary.lastModified() < text.lastModified()) {
            load_lut_from_text()
            write_lut_to_binary()
        } else {
            read_lut_from_binary()
        }
    }

    fun init(_app_context: Context) {
        setup_filenames(_app_context)
        tmp_pos = Merc28(0, 0)
        mActive = false
        line_paint = arrayOfNulls<Paint>(N_PAINTS)
        thin_line_paint = arrayOfNulls<Paint>(N_PAINTS)
        for (i in 0 until N_PAINTS) {
            line_paint[i] = Paint()
            line_paint[i].setStyle(Paint.Style.STROKE)
            line_paint[i].setStrokeWidth(line_widths[i])
            line_paint[i].setColor(Color.argb(opacity[i], 0x00, 0x30, 0x10))
            thin_line_paint[i] = Paint()
            thin_line_paint[i].setStyle(Paint.Style.STROKE)
            thin_line_paint[i].setStrokeWidth(thin_line_widths[i])
            thin_line_paint[i].setColor(Color.argb(thin_opacity[i], 0xff, 0xff, 0xff))
        }
        text_paint = Paint()
        text_paint.setColor(Color.argb(224, 0x38, 0x0, 0x58))
        val face: Typeface = Typeface.DEFAULT_BOLD
        text_paint.setTypeface(face)
        text_paint.setAntiAlias(true)
        text_paint.setTextSize(22)
        load_tower_xy_data()
    }

    const val BASE = 16.0f
    const val TEXT_RADIUS = 70.0f
    fun find_tower_pos(index: Int, tower_pos: Merc28?): Boolean {
        if (Logger.recent_cids == null) {
            // catch the early initialisation case before the service has started
            return false
        }
        val cid: Int = Logger.recent_cids.get(index)!!.cid
        val lac: Int = Logger.recent_cids.get(index)!!.lac
        // uninitialised history entries have cid==-1 : this will never match in
        // the LUT
        val cl = "$lac,$cid"
        return if (lut.containsKey(cl)) {
            tower_pos!!.copy_from(lut.get(cl))
            true
        } else {
            false
        }
    }

    fun is_active(): Boolean {
        return mActive
    }

    fun toggle_active() {
        mActive = if (mActive) false else true
    }

    fun draw_line(c: Canvas, w: Int, h: Int, pixel_shift: Int, display_pos: Merc28?) {
        var i: Int
        i = 2
        while (i >= 0) {
            if (find_tower_pos(i, tmp_pos)) {
                val dx: Int = tmp_pos!!.X - display_pos!!.X shr pixel_shift
                val dy: Int = tmp_pos!!.Y - display_pos!!.Y shr pixel_shift
                val fx = dx.toFloat()
                val fy = dy.toFloat()
                val f = Math.sqrt(fx * fx + fy * fy) as Float
                if (f > BASE) { // else tower is in centre of view
                    val x0 = (w shr 1).toFloat() + BASE * (fx / f)
                    val y0 = (h shr 1).toFloat() + BASE * (fy / f)
                    val x1 = (w shr 1).toFloat() + fx
                    val y1 = (h shr 1).toFloat() + fy
                    c.drawLine(x0, y0, x1, y1, line_paint[i])
                    c.drawLine(x0, y0, x1, y1, thin_line_paint[i])
                    if (i == 0) {
                        val zd: Double = tmp_pos!!.metres_away(display_pos)
                        var caption: String?
                        if (zd < 1000) {
                            caption = String.format("%dm", zd.toInt())
                        } else {
                            caption = String.format("%.1fkm", 0.001 * zd)
                        }
                        val tw: Float = text_paint.measureText(caption)
                        val xt = (w shr 1).toFloat() + TEXT_RADIUS * (fx / f)
                        val yt = (h shr 1).toFloat() + TEXT_RADIUS * (fy / f)
                        c.drawText(caption, xt - 0.5f * tw, yt, text_paint)
                    }
                }
            }
            i--
        }
    }
}