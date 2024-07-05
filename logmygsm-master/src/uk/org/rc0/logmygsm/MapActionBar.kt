
package uk.org.rc0.logmygsm

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class MapActionBar(context: Context, target: Callbacks) {
    // This is the bar that is instanced across the top of the map
    internal interface Callbacks {
        fun toggle_scaled()
        fun zoom_in()
        fun zoom_out()
        fun cycle_left()
        fun cycle_right()
    }

    // ---------------------------------
    private val target: Callbacks
    private val paint: Paint
    private val cycle_left_bm: Bitmap
    private val cycle_right_bm: Bitmap
    private val binocular_bm: Bitmap
    private val zoom_out_bm: Bitmap
    private val zoom_in_bm: Bitmap
    private var last_w = -1
    private var last_button_height = -1
    private var chunk = -1
    private var button_width_2 = 0
    private var button_height_2 = 0
    private var button_radius = 0
    private var button_offset = 0
    private var button_half_line = 0
    private var r_zin: Rect? = null
    private var r_zout: Rect? = null
    private var r_right: Rect? = null
    private var r_left: Rect? = null
    private var r_2x: Rect? = null
    private val N_BUTTONS = 5
    private fun update_dimensions(w: Int, button_height: Int) {
        chunk = w / (2 * N_BUTTONS)
        button_height_2 = button_height shr 1
        button_radius = button_height_2 - (button_height_2 shr 2)
        button_offset = button_radius + (button_radius shr 1)
        button_half_line = button_radius - (button_radius shr 2)
        button_width_2 = button_height_2
        if (button_width_2 > chunk) {
            button_width_2 = chunk
        }
        r_zin = Rect(chunk * 1 - button_width_2, 0, chunk * 1 + button_width_2, button_height)
        r_zout = Rect(chunk * 9 - button_width_2, 0, chunk * 9 + button_width_2, button_height)
        r_left = Rect(chunk * 3 - button_width_2, 0, chunk * 3 + button_width_2, button_height)
        r_right = Rect(chunk * 7 - button_width_2, 0, chunk * 7 + button_width_2, button_height)
        r_2x = Rect(chunk * 5 - button_width_2, 0, chunk * 5 + button_width_2, button_height)
        last_w = w
        last_button_height = button_height
    }

    fun draw(c: Canvas, w: Int, button_height: Int) {
        if (w != last_w || last_button_height != button_height) {
            update_dimensions(w, button_height)
        }
        c.drawBitmap(zoom_in_bm, null, r_zin, null)
        c.drawBitmap(zoom_out_bm, null, r_zout, null)
        c.drawBitmap(cycle_left_bm, null, r_left, null)
        c.drawBitmap(cycle_right_bm, null, r_right, null)
        c.drawBitmap(binocular_bm, null, r_2x, null)
    }

    fun decode(x: Int) {
        val which = x / (chunk shl 1) // assume truncate
        when (which) {
            0 -> target.zoom_in()
            1 -> target.cycle_left()
            2 -> target.toggle_scaled()
            3 -> target.cycle_right()
            4 -> target.zoom_out()
            else -> {
            }
        }
    }

    // ---------------------------------
    init {
        val res: Resources = context.getResources()
        this.target = target
        paint = Paint()
        paint.setStrokeWidth(4)
        paint.setColor(Color.BLACK)
        paint.setStyle(Paint.Style.STROKE)
        cycle_left_bm = BitmapFactory.decodeResource(res, R.drawable.cycle_left_64)
        cycle_right_bm = BitmapFactory.decodeResource(res, R.drawable.cycle_right_64)
        binocular_bm = BitmapFactory.decodeResource(res, R.drawable.binocular_64)
        zoom_out_bm = BitmapFactory.decodeResource(res, R.drawable.zoom_out_64)
        zoom_in_bm = BitmapFactory.decodeResource(res, R.drawable.zoom_in_64)
    }
}