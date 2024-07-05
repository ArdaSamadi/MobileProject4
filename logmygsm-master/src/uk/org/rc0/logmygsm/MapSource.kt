
package uk.org.rc0.logmygsm

import android.graphics.Bitmap
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import java.io.File
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileNotFoundException
import java.io.IOException
import java.util.HashMap
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class MapSource(_menu_name: String, _path_segment: String, _code: Int) {
    private var menu_name = ""
    var path_segment = ""
    var index: Int
    private val code: Int
    fun get_menu_name(): String {
        return menu_name
    }

    fun get_tile_path(zoom: Int, x: Int, y: Int): String {
        return String.format("%s/%s/%d/%d/%d.png.tile",
                path_start, path_segment, zoom, x, y)
    }

    // Override in sub-classes for map types that can support download
    fun get_download_url(zoom: Int, x: Int, y: Int): String? {
        return null
    }

    fun apply_overlay(bm: Bitmap?, zoom: Int, tile_x: Int, tile_y: Int) {
        return
    }

    fun get_code(): Int {
        return code
    }

    companion object {
        private const val last_hope_path = "/sdcard/LogMyGsm/tiles"
        private val possible_paths = arrayOf(
                "/sdcard/external_sd/Maverick/tiles",
                "/sdcard/Maverick/tiles",
                "/sdcard/external_sd/LogMyGsm/tiles",
                last_hope_path
        )
        private const val PREFS_DIR = "/sdcard/LogMyGsm/prefs/"
        private const val URL_MAP_FILE = PREFS_DIR + "urls.txt"
        var url_map: HashMap<String, String>? = null
        const val KEY_MAPNIK = "MAPNIK"
        const val KEY_OPENCYCLEMAP = "OPENCYCLEMAP"
        const val KEY_OS_1 = "OS_1"
        const val KEY_OS_2 = "OS_2"
        const val KEY_AERIAL_1 = "AERIAL_1"
        const val KEY_AERIAL_2 = "AERIAL_2"
        var path_start: String? = null
        fun read_url_map() {
            // Store download URLs in a text file outside of the application
            url_map = HashMap<String, String>()
            // Put dummy values in the map so at least something exists if URL_MAP_FILE
            // fails to load
            url_map.put(KEY_MAPNIK, "//127.0.0.1/%d/%d/%d.png")
            url_map.put(KEY_OPENCYCLEMAP, "//127.0.0.1/%d/%d/%d.png")
            url_map.put(KEY_OS_1, "//127.0.0.1/")
            url_map.put(KEY_OS_2, "")
            url_map.put(KEY_AERIAL_1, "//127.0.0.1/")
            url_map.put(KEY_AERIAL_2, "")
            val f = File(URL_MAP_FILE)
            val br: BufferedReader
            var is_open = false
            if (f.exists()) {
                try {
                    br = BufferedReader(FileReader(URL_MAP_FILE))
                    is_open = true
                    try {
                        var key: String
                        var value: String
                        while (true) {
                            key = br.readLine()
                            if (key.compareTo("END") === 0) {
                                break
                            }
                            value = br.readLine()
                            url_map.put(key, value)
                        }
                        // exception at EOF or any other error
                    } catch (e: IOException) {
                    }
                    if (is_open) {
                        br.close()
                    }
                } catch (e: IOException) {
                }
            }
        }

        // Discover where the tiles are stored.  Share tiles with Maverick (-Pro) if
        // possible, otherwise use our own storage
        fun init() {
            for (i in possible_paths.indices) {
                val f = File(possible_paths[i])
                if (f.isDirectory()) {
                    path_start = String(possible_paths[i])
                    break
                }
            }

            // We must force a path to exist otherwise there is nowhere to download new
            // tiles into
            if (path_start == null) {
                path_start = String(last_hope_path)
                val dir = File(path_start)
                dir.mkdirs()
            }
            read_url_map()
        }

        val qk03: CharArray = "0123".toCharArray()
        fun get_quadkey(zoom: Int, x: Int, y: Int): String {
            val quadkey = CharArray(zoom)
            var i: Int
            i = 0
            while (i < zoom) {
                val j = zoom - 1 - i
                val xx = x shr j and 1
                val yy = y shr j and 1
                quadkey[i] = qk03[xx + (yy shl 1)]
                i++
            }
            return String(quadkey)
        }
    }

    init {
        menu_name = _menu_name
        path_segment = _path_segment
        code = _code
        index = -1
    }
} // ------------------------------------------------------------------

internal class MapSource_Mapnik(_menu_name: String, _path_segment: String, _code: Int) : MapSource(_menu_name, _path_segment, _code) {
    override fun get_download_url(zoom: Int, x: Int, y: Int): String {
        return String.format(url_map.get(KEY_MAPNIK), zoom, x, y)
    }
} // ------------------------------------------------------------------

internal class MapSource_Cycle(_menu_name: String, _path_segment: String, _code: Int) : MapSource(_menu_name, _path_segment, _code) {
    override fun get_download_url(zoom: Int, x: Int, y: Int): String {
        return String.format(url_map.get(KEY_OPENCYCLEMAP), zoom, x, y)
    }
} // ------------------------------------------------------------------

