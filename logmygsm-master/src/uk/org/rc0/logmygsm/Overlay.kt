
package uk.org.rc0.logmygsm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.Math
import java.util.ArrayList
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object Overlay {
    private const val PREFDIR = "/sdcard/LogMyGsm/prefs"
    private const val TAG = "Overlay"
    private const val VERSION = 2
    private const val MAGIC_NUMBER = -0x1b0aa00 or VERSION
    var p_thick_red: Paint? = null
    var p_thin_red: Paint? = null
    var p_thin_red_noaa: Paint? = null
    var p_trans_red: Paint? = null
    var p_thick_black: Paint? = null
    var p_thin_black: Paint? = null
    var p_thick_black_dash: Paint? = null
    var p_thin_black_dash: Paint? = null
    var p_thin_blue_noaa: Paint? = null
    var p_transparent_grey: Paint? = null
    var p_wash: Paint? = null
    var p_caption_left: Paint? = null
    var p_caption_right: Paint? = null
    var p_colours: Array<Paint?>
    var anglx: FloatArray
    var angly: FloatArray
    var angsx: FloatArray
    var angsy: FloatArray
    val rgb_table = arrayOf(intArrayOf(0xFF, 0x2C, 0x2C), intArrayOf(0x00, 0xD0, 0x00), intArrayOf(0x87, 0x47, 0xFF), intArrayOf(0xD9, 0x78, 0x00), intArrayOf(0x08, 0xD6, 0xD2), intArrayOf(0xFF, 0x30, 0xF6), intArrayOf(0x96, 0xA6, 0x00), intArrayOf(0x0E, 0x7E, 0xDD), intArrayOf(0xFF, 0x9A, 0x8C), intArrayOf(0x91, 0xE5, 0x64), intArrayOf(0xF9, 0x8C, 0xFF), intArrayOf(0x5E, 0x08, 0x57), intArrayOf(0x5A, 0x2C, 0x00), intArrayOf(0x08, 0x45, 0x00), intArrayOf(0x18, 0x25, 0x6B))
    fun init() {
        if (p_thick_red != null) return
        p_thin_red_noaa = Paint()
        p_thin_red_noaa.setStrokeWidth(1)
        p_thin_red_noaa.setColor(Color.RED)
        p_thin_red_noaa.setStyle(Paint.Style.STROKE)
        p_thin_red = Paint(p_thin_red_noaa)
        p_thin_red.setFlags(Paint.ANTI_ALIAS_FLAG)
        p_thick_red = Paint(p_thin_red)
        p_thick_red.setStrokeWidth(2)
        p_trans_red = Paint(Paint.ANTI_ALIAS_FLAG)
        p_trans_red.setStrokeWidth(2)
        p_trans_red.setColor(Color.argb(0x80, 0xff, 0x00, 0x00))
        p_trans_red.setStyle(Paint.Style.FILL)
        p_thick_black = Paint(Paint.ANTI_ALIAS_FLAG)
        p_thick_black.setStrokeWidth(2)
        p_thick_black.setColor(Color.BLACK)
        p_thick_black.setStyle(Paint.Style.STROKE)
        p_thin_black = Paint(Paint.ANTI_ALIAS_FLAG)
        p_thin_black.setStrokeWidth(1)
        p_thin_black.setColor(Color.BLACK)
        p_thin_black.setStyle(Paint.Style.STROKE)
        p_thin_blue_noaa = Paint()
        p_thin_blue_noaa.setStrokeWidth(1)
        p_thin_blue_noaa.setColor(Color.argb(0x80, 0x00, 0x00, 0xff))
        p_thin_blue_noaa.setStyle(Paint.Style.STROKE)
        p_transparent_grey = Paint(Paint.ANTI_ALIAS_FLAG)
        p_transparent_grey.setColor(Color.argb(0x60, 0x70, 0x70, 0xa0))
        p_transparent_grey.setStyle(Paint.Style.FILL)
        val dpe = DashPathEffect(floatArrayOf(2.0f, 2.0f), 0.0f)
        p_thick_black_dash = Paint(p_thick_black)
        p_thick_black_dash.setPathEffect(dpe)
        p_thin_black_dash = Paint(p_thin_black)
        p_thin_black_dash.setPathEffect(dpe)
        p_caption_left = Paint(Paint.ANTI_ALIAS_FLAG)
        p_caption_left.setColor(Color.BLACK)
        p_caption_left.setTextSize(9)
        p_caption_left.setTextAlign(Paint.Align.LEFT)
        p_caption_left.setTypeface(Typeface.DEFAULT_BOLD)
        p_caption_right = Paint(p_caption_left)
        p_caption_right.setTextAlign(Paint.Align.RIGHT)
        p_wash = Paint(Paint.ANTI_ALIAS_FLAG)
        p_wash.setColor(Color.argb(0x80, 0xff, 0xff, 0xff))
        p_wash.setStyle(Paint.Style.FILL)
        val n = rgb_table.size
        val alpha = 0x90
        p_colours = arrayOfNulls<Paint>(n)
        for (i in 0 until n) {
            p_colours[i] = Paint(Paint.ANTI_ALIAS_FLAG)
            p_colours[i].setColor(Color.argb(alpha, rgb_table[i][0], rgb_table[i][1], rgb_table[i][2]))
            p_colours[i].setStyle(Paint.Style.FILL)
        }
        anglx = FloatArray(32)
        angly = FloatArray(32)
        angsx = FloatArray(32)
        angsy = FloatArray(32)
        for (i in 0..31) {
            var ang = i * (360.0 / 32.0)
            ang = Math.toRadians(ang)
            anglx[i] = (8.0 * Math.cos(ang)) as Float
            angly[i] = (8.0 * Math.sin(ang)) as Float
            angsx[i] = (5.0 * Math.cos(ang)) as Float
            angsy[i] = (5.0 * Math.sin(ang)) as Float
        }
    }

    fun apply(bm: Bitmap?, overlay_file: String, overlay_param: Int,
              zoom: Int, tile_x: Int, tile_y: Int) {
        init()
        val the_recipe = Recipe(overlay_file, overlay_param and 7,
                zoom, tile_x, tile_y,
                overlay_param and 8 != 0)
        the_recipe?.apply(bm)
        return
    }

    internal class Recipe(overlay_file: String, overlay_param: Int,
                          zoom: Int, tile_x: Int, tile_y: Int, simplify: Boolean) {
        // ---------------------------
        internal class Feature(var sx: Int, var sy: Int) {
            fun render(c: Canvas?) {}

            init {
                missing[sx][sy] = 0
            }
        }

        internal class Sector     //Log.i(TAG, "circle x=" + sx + " y=" + sy + " size=" + size + " colour=" + colour);
        (_sx: Int, _sy: Int, var size: Int, var colour: Int, var angle: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                val sz = size.toFloat()
                var a = angle.toFloat() * (360.0f / 32.0f)
                a -= 90.0f // convention for where 0-degrees is
                a -= 120.0f // start angle not centre of sweep to be provided
                val r = RectF(xc - sz, yc - sz, xc + sz, yc + sz)
                c.drawArc(r, a, 240.0f, false, p_colours[colour])
                c.drawArc(r, a, 240.0f, false, p_thin_black)
            }
        }

        internal class Circle     //Log.i(TAG, "circle x=" + sx + " y=" + sy + " size=" + size + " colour=" + colour);
        (_sx: Int, _sy: Int, var size: Int, var colour: Int) : Feature(_sx, _sy) {
            var angle = 0
            override fun render(c: Canvas) {
                val xc: Float
                val yc: Float
                val radius: Float
                xc = sx * 16.0f + 8.0f
                yc = sy * 16.0f + 8.0f
                radius = size.toFloat()
                c.drawCircle(xc, yc, radius, p_colours[colour])
                c.drawCircle(xc, yc, radius, p_thin_black)
            }
        }

        internal class Donut(_sx: Int, _sy: Int, var size: Int, var colour: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val xc: Float
                val yc: Float
                val radius: Float
                xc = sx * 16.0f + 8.0f
                yc = sy * 16.0f + 8.0f
                radius = size.toFloat()
                c.drawCircle(xc, yc, radius, p_thick_red)
            }
        }

        internal class Tower(_sx: Int, _sy: Int, _eno: Int, var colour: Int, _caption: String) : Feature(_sx, _sy) {
            var eno: Boolean
            var caption: String
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                val r = RectF(xc - 8.0f, yc - 8.0f, xc + 8.0f, yc + 8.0f)
                c.drawRect(r, p_colours[colour])
                if (eno) {
                    c.drawRect(r, p_thick_black_dash)
                } else {
                    c.drawRect(r, p_thick_black)
                }
                val width: Float = p_caption_left.measureText(caption) + margin2
                if (sx >= 8) {
                    c.drawRect(xc + 7 - width, yc - margin2, xc + 7, yc + 7, p_wash)
                    c.drawText(caption, xc + 7 - margin, yc + 7 - margin, p_caption_right)
                } else {
                    c.drawRect(xc - 8, yc - margin2, xc - 8 + width, yc + 7, p_wash)
                    c.drawText(caption, xc - 8 + margin, yc + 7 - margin, p_caption_left)
                }
            }

            companion object {
                const val margin = 1
                const val margin2 = 2
            }

            init {
                eno = _eno == 1
                caption = _caption
            }
        }

        internal class Adjacent(_sx: Int, _sy: Int, _eno: Int, var colour: Int) : Feature(_sx, _sy) {
            var eno: Boolean
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                val r = RectF(xc - 5.0f, yc - 5.0f, xc + 5.0f, yc + 5.0f)
                c.drawRect(r, p_colours[colour])
                if (eno) {
                    c.drawRect(r, p_thin_black_dash)
                } else {
                    c.drawRect(r, p_thin_black)
                }
            }

            init {
                eno = _eno == 1
            }
        }

        internal class Multiple(_sx: Int, _sy: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val x0 = (sx shl 4) as Float
                val y0 = (sy shl 4) as Float
                val r1 = RectF(x0 + 1, y0 + 1, x0 + 9, y0 + 9)
                val r2 = RectF(x0 + 7, y0 + 3, x0 + 15, y0 + 11)
                val r3 = RectF(x0 + 3, y0 + 6, x0 + 11, y0 + 14)
                c.drawRect(r1, p_trans_red)
                c.drawRect(r2, p_trans_red)
                c.drawRect(r3, p_trans_red)
                c.drawRect(r1, p_thin_black)
                c.drawRect(r2, p_thin_black)
                c.drawRect(r3, p_thin_black)
            }
        }

        internal class Null(_sx: Int, _sy: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                c.drawLine(xc - 8, yc - 4, xc, yc - 8, p_thin_red)
                c.drawLine(xc - 8, yc + 4, xc + 8, yc - 4, p_thin_red)
                c.drawLine(xc, yc + 8, xc + 8, yc + 4, p_thin_red)
                c.drawLine(xc - 8, yc, xc - 4, yc + 8, p_thin_red)
                c.drawLine(xc - 4, yc - 8, xc + 4, yc + 8, p_thin_red)
                c.drawLine(xc + 4, yc - 8, xc + 8, yc, p_thin_red)
            }
        }

        internal class Missing(_sx: Int, _sy: Int, var count: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val x0 = (sx shl 4) as Float
                val y0 = (sy shl 4) as Float
                val r1 = RectF(x0 + 1, y0 + 1, x0 + (count shl 4) - 2, y0 + (count shl 4) - 2)
                c.drawRect(r1, p_thin_blue_noaa)
            }
        }

        internal class HighMissing(_sx: Int, _sy: Int, var count: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val x0 = (sx shl 4) as Float
                val y0 = (sy shl 4) as Float
                val r1 = RectF(x0 + 1, y0 + 1, x0 + (count shl 4) - 2, y0 + (count shl 4) - 2)
                c.drawRect(r1, p_thin_red_noaa)
            }
        }

        internal class PlainBox(_sx: Int, _sy: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val x0 = (sx shl 4) as Float
                val y0 = (sy shl 4) as Float
                val r1 = RectF(x0, y0, x0 + 16, y0 + 16)
                c.drawRect(r1, p_transparent_grey)
            }
        }

        internal class VaneLong(_sx: Int, _sy: Int, var angle: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                c.drawLine(xc, yc, xc + anglx[angle], yc + angly[angle], p_thin_black)
            }
        }

        internal class VaneShort(_sx: Int, _sy: Int, var angle: Int) : Feature(_sx, _sy) {
            override fun render(c: Canvas) {
                val xc = (8 + (sx shl 4)) as Float
                val yc = (8 + (sy shl 4)) as Float
                c.drawLine(xc, yc, xc + angsx[angle], yc + angsy[angle], p_thin_black)
            }
        }

        // ---------------------------
        var content1: ArrayList<Feature?>
        var content2: ArrayList<Feature?>

        // return position in the file where the required overlay data starts, or -1 if there is none
        private fun lookup(`in`: RandomAccessFile, overlay_param: Int, zoom: Int, tile_x: Int, tile_y: Int): Long {
            var zoom = zoom
            val param_offset: Int
            param_offset = when (overlay_param) {
                2 -> 0
                3 -> 4
                else -> return -1
            }
            return try {
                var offset: Int
                var len: Long
                var loc: Long
                val magic: Int
                var wide: Boolean
                val pp0: Int
                val pp1: Int
                val idx1: Int
                magic = read32(`in`, 0)
                if (magic != MAGIC_NUMBER) {
                    return -1
                }
                loc = `in`.length() - 4
                offset = read32(`in`, loc)
                //in.seek(loc);
                //offset = in.readInt();
                loc -= offset.toLong() // looking at level 0 table
                while (zoom > 0) {
                    zoom -= 1
                    val xz = tile_x shr zoom and 1
                    val yz = tile_y shr zoom and 1
                    val p = read8(`in`, loc)
                    val idx = (p and 15) + (xz + xz + yz shl 4)
                    val t = table1[idx]
                    wide = p and 0x80 != 0
                    if (t < 0) {
                        return -1
                    }
                    offset = if (wide) {
                        read32(`in`, loc + 1 + 4 * t)
                    } else {
                        read16(`in`, loc + 1 + 2 * t)
                    }
                    loc -= offset.toLong()
                }
                val p = read8(`in`, loc)
                wide = p and 0x80 != 0
                pp0 = p and 0xf
                pp1 = p shr 4 and 0x3
                idx1 = table3[param_offset + pp1]
                if (idx1 >= 0) {
                    val idx = idx1 + table2[pp0]
                    offset = if (wide) {
                        read32(`in`, loc + 1 + 4 * idx)
                    } else {
                        read16(`in`, loc + 1 + 2 * idx)
                    }
                    loc -= offset.toLong()
                    loc
                } else {
                    -1
                }
            } catch (e: IOException) {
                -1
            }
        }

        private fun SX(coord: Int): Int {
            return coord shr 4 and 15
        }

        private fun SY(coord: Int): Int {
            return coord and 15
        }

        private fun decode(`in`: RandomAccessFile, pos: Long, simplify: Boolean) {
            var pos = pos
            var b: Int
            var coord = -1
            try {
                while (true) {
                    b = read8(`in`, pos)
                    val opc = b shr 4 and 15
                    val displacement = b and 15
                    var opc2: Int
                    var b1: Int
                    var eno: Int
                    var len: Int
                    var colour: Int
                    var size: Int
                    var angle: Int
                    when (opc) {
                        0, 1 -> {
                            // long vane
                            angle = b and 0x1f
                            if (!simplify) {
                                content1.add(VaneLong(SX(coord), SY(coord), angle))
                            }
                            pos += 1
                        }
                        2, 3 -> {
                            // short vane
                            angle = b and 0x1f
                            if (!simplify) {
                                content1.add(VaneShort(SX(coord), SY(coord), angle))
                            }
                            pos += 1
                        }
                        4, 5 -> {
                            // sector
                            coord += 1
                            angle = b and 0x1f
                            b1 = read8(`in`, pos + 1)
                            size = b1 shr 4 and 15
                            colour = b1 and 15
                            if (simplify) {
                                content1.add(PlainBox(SX(coord), SY(coord)))
                            } else {
                                content1.add(Sector(SX(coord), SY(coord), size, colour, angle))
                            }
                            pos += 2
                        }
                        6 -> {
                            coord += 1 + displacement // embedded skip
                            b1 = read8(`in`, pos + 1)
                            size = b1 shr 4 and 15
                            colour = b1 and 15
                            if (simplify) {
                                content1.add(PlainBox(SX(coord), SY(coord)))
                            } else {
                                content1.add(Circle(SX(coord), SY(coord), size, colour))
                            }
                            pos += 2
                        }
                        7 -> {
                            coord += 1 + displacement // embedded skip
                            b1 = read8(`in`, pos + 1)
                            size = b1 shr 4 and 15
                            colour = b1 and 15
                            if (simplify) {
                                content1.add(PlainBox(SX(coord), SY(coord)))
                            } else {
                                content1.add(Donut(SX(coord), SY(coord), size, colour))
                            }
                            pos += 2
                        }
                        8 -> {
                            coord += 1 + displacement // embedded skip
                            b1 = read8(`in`, pos + 1)
                            eno = b1 shr 7 and 1
                            colour = b1 and 15
                            content2.add(Adjacent(SX(coord), SY(coord), eno, colour))
                            pos += 2
                        }
                        9 -> {
                            coord += 1 + displacement // embedded skip
                            b1 = read8(`in`, pos + 1)
                            eno = b1 shr 7 and 1
                            len = b1 shr 4 and 7
                            colour = b1 and 15
                            val caption = read8_multi(`in`, pos + 2, len)
                            content2.add(Tower(SX(coord), SY(coord), eno, colour, String(caption)))
                            pos += 2 + len.toLong()
                        }
                        10 -> {
                            coord += 1 + displacement // embedded skip
                            content2.add(Multiple(SX(coord), SY(coord)))
                            pos += 1
                        }
                        11 -> {
                            coord += 1 + displacement // embedded skip
                            if (simplify) {
                                content1.add(PlainBox(SX(coord), SY(coord)))
                            } else {
                                content1.add(Null(SX(coord), SY(coord)))
                            }
                            pos += 1
                        }
                        12 -> {
                            coord += 1 + displacement // embedded skip
                            pos += 1
                        }
                        13, 14 -> {
                        }
                        15 -> {
                            opc2 = b and 15
                            when (opc2) {
                                0 -> {
                                    b1 = read8(`in`, pos + 1)
                                    coord += b1
                                    pos += 2
                                }
                                15 -> return
                                else -> {
                                }
                            }
                        }
                    }
                    if (coord >= 256) {
                        // crude safety net against mis-parsing the file and getting
                        // trapped in a long-running loop
                        break
                    }
                }
            } catch (e: IOException) {
                return
            }
        }

        private fun gather_missing(simplify: Boolean) {
            for (lvl in 0..3) {
                val step0 = 1 shl lvl
                val step1 = 2 shl lvl
                val count = 1 shl lvl
                var i = 0
                while (i < 16) {
                    var j = 0
                    while (j < 16) {
                        if (missing[i][j] == count &&
                                missing[i + step0][j] == count &&
                                missing[i][j + step0] == count &&
                                missing[i + step0][j + step0] == count) {
                            missing[i][j] = count + count
                            missing[i + step0][j] = 0
                            missing[i][j + step0] = 0
                            missing[i + step0][j + step0] = 0
                        }
                        j += step1
                    }
                    i += step1
                }
            }
            for (i in 0..15) {
                for (j in 0..15) {
                    val count = missing[i][j]
                    if (count > 0) {
                        if (simplify) {
                            content1.add(HighMissing(i, j, count))
                        } else {
                            content1.add(Missing(i, j, count))
                        }
                    }
                }
            }
        }

        fun apply2(c: Canvas?, content: ArrayList<Feature?>) {
            val n: Int = content.size()
            for (i in 0 until n) {
                val f: Feature = content.get(i)
                f.render(c)
            }
        }

        fun apply(bm: Bitmap?) {
            val c = Canvas(bm)
            apply2(c, content1)
            apply2(c, content2)
            return
        }

        companion object {
            // ---------------------------
            // scratch storage during building.
            // allocate just once.
            // Relies on only one Recipe being under construction at a time
            var missing = Array(16) { IntArray(16) }

            // ---------------------------
            @Throws(IOException::class)
            private fun read8(`in`: RandomAccessFile, loc: Long): Int {
                `in`.seek(loc)
                return `in`.readUnsignedByte()
            }

            @Throws(IOException::class)
            private fun read8_multi(`in`: RandomAccessFile, loc: Long, len: Int): ByteArray {
                val result = ByteArray(len)
                `in`.seek(loc)
                `in`.read(result)
                return result
            }

            @Throws(IOException::class)
            private fun read16(`in`: RandomAccessFile, loc: Long): Int {
                `in`.seek(loc)
                return `in`.readUnsignedShort()
            }

            @Throws(IOException::class)
            private fun read32(`in`: RandomAccessFile, loc: Long): Int {
                `in`.seek(loc)
                return `in`.readInt()
            }

            val table1 = intArrayOf(
                    -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0,
                    -1, -1, 0, 1, -1, -1, 0, 1, -1, -1, 0, 1, -1, -1, 0, 1,
                    -1, -1, -1, -1, 0, 1, 1, 2, -1, -1, -1, -1, 0, 1, 1, 2,
                    -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 1, 2, 1, 2, 2, 3
            )
            val table2 = intArrayOf(
                    0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4
            )
            val table3 = intArrayOf(
                    -1, 0, -1, 0,
                    -1, -1, 0, 1
            )

            fun clear_missing() {
                for (i in 0..15) {
                    for (j in 0..15) {
                        missing[i][j] = 1
                    }
                }
            }
        }

        init {

            // simplify is the option to reduce covered areas to grey squares, to
            // highlight the 'to do' areas better
            var `in`: RandomAccessFile?
            content1 = ArrayList<Feature>()
            content2 = ArrayList<Feature>()
            clear_missing()
            try {
                `in` = RandomAccessFile(PREFDIR + "/" + overlay_file, "r")
            } catch (e: IOException) {
                `in` = null
            }
            if (`in` != null) {
                val pos = lookup(`in`, overlay_param, zoom, tile_x, tile_y)
                // Log.i(TAG, "z=" + zoom + " x=" + tile_x + " y=" + tile_y + " pos=" + pos);
                if (pos >= 0) {
                    decode(`in`, pos, simplify)
                }
                try {
                    `in`.close()
                } catch (e: IOException) {
                }
            }
            gather_missing(simplify)
        }
    }
}