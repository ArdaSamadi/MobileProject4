
package uk.org.rc0.logmygsm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import java.io.File
import java.lang.Runnable
import java.util.LinkedList
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object TileStore {
    private const val bm_log_size = 8
    private const val bm_size = 1 shl bm_log_size
    private const val TAG = "TileStore"
    private const val do_log = false
    private var last_w = 0
    private var last_h = 0
    private var front: Array<Entry?>
    private var next = 0
    private var back: Array<Entry?>?
    var draw_cycle = 0
    private var gray_paint: Paint? = null
    private var light_gray_paint: Paint? = null
    var trail_paint: Paint? = null
    var trail_dot_paint_0: Paint? = null
    var trail_dot_paint_1: Paint? = null
    const val TRAIL_DOT_SIZE = 2.0f
    private var mHandler: Handler? = null
    private var bg_queue: LinkedList<TilePos>? = null
    private var loading_bitmap: Bitmap? = null
    private var mContext: Context? = null
    private var start_time: Long = 0
    var highlight_border_paint: Paint? = null
    private const val HIGHLIGHT_WIDTH = 16
    private val scaling_paint: Paint? = null

    // -----------
    fun new_cache(): Array<Entry?> {
        val ww = (last_w shr 8) + 2
        val hh = (last_h shr 8) + 2
        var tt = ww * hh
        // oversize to give panning space around edges or to allow zoom in, zoom
        // out etc
        tt += tt
        if (do_log) {
            Log.i(TAG, "New cache w=$ww h=$hh tt=$tt")
        }
        return arrayOfNulls(tt)
    }

    fun init(the_app_context: Context?) {
        refresh_epoch()
        mContext = the_app_context
        last_w = 240
        last_h = 400
        front = new_cache()
        next = 0
        back = null
        draw_cycle = 0
        gray_paint = Paint()
        gray_paint.setColor(Color.GRAY)
        light_gray_paint = Paint()
        light_gray_paint.setColor(Color.argb(255, 0xa0, 0xa0, 0xa0))
        trail_paint = Paint()
        trail_paint.setColor(Color.argb(56, 0x6d, 0, 0xb0))
        trail_paint.setStyle(Paint.Style.FILL)
        trail_dot_paint_0 = Paint()
        trail_dot_paint_0.setColor(Color.argb(255, 0, 0, 0))
        trail_dot_paint_0.setStyle(Paint.Style.FILL)
        trail_dot_paint_1 = Paint()
        trail_dot_paint_1.setColor(Color.argb(255, 255, 255, 255))
        trail_dot_paint_1.setStyle(Paint.Style.FILL)
        highlight_border_paint = Paint()
        highlight_border_paint.setColor(Color.argb(32, 0x00, 0x00, 0xff))
        highlight_border_paint.setStyle(Paint.Style.STROKE)
        highlight_border_paint.setStrokeWidth(HIGHLIGHT_WIDTH)
        highlight_border_paint.setStrokeCap(Paint.Cap.SQUARE)
        mHandler = Handler()
        bg_queue = LinkedList<TilePos>()
        loading_bitmap = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888)
        val my_canv = Canvas(loading_bitmap)
        my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint)
        my_canv.drawRect(bm_size shr 3, bm_size shr 3,
                bm_size - (bm_size shr 3), bm_size - (bm_size shr 3),
                light_gray_paint)
        val scaling_paint = Paint(Paint.FILTER_BITMAP_FLAG)
    }

    // -----------
    // Internal
    //
    fun render_dot(c: Canvas, px: Float, py: Float, parity: Int) {
        c.drawCircle(px, py, Trail.splot_radius, trail_paint)
        if (parity == 1) {
            c.drawCircle(px, py, TRAIL_DOT_SIZE, trail_dot_paint_1)
        } else {
            c.drawCircle(px, py, TRAIL_DOT_SIZE, trail_dot_paint_0)
        }
    }

    private fun render_old_trail(bm: Bitmap?, zoom: Int, tile_x: Int, tile_y: Int) {
        val pixel_shift: Int = Merc28.shift - (zoom + bm_log_size)
        val tile_shift: Int = Merc28.shift - zoom
        val xnw = tile_x shl tile_shift
        val ynw = tile_y shl tile_shift
        var parity = 0
        val my_canv = Canvas(bm)
        val pa: Trail.PointArray = Logger.mTrail!!.get_historical()
        var last_x = 0
        var last_y = 0
        for (i in 0 until pa.n) {
            val px: Int = pa.x!!.get(i) - xnw shr pixel_shift
            val py: Int = pa.y!!.get(i) - ynw shr pixel_shift
            var do_add = true
            if (i > 0) {
                val manhattan: Int = Math.abs(px - last_x) + Math.abs(py - last_y)
                if (manhattan < Trail.splot_gap) {
                    do_add = false
                }
            }
            if (do_add) {
                render_dot(my_canv, px.toFloat(), py.toFloat(), parity)
                parity = parity xor 1
                last_x = px
                last_y = py
            }
        }
    }

    private fun render_highlight_border(bm: Bitmap?) {
        val my_canv = Canvas(bm)
        val hw: Int
        val hw2: Int
        hw = HIGHLIGHT_WIDTH - (HIGHLIGHT_WIDTH shr 4)
        hw2 = 256 - hw
        my_canv.drawLine(hw, hw, hw2, hw, highlight_border_paint)
        my_canv.drawLine(hw, hw2, hw2, hw2, highlight_border_paint)
        my_canv.drawLine(hw, hw, hw, hw2, highlight_border_paint)
        my_canv.drawLine(hw2, hw, hw2, hw2, highlight_border_paint)
    }

    private fun render_coarse_zoom_cross(bm: Bitmap?) {
        val my_canv = Canvas(bm)
        val hw: Int
        val hw2: Int
        hw = HIGHLIGHT_WIDTH - (HIGHLIGHT_WIDTH shr 4)
        hw2 = 256 - hw
        my_canv.drawLine(hw, hw, hw2, hw2, highlight_border_paint)
        my_canv.drawLine(hw, hw2, hw2, hw, highlight_border_paint)
    }

    private fun render_bitmap(zoom: Int, map_source: MapSource?, x: Int, y: Int): TilingResponse {
        var filename: String? = null
        filename = map_source!!.get_tile_path(zoom, x, y)
        var file = File(filename)
        var bm: Bitmap? = null
        var is_dummy = false
        try {
            if (file.exists()) {
                val temp_bm: Bitmap = BitmapFactory.decodeFile(filename)
                bm = temp_bm.copy(Bitmap.Config.ARGB_8888, true)
                if (file.lastModified() > start_time) {
                    render_highlight_border(bm)
                }
            } else {
                // Try to use a sub-region of a zoomed out bitmap instead
                // NOTE : this could be modified so that the above 'then' clause is the initial iteration,
                // but that would introduce extra bitmap copy operations to the normal case
                var x0: Int
                var y0: Int // top corner of source bitmap
                var swh: Int // sub width/height
                var tx: Int
                var ty: Int // tile coords
                tx = x
                ty = y
                swh = bm_size
                y0 = 0
                x0 = y0
                for (parent in 1..3) {
                    val local_zoom = zoom - parent
                    swh = swh shr 1
                    x0 = (tx and 1 shl 7) + (x0 shr 1)
                    y0 = (ty and 1 shl 7) + (y0 shr 1)
                    tx = tx shr 1
                    ty = ty shr 1
                    filename = map_source!!.get_tile_path(local_zoom, tx, ty)
                    file = File(filename)
                    if (file.exists()) {
                        val temp_bm: Bitmap = BitmapFactory.decodeFile(filename)
                        val ancestor_bm: Bitmap = temp_bm.copy(Bitmap.Config.ARGB_8888, true)
                        val sub_bm: Bitmap = Bitmap.createBitmap(ancestor_bm, x0, y0, swh, swh)
                        bm = Bitmap.createScaledBitmap(sub_bm, bm_size, bm_size, false)
                        render_coarse_zoom_cross(bm)
                        if (file.lastModified() > start_time) {
                            render_highlight_border(bm)
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // to deal with corrupt tile files and such horrors
        }
        if (bm == null) {
            bm = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888)
            val my_canv = Canvas(bm)
            my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint)
            is_dummy = true
        }
        map_source!!.apply_overlay(bm, zoom, x, y)
        render_old_trail(bm, zoom, x, y)
        return TilingResponse(bm, is_dummy)
    }

    private fun start_bg_load(zoom: Int, x: Int, y: Int, map_source: MapSource?) {
        // Check if this job is already on the queue
        var i: Int
        i = 0
        while (i < bg_queue.size()) {
            if (bg_queue.get(i).isMatch(zoom, x, y, map_source)) {
                return
            }
            i++
        }
        // Not already in the queue of tiles to render.  Let's go....
        bg_queue.add(TilePos(zoom, x, y, map_source))
        if (bg_queue.size() === 1) {
            // We've just queued the 1st piece of work: kick off the bg stuff
            if (do_log) {
                Log.i(TAG, "Starting first bg load op")
            }
            TilingThread(zoom, x, y, map_source).start()
        }
    }

    fun loading_is_dormant(): Boolean {
        return if (bg_queue.size() > 0) {
            false
        } else {
            true
        }
    }

    private fun make_entry(zoom: Int, map_source: MapSource?, x: Int, y: Int, b: Bitmap?, is_dummy: Boolean): Entry {
        return Entry(zoom, map_source, x, y, b, is_dummy)
    }

    private fun check_full() {
        if (next == front.size) {
            back = front
            front = new_cache()
            next = 0
            if (do_log) {
                Log.i(TAG, "Flushed tile store")
            }
        }
    }

    private fun lookup(zoom: Int, map_source: MapSource?, x: Int, y: Int): Entry? {
        // front should never be null
        for (i in next - 1 downTo 0) {
            if (front[i].isMatch(zoom, x, y, map_source)) {
                return front[i]
            }
        }
        // Miss. Match in back?
        var back_match: Entry? = null
        if (back != null) {
            for (i in back.indices.reversed()) {
                if (back!![i].isMatch(zoom, x, y, map_source)) {
                    if (do_log) {
                        Log.i(TAG, "Back match found at $i")
                    }
                    back_match = back!![i]
                    break
                }
            }
        }
        return if (back_match != null) {
            check_full()
            front[next++] = back_match
            back_match
        } else {
            // Full miss.
            start_bg_load(zoom, x, y, map_source)
            null
        }
    }

    private fun ripple() {
        // gravitate entries in 'front' towards the end that's checked first
        for (i in 1 until next) {
            if (front[i - 1]!!.cycle > front[i]!!.cycle) {
                // swap two entries over
                val t = front[i]
                front[i] = front[i - 1]
                front[i - 1] = t
            }
        }
    }

    // -----------
    // Interface with map
    fun invalidate() {
        // Get rid of cache when changes occur in tiles - to force reload
        front = new_cache()
        next = 0
        back = null
        // Todo : drop all bar first entry in bg_queue ?
    }

    fun sleep_invalidate() {
        // Get rid of cache to free up memory when activities exit
        front = new_cache()
        next = 0
        back = null
        System.gc()
    }

    fun draw(c: Canvas, w: Int, h: Int, zoom: Int, map_source: MapSource?, midpoint: Merc28?, is_scaled: Boolean) {
        val pixel_shift: Int = Merc28.shift - (zoom + bm_log_size)

        // Compute pixels from origin at this zoom level for top-left corner of canvas
        val px: Int
        val py: Int
        px = (midpoint!!.X shr pixel_shift) - (w shr 1)
        py = (midpoint!!.Y shr pixel_shift) - (h shr 1)

        // Hence compute tile containing top-left corner of canvas
        val tx: Int
        val ty: Int
        tx = px shr bm_log_size
        ty = py shr bm_log_size

        // top-left corner of the top-left tile, in pixels relative to top-left corner of canvas
        val ox: Int
        val oy: Int
        ox = (tx shl bm_log_size) - px
        oy = (ty shl bm_log_size) - py

        // These are used to size the new 'front' cache when it gets re-allocated
        last_w = w
        last_h = h

        // This is used in maintaining the cache so that the most recently used
        // entries are the ones hit first in the search.
        draw_cycle++
        var i: Int
        var j: Int
        val mask = (1 shl zoom) - 1
        i = 0
        while (ox + (i shl bm_log_size) < w) {
            val xx = ox + (i shl bm_log_size)
            j = 0
            while (oy + (j shl bm_log_size) < h) {
                val yy = oy + (j shl bm_log_size)
                val e = lookup(zoom, map_source, tx + i and mask, ty + j)
                var bm: Bitmap
                bm = if (e != null) {
                    e.add_recent_trail()
                    e.touch()
                    e.bitmap
                } else {
                    loading_bitmap
                }
                val dest = Rect(xx, yy, xx + bm_size, yy + bm_size)
                c.drawBitmap(bm, null, dest,
                        if (is_scaled) scaling_paint else null)
                j++
            }
            i++
        }
        ripple()
    }

    fun trigger_fetch(context: Context?) {
        var targets: LinkedList<TilePos?>?
        targets = LinkedList<TilePos>()
        for (i in 0 until next) {
            if (front[i]!!.is_dummy) {
                targets.add(TilePos(front[i]))
            }
        }
        if (back != null) {
            for (i in back.indices) {
                if (back!![i]!!.is_dummy) {
                    targets.add(TilePos(back!![i]))
                }
            }
        }
        Downloader.start_multiple_fetch(targets, true, context)
        targets = null
    }

    fun get_epoch(): Long {
        return start_time
    }

    fun refresh_epoch() {
        start_time = System.currentTimeMillis()
    }

    // -----------
    // State
    internal class TilePos {
        var zoom: Int
        var x: Int
        var y: Int
        var map_source: MapSource?

        constructor(_zoom: Int, _x: Int, _y: Int, _map_source: MapSource?) {
            zoom = _zoom
            x = _x
            y = _y
            map_source = _map_source
        }

        constructor(old: TilePos?) {
            zoom = old!!.zoom
            x = old.x
            y = old.y
            map_source = old.map_source
        }

        fun isMatch(_zoom: Int, _x: Int, _y: Int, _map_source: MapSource): Boolean {
            return if (_zoom == zoom &&
                    _map_source.get_code() === map_source!!.get_code() &&
                    _x == x &&
                    _y == y) {
                true
            } else {
                false
            }
        }
    }

    private class Entry internal constructor(_zoom: Int, _map_source: MapSource?, _x: Int, _y: Int, _b: Bitmap?, _is_dummy: Boolean) : TilePos(_zoom, _x, _y, _map_source) {
        var pixel_shift: Int
        var tile_shift: Int
        var cycle = 0
        private val upto: Trail.Upto
        var is_dummy: Boolean
        var b: Bitmap?
        val bitmap: Bitmap?
            get() = b

        fun touch() {
            cycle = draw_cycle
        }

        fun add_recent_trail() {
            val my_canv = Canvas(b)
            Logger.mTrail!!.draw_recent_trail(my_canv,
                    x shl tile_shift, y shl tile_shift,
                    pixel_shift, upto)
        }

        init {
            pixel_shift = Merc28.shift - (zoom + bm_log_size)
            tile_shift = Merc28.shift - zoom
            b = _b
            is_dummy = _is_dummy
            upto = Upto()
            touch()
        }
    }

    // -----------
    private class TilingResponse(_bm: Bitmap?, _is_dummy: Boolean) : Runnable {
        private val bm: Bitmap?
        private val is_dummy: Boolean
        @Override
        fun run() {
            check_full()
            var tp: TilePos = bg_queue.remove() // head of list
            val e = make_entry(tp.zoom, tp.map_source, tp.x, tp.y, bm, is_dummy)
            if (do_log) {
                Log.i(TAG, "Putting load response at position " + next)
            }
            front[next++] = e
            if (bg_queue.size() > 0) {
                tp = bg_queue.getFirst()
                if (do_log) {
                    Log.i(TAG, "Start next job, queue size is " + bg_queue.size())
                }
                TilingThread(tp.zoom, tp.x, tp.y, tp.map_source).start()
            } else {
                // Last job done.  Force map redraw
                val intent = Intent(Logger.UPDATE_GPS)
                mContext.sendBroadcast(intent)
            }
        }

        init {
            bm = _bm
            is_dummy = _is_dummy
        }
    }

    private class TilingThread(private val zoom: Int, private val x: Int, private val y: Int, _map_source: MapSource?) : Thread() {
        private val map_source: MapSource?
        @Override
        fun run() {
            var resp: TilingResponse? = render_bitmap(zoom, map_source, x, y)
            mHandler.post(resp)
            resp = null
        }

        init {
            map_source = _map_source
            //setPriority(Thread.MIN_PRIORITY);
        }
    }
}