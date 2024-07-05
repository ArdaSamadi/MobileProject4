
package uk.org.rc0.logmygsm

import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.Integer
import java.lang.Math
import java.lang.NumberFormatException
import java.util.LinkedList
import java.util.Set
import java.util.HashSet
import java.util.Iterator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class Map(context: Context, attrs: AttributeSet?) : View(context, attrs), MapActionBar.Callbacks {
    private val red_paint: Paint
    private val red_stroke_paint: Paint
    private val red_double_stroke_paint: Paint
    private val button_stroke_paint: Paint
    private val mRB: RoutingsBar
    private val mAB: MapActionBar
    private var zoom = 0
    protected var pixel_shift = 0
    private var tile_shift = 0
    private var drag_scale = 0f
    private var last_w: Int
    private var last_h: Int
    private var map_source: MapSource?

    // the GPS fix from the logger
    private var estimated_pos: Merc28?

    // the location at the centre of the screen - may be != estimated_pos if is_dragged is true.
    protected var display_pos: Merc28?

    // Set to true if we've off-centred the map
    private var is_dragged = false

    // Set to true if map tiles are scaled to make them more legible
    protected var mScaled: Boolean

    // --------------------------------------------------------------------------
    interface PositionListener {
        fun display_position_update()
    }

    private var mPL: PositionListener? = null
    fun register_position_listener(pl: PositionListener?) {
        mPL = pl
    }

    // Main drawing routines...
    private var len1 = 0f
    private var len3 = 0f
    private var len4 = 0f
    private var button_size = 0
    private var button_size_2 = 0
    private var button_size_h = 0
    private fun set_lengths(width: Int, height: Int) {
        val t: Int
        t = width + height shr 1
        button_size = (height shr 3) + (height shr 4) /* 3/16* h */
        button_size_2 = button_size shr 1
        button_size_h = button_size_2 - (button_size shr 3)
        len1 = (t shr 5).toFloat() // approx 8 * (t/240)
        len3 = (t + t + t shr 5).toFloat() // approx 3*len1
        len4 = 0.5f * len3 // approx 1.5*len1
    }

    private fun draw_centre_circle(c: Canvas, w: Int, h: Int) {
        val x0: Float
        val x1: Float
        val x2: Float
        val x3: Float
        val xc: Float
        val y0: Float
        val y1: Float
        val y2: Float
        val y3: Float
        val yc: Float
        xc = (w / 2).toFloat()
        yc = (h / 2).toFloat()
        x0 = xc - len3
        x1 = xc - len1
        x2 = xc + len1
        x3 = xc + len3
        y0 = yc - len3
        y1 = yc - len1
        y2 = yc + len1
        y3 = yc + len3
        c.drawCircle(xc, yc, len3, red_stroke_paint)
        c.drawLine(x0, yc, x1, yc, red_paint)
        c.drawLine(x2, yc, x3, yc, red_paint)
        c.drawLine(xc, y0, xc, y1, red_paint)
        c.drawLine(xc, y2, xc, y3, red_paint)
    }

    fun draw_position(c: Canvas, w: Int, h: Int) {
        if (estimated_pos != null) {
            var x0: Float
            var x1: Float
            var x2: Float
            var x3: Float
            var xc: Float
            var y0: Float
            var y1: Float
            var y2: Float
            var y3: Float
            var yc: Float
            xc = (w shr 1).toFloat()
            yc = (h shr 1).toFloat()
            if (is_dragged) {
                val dx: Int = estimated_pos.X - display_pos!!.X shr pixel_shift
                val dy: Int = estimated_pos.Y - display_pos!!.Y shr pixel_shift
                xc += dx.toFloat()
                yc += dy.toFloat()
            }
            if (Logger.validFix) {
                c.save()
                c.rotate(Logger.lastBearing as Float, xc, yc)
                val xl = xc - 1.0f * len1
                val xr = xc + 1.0f * len1
                val yb = yc - (len4 + 0.5f * len1)
                val yt = yc - (len4 + 3.5f * len1)
                c.drawLine(xc, yt, xl, yb, red_double_stroke_paint)
                c.drawLine(xc, yt, xr, yb, red_double_stroke_paint)
                c.drawLine(xl, yb, xr, yb, red_double_stroke_paint)

                // and draw the position circle
                c.drawCircle(xc, yc, len4, red_double_stroke_paint)
                c.restore()
            }
        }
    }

    private fun redraw_map(canvas: Canvas) {
        val width: Int = getWidth()
        val height: Int = getHeight()
        if (last_w != width || last_h != height) {
            set_lengths(width, height)
        }
        last_w = width
        last_h = height
        val save_level: Int
        if (mScaled) {
            save_level = canvas.save()
            canvas.scale(TILE_SCALING, TILE_SCALING, (width shr 1).toFloat(), (height shr 1).toFloat())
        }
        val t = Transform(display_pos, width, height, pixel_shift)
        TileStore.draw(canvas, width, height, zoom, map_source, display_pos, mScaled)
        Logger.mWaypoints!!.draw(canvas, t, true)
        Logger.mLandmarks!!.draw(canvas, t)
        draw_position(canvas, width, height)
        if (TowerLine.is_active()) {
            TowerLine.draw_line(canvas, width, height, pixel_shift, display_pos)
        }
        if (mScaled) {
            canvas.restore()
        }
        draw_centre_circle(canvas, width, height)
        mAB.draw(canvas, width, button_size)
        mRB.show_routings(canvas, width, height, display_pos, button_size)
    }

    // Interface with main UI activity
    fun update_map() {
        // This try-catch shouldn't be necessary.
        // What is the real bug?
        estimated_pos = try {
            Logger.mTrail!!.get_estimated_position()
        } catch (n: NullPointerException) {
            null
        }
        if (estimated_pos != null &&
                (display_pos == null || !is_dragged)) {
            display_pos = Merc28(estimated_pos)
        }
        invalidate()
    }

    private fun maybe_invalidate() {
        // called after a UI action that ought to cause a map redraw.
        // BUT : if we're still loading tiles from disc after the last redraw,
        // don't bother.  Rationale : if the user is quickly stepping through zoom
        // levels, he will quickly get ahead of the loader and there will a huge lag in doing the final redraw,
        // and many unwanted tiles will get loaded.
        if (TileStore.loading_is_dormant()) {
            invalidate()
        }
    }

    fun select_map_source(which: MapSource?) {
        map_source = which
        maybe_invalidate()
    }

    private fun setZoom(z: Int) {
        //tile_cache.setZoom(z);
        zoom = z
        pixel_shift = Merc28.shift - (z + 8)
        tile_shift = Merc28.shift - z
        drag_scale = (1 shl pixel_shift).toFloat()
    }

    // Yes, we should use the Android preferences system for this.
    // Saving to SD card allows us to edit the file offline etc
    // for debugging and so on
    fun restore_state_from_file(tail: String) {
        val file = File("/sdcard/LogMyGsm/prefs/$tail")
        // defaults in case of strife
        display_pos = Merc28(54.5, -2.0) // in the wilderness
        setZoom(14)
        map_source = MapSources.get_default()
        mScaled = false
        if (file.exists()) {
            try {
                val br = BufferedReader(FileReader(file))
                var line: String
                line = br.readLine()
                setZoom(Integer.parseInt(line))
                val x: Int
                val y: Int
                line = br.readLine()
                x = Integer.parseInt(line)
                line = br.readLine()
                y = Integer.parseInt(line)
                // If we survive unexcepted to here we parsed the file OK
                display_pos = Merc28(x, y)
                line = br.readLine()
                map_source = MapSources.lookup(Integer.parseInt(line))
                if (map_source == null) {
                    map_source = MapSources.get_default()
                }
                line = br.readLine()
                val read_scaled: Int = Integer.parseInt(line)
                mScaled = read_scaled != 0
                br.close()
            } catch (e: IOException) {
            } catch (n: NumberFormatException) {
            }
        }
    }

    fun save_state_to_file(tail: String?) {
        if (display_pos != null) {
            val dir = File("/sdcard/LogMyGsm/prefs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, tail)
            try {
                val bw = BufferedWriter(FileWriter(file))
                bw.write(String.format("%d\n", zoom))
                bw.write(String.format("%d\n", display_pos.X))
                bw.write(String.format("%d\n", display_pos.Y))
                bw.write(String.format("%d\n", map_source!!.get_code()))
                bw.write(String.format("%d\n", if (mScaled) 1 else 0))
                bw.close()
            } catch (e: IOException) {
            }
        }
    }

    private fun notify_position_update() {
        if (mPL != null) {
            mPL!!.display_position_update()
        }
    }

    fun current_tile_string(): String {
        val X: Int
        val Y: Int
        if (display_pos != null) {
            X = display_pos.X shr tile_shift
            Y = display_pos.Y shr tile_shift
        } else {
            X = 0
            Y = 0
        }
        return if (zoom >= 17) {
            val x0 = X / 10000
            val y0 = Y / 10000
            val x1 = X % 10000
            val y1 = Y % 10000
            val xc = ('@'.toInt() + x0).toChar()
            val yc = ('@'.toInt() + y0).toChar()
            String.format("%1c%04d%1c%04d",
                    xc, x1, yc, y1)
        } else {
            String.format("%5d%5d", X, Y)
        }
    }

    fun current_grid_ref(): String {
        return if (display_pos != null) {
            display_pos.grid_ref_5m_nosp()
        } else {
            "NO POSITION"
        }
    }

    fun current_lat(): Double {
        return if (display_pos != null) {
            display_pos.to_lat()
        } else {
            0.0
        }
    }

    fun current_lon(): Double {
        return if (display_pos != null) {
            display_pos.to_lon()
        } else {
            0.0
        }
    }

    fun current_zoom(): Int {
        return zoom
    }

    fun current_mapsource(): MapSource? {
        return map_source
    }

    fun trigger_fetch_around(delta: Int, context: Context?) {
        if (display_pos != null) {
            var i: Int
            var j: Int
            var x0: Int
            val x1: Int
            var y0: Int
            val y1: Int
            val targets: LinkedList<TileStore.TilePos?>
            targets = LinkedList<TileStore.TilePos>()
            x0 = display_pos.X shr tile_shift
            y0 = display_pos.Y shr tile_shift
            x1 = x0 + delta
            y1 = y0 + delta
            x0 -= delta
            y0 -= delta
            i = x0
            while (i <= x1) {
                j = y0
                while (j <= y1) {
                    targets.add(TilePos(zoom, i, j, map_source))
                    j++
                }
                i++
            }
            Downloader.start_multiple_fetch(targets, true, context)
        }
    }

    // Considering the area in view on-screen, consider that same area
    // at zoom+1, zoom+2, ..., zoom+levels, and download any missing
    // map tiles in those regions.  If forced=true, download them again
    // whether they're missing or not
    fun trigger_fetch_tree(levels: Int, forced: Boolean, context: Context?) {
        var dw = last_w shr 1
        var dh = last_h shr 1
        val tiles: LinkedList<TileStore.TilePos?>
        tiles = LinkedList<TileStore.TilePos>()
        if (display_pos == null) {
            return
        }
        if (mScaled) {
            // Viewport is only showing effectively half the number of tiles you'd expect.
            // Avoid over-fetching
            dw = dw shr 1
            dh = dh shr 1
        }
        val px0: Int = display_pos.X - (dw shl pixel_shift)
        val px1: Int = display_pos.X + (dw shl pixel_shift)
        val py0: Int = display_pos.Y - (dh shl pixel_shift)
        val py1: Int = display_pos.Y + (dh shl pixel_shift)
        var deepest = zoom + levels
        if (deepest > MAX_ZOOM) {
            deepest = MAX_ZOOM
        }
        for (my_zoom in MIN_ZOOM..deepest) {
            val tile_shift: Int = Merc28.shift - my_zoom
            val tx0 = px0 shr tile_shift
            val tx1 = px1 shr tile_shift
            val ty0 = py0 shr tile_shift
            val ty1 = py1 shr tile_shift
            val mask = (1 shl my_zoom) - 1
            for (x in tx0..tx1) {
                for (y in ty0..ty1) {
                    tiles.add(TilePos(my_zoom, x and mask, y, map_source))
                }
            }
        }
        Downloader.start_multiple_fetch(tiles, forced, context)
    }

    fun share_grid_ref(_activity: Activity) {
        if (display_pos != null) {
            try {
                val the_intent = Intent()
                the_intent.setAction(Intent.ACTION_SEND)
                the_intent.setType("text/plain")
                the_intent.putExtra(Intent.EXTRA_TEXT, "At " + display_pos.grid_ref_5m_nosp().toString() + " : ")
                _activity.startActivity(Intent.createChooser(the_intent, "Share grid ref using"))
            } catch (e: Exception) {
            }
        }
    }

    // Local UI callbacks
    fun clear_trail() {
        Logger.mTrail!!.clear()
        TileStore.invalidate()
        invalidate()
    }

    override fun zoom_out() {
        if (zoom > MIN_ZOOM) {
            setZoom(zoom - 1)
            notify_position_update()
            maybe_invalidate()
        }
    }

    override fun zoom_in() {
        if (zoom < MAX_ZOOM) {
            setZoom(zoom + 1)
            notify_position_update()
            maybe_invalidate()
        }
    }

    override fun toggle_scaled() {
        mScaled = !mScaled
        invalidate()
    }

    fun is_scaled(): Boolean {
        return mScaled
    }

    private var mLastX = 0f
    private var mLastY = 0f
    override fun cycle_left() {
        map_source = MapSources.predecessor(map_source)
        maybe_invalidate()
    }

    override fun cycle_right() {
        map_source = MapSources.successor(map_source)
        maybe_invalidate()
    }

    private fun check_buttons(x: Float, y: Float): Boolean {
        return if (y < button_size) {
            true
        } else false
    }

    private fun try_recentre(x: Float, y: Float): Boolean {
        // Hit on the centre cross-hair region to re-centre the map on the GPS fix
        if (y > getHeight() - button_size shr 1 &&
                y < getHeight() + button_size shr 1 &&
                x > getWidth() - button_size shr 1 &&
                x < getWidth() + button_size shr 1) {
            // If (estimated_pos == null) here, it means no history of recent GPS fixes;
            // refuse to drop the display position then
            if (estimated_pos != null) {
                display_pos = Merc28(estimated_pos)
                is_dragged = false
                notify_position_update()
                invalidate()
                return true
            } else {
                val tower_pos = Merc28(0, 0)
                if (TowerLine.find_tower_pos(0, tower_pos)) {
                    display_pos = tower_pos
                    is_dragged = false
                    notify_position_update()
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    private fun try_start_drag(x: Float, y: Float): Boolean {
        // Not inside the zoom buttons - initiate drag
        if (display_pos != null) {
            mLastX = x
            mLastY = y
            is_dragged = true
            return true
        }
        return false
    }

    @Override
    fun onTouchEvent(event: MotionEvent): Boolean {
        val action: Int = event.getAction()
        val x: Float = event.getX()
        val y: Float = event.getY()
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (check_buttons(x, y)) {
                    mAB.decode(x.toInt())
                    return true
                }
                if (try_recentre(x, y)) {
                    return true
                }
                if (try_start_drag(x, y)) {
                    return true
                }
            }
            MotionEvent.ACTION_MOVE ->         // Prevent a drag starting on a zoom button etc, which would be bogus.
                if (is_dragged && !check_buttons(x, y)) {
                    val dx: Float
                    val dy: Float
                    dx = x - mLastX
                    dy = y - mLastY
                    display_pos!!.X = display_pos!!.X - (0.5 + drag_scale * dx).toInt() and Merc28.shift_mask
                    display_pos!!.Y -= (0.5 + drag_scale * dy).toInt()
                    if (display_pos!!.Y < 0) {
                        display_pos!!.Y = 0
                    }
                    if (display_pos!!.Y > Merc28.shift_mask) {
                        display_pos!!.Y = Merc28.shift_mask
                    }
                    mLastX = x
                    mLastY = y
                    notify_position_update()
                    invalidate()
                    return true
                }
            else -> return false
        }
        return false
    }

    // Called by framework
    @Override
    protected fun onDraw(canvas: Canvas) {
        if (display_pos == null) {
            canvas.drawColor(Color.rgb(40, 40, 40))
            val foo2: String = String.format("No fix")
            canvas.drawText(foo2, 10, 80, red_paint)
        } else {
            redraw_map(canvas)
        }
    }

    fun da_offset_metres(): Double {
        return if (estimated_pos != null && display_pos != null) {
            display_pos.metres_away(estimated_pos)
        } else {
            0
        }
    }

    // -----------
    inner class LocationOffset {
        var known = false
        var dragged = false
        var metres = 0.0
        var bearing = 0.0

        constructor(other_location: Merc28) {
            known = true
            metres = display_pos!!.metres_away(other_location)
            bearing = display_pos!!.bearing_to(other_location)
            dragged = is_dragged
        }

        constructor() {}
    }

    fun get_tower_offset(): LocationOffset {
        val result: LocationOffset = LocationOffset()
        if (display_pos == null) {
            result.known = false
        } else {
            val tower_pos = Merc28(0, 0)
            if (TowerLine.find_tower_pos(0, tower_pos)) {
                result.metres = display_pos.metres_away(tower_pos)
                result.bearing = display_pos.bearing_to(tower_pos)
                result.known = true
                result.dragged = is_dragged
            } else {
                result.known = false
            }
        }
        return result
    }

    companion object {
        private const val TAG = "Map"
        private const val MAX_ZOOM = 18
        const val MIN_ZOOM = 2
        const val TILE_SCALING = 2.0f
    }

    // --------------------------------------------------------------------------
    init {
        red_paint = Paint()
        red_paint.setStrokeWidth(2)
        red_paint.setColor(Color.RED)
        red_stroke_paint = Paint()
        red_stroke_paint.setStrokeWidth(2)
        red_stroke_paint.setColor(Color.RED)
        red_stroke_paint.setStyle(Paint.Style.STROKE)
        red_double_stroke_paint = Paint()
        red_double_stroke_paint.setStrokeWidth(4)
        red_double_stroke_paint.setColor(Color.RED)
        red_double_stroke_paint.setStyle(Paint.Style.STROKE)
        button_stroke_paint = Paint()
        button_stroke_paint.setStrokeWidth(4)
        button_stroke_paint.setColor(Color.BLACK)
        button_stroke_paint.setStyle(Paint.Style.STROKE)
        val res: Resources = context.getResources()
        val dest_text_size: Int = res.getDimensionPixelSize(R.dimen.distanceFontSize)
        mRB = RoutingsBar(dest_text_size)
        mAB = MapActionBar(context, this)
        setZoom(14)
        map_source = MapSources.get_default()
        estimated_pos = null
        display_pos = null
        last_h = 0
        last_w = last_h
        mScaled = false
    }
}