internal class MapSource_OS(_menu_name: String, _path_segment: String, _code: Int) : MapSource(_menu_name, _path_segment, _code) {
    override fun get_download_url(zoom: Int, x: Int, y: Int): String {
        return String(url_map.get(KEY_OS_1) + get_quadkey(zoom, x, y) + url_map.get(KEY_OS_2))
    }
} // ------------------------------------------------------------------

internal class MapSource_Bing_Aerial(_menu_name: String, _path_segment: String, _code: Int) : MapSource(_menu_name, _path_segment, _code) {
    override fun get_download_url(zoom: Int, x: Int, y: Int): String {
        return String(url_map.get(KEY_AERIAL_1) + get_quadkey(zoom, x, y) + url_map.get(KEY_AERIAL_2))
    }

    override fun get_tile_path(zoom: Int, x: Int, y: Int): String {
        return String.format("%s/%s/%d/%d/%d.jpg.tile",
                path_start, path_segment, zoom, x, y)
    }
} // ------------------------------------------------------------------

internal class MapSource_Overlay(_menu_name: String, _path_segment: String, _code: Int, private val overlay_file: String, private val overlay_param: Int) : MapSource_Mapnik(_menu_name, _path_segment, _code) {
    override fun apply_overlay(bm: Bitmap?, zoom: Int, tile_x: Int, tile_y: Int) {
        Overlay.apply(bm, overlay_file, overlay_param, zoom, tile_x, tile_y)
    }
} // ------------------------------------------------------------------

internal object MapSources {
    const val MAP_OSM = 2
    const val MAP_OS = 3
    const val MAP_OPEN_CYCLE = 4
    const val MAP_BING_AERIAL = 5
    const val MAP_A_2G_OVL = 16
    const val MAP_A_3G_OVL = 17
    const val MAP_A_2G_TODO_OVL = 20
    const val MAP_A_3G_TODO_OVL = 21
    const val MAP_B_2G_OVL = 24
    const val MAP_B_3G_OVL = 25
    const val MAP_B_2G_TODO_OVL = 28
    const val MAP_B_3G_TODO_OVL = 29
    const val MAP_C_2G_OVL = 32
    const val MAP_C_3G_OVL = 33
    const val MAP_C_2G_TODO_OVL = 36
    const val MAP_C_3G_TODO_OVL = 37
    val sources = arrayOf(
            MapSource_Cycle("Open Cycle Map", "OSM Cycle Map", MAP_OPEN_CYCLE),
            MapSource_Bing_Aerial("Bing Aerial", "Microsoft Hybrid", MAP_BING_AERIAL),
            MapSource_OS("Ordnance Survey", "Ordnance Survey Explorer Maps (UK)", MAP_OS),
            MapSource_Mapnik("OpenStreetMap", "mapnik", MAP_OSM),
            MapSource_Overlay("NetA 2G", "mapnik", MAP_A_2G_OVL, "overlay_a.db", 2),
            MapSource_Overlay("NetA 3G", "mapnik", MAP_A_3G_OVL, "overlay_a.db", 3),
            MapSource_Overlay("NetA 2G todo", "mapnik", MAP_A_2G_TODO_OVL, "overlay_a.db", 8 or 2),
            MapSource_Overlay("NetA 3G todo", "mapnik", MAP_A_3G_TODO_OVL, "overlay_a.db", 8 or 3),
            MapSource_Overlay("NetB 2G", "mapnik", MAP_B_2G_OVL, "overlay_b.db", 2),
            MapSource_Overlay("NetB 3G", "mapnik", MAP_B_3G_OVL, "overlay_b.db", 3),
            MapSource_Overlay("NetB 2G todo", "mapnik", MAP_B_2G_TODO_OVL, "overlay_b.db", 8 or 2),
            MapSource_Overlay("NetB 3G todo", "mapnik", MAP_B_3G_TODO_OVL, "overlay_b.db", 8 or 3),
            MapSource_Overlay("NetC 2G", "mapnik", MAP_C_2G_OVL, "overlay_c.db", 2),
            MapSource_Overlay("NetC 3G", "mapnik", MAP_C_3G_OVL, "overlay_c.db", 3),
            MapSource_Overlay("NetC 2G todo", "mapnik", MAP_C_2G_TODO_OVL, "overlay_c.db", 8 or 2),
            MapSource_Overlay("NetC 3G todo", "mapnik", MAP_C_3G_TODO_OVL, "overlay_c.db", 8 or 3))

    fun lookup(code: Int): MapSource? {
        for (source in sources) {
            if (source.get_code() == code) {
                return source
            }
        }
        return null
    }

    fun init_indices() {
        for (i in sources.indices) {
            sources[i].index = i
        }
    }

    fun successor(current: MapSource?): MapSource {
        if (current!!.index < 0) {
            init_indices()
        }
        var index = current.index + 1
        if (index >= sources.size) {
            index = 0
        }
        return sources[index]
    }

    fun predecessor(current: MapSource?): MapSource {
        if (current!!.index < 0) {
            init_indices()
        }
        var index = current.index - 1
        if (index < 0) {
            index = sources.size - 1
        }
        return sources[index]
    }

    fun get_default(): MapSource? {
        return lookup(MAP_OSM)
    }
}