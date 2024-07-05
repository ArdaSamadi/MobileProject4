
package uk.org.rc0.logmygsm

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.util.Log
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object Menus2 {
    private const val OPTION_GROUP_MASK_HI = 0xf00
    private const val OPTION_GROUP_MASK_LO = 0x0ff
    const val OPTION_LOCAL_BASE = 0x100
    const val OPTION_MAP_BASE = 0x200
    const val OPTION_DOWNLOAD_BASE = 0x300
    const val OPTION_TOGGLE_TOWERLINE = OPTION_LOCAL_BASE or 0xf
    const val TILE_SCALING = 0xff
    const val OPTION_TILE_SCALING = OPTION_MAP_BASE or TILE_SCALING
    private const val DOWNLOAD_SINGLE = 0
    private const val DOWNLOAD_MISSING = 1
    private const val DOWNLOAD_33 = 2
    private const val DOWNLOAD_55 = 3
    private const val DOWNLOAD_LEV_0 = 4
    private const val DOWNLOAD_LEV_1 = 5
    private const val DOWNLOAD_LEV_2 = 6
    private const val DOWNLOAD_LEV_0_FORCE = 7
    private const val DOWNLOAD_LEV_0_ROUTE = 8
    fun insert_maps_menu(parent: Menu): Array<MenuItem?> {
        val sub: SubMenu = parent.addSubMenu(0, 0, Menu.NONE, "Maps")
        var toggle: MenuItem
        val toggles: Array<MenuItem?> = arrayOfNulls<MenuItem>(2)
        sub.setIcon(android.R.drawable.ic_menu_mapmode)
        for (source in MapSources.sources) {
            sub.add(Menu.NONE, OPTION_MAP_BASE + source.get_code(), Menu.NONE, source.get_menu_name())
        }
        toggles[0] = sub.add(Menu.NONE, OPTION_TILE_SCALING, Menu.NONE,
                String.format("Scale tiles by %.1fx", Map.TILE_SCALING))
        toggles[0].setCheckable(true)
        toggles[1] = sub.add(Menu.NONE, OPTION_TOGGLE_TOWERLINE, Menu.NONE, "Show towerline")
        toggles[1].setCheckable(true)
        return toggles
    }

    fun insert_download_menu(parent: Menu) {
        val m_download: SubMenu = parent.addSubMenu(0, 0, Menu.NONE, "Download(s)")
        m_download.setIcon(android.R.drawable.ic_menu_view)
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_SINGLE, Menu.NONE, "Central tile")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_0, Menu.NONE, "Missing ..,0 levels")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_1, Menu.NONE, "Missing ..,0,1 levels")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_2, Menu.NONE, "Missing ..,0,1,2 levels")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_0_ROUTE, Menu.NONE, "Missing ..,0 on route")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_MISSING, Menu.NONE, "Recent missing")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_0_FORCE, Menu.NONE, "Force ..,0 levels")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_33, Menu.NONE, "3x3 region")
        m_download.add(Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_55, Menu.NONE, "5x5 region")
    }

    fun decode_download_option(subcode: Int, context: Context?, map: Map?): Boolean {
        return when (subcode) {
            DOWNLOAD_SINGLE -> {
                map!!.trigger_fetch_around(0, context)
                true
            }
            DOWNLOAD_MISSING -> {
                TileStore.trigger_fetch(context)
                true
            }
            DOWNLOAD_33 -> {
                map!!.trigger_fetch_around(1, context)
                true
            }
            DOWNLOAD_55 -> {
                map!!.trigger_fetch_around(2, context)
                true
            }
            DOWNLOAD_LEV_0 -> {
                map!!.trigger_fetch_tree(0, false, context)
                true
            }
            DOWNLOAD_LEV_1 -> {
                map!!.trigger_fetch_tree(1, false, context)
                true
            }
            DOWNLOAD_LEV_2 -> {
                map!!.trigger_fetch_tree(2, false, context)
                true
            }
            DOWNLOAD_LEV_0_FORCE -> {
                map!!.trigger_fetch_tree(0, true, context)
                true
            }
            DOWNLOAD_LEV_0_ROUTE -> {
                Downloader.trigger_fetch_route(0, false, context, map)
                true
            }
            else -> false
        }
    }

    fun decode_map_option(subcode: Int, map: Map?): Boolean {
        val source: MapSource
        return when (subcode) {
            TILE_SCALING -> {
                Log.i("Menus", "Tile scaling decoded")
                map!!.toggle_scaled()
                true
            }
            else -> {
                source = MapSources.lookup(subcode)
                if (source != null) {
                    //Log.i("Menus", "Match " + source.get_menu_name());
                    map!!.select_map_source(source)
                    true
                } else {
                    Log.i("Menus", "No match for code $subcode")
                    false
                }
            }
        }
    }

    fun group(code: Int): Int {
        return code and OPTION_GROUP_MASK_HI
    }

    fun option(code: Int): Int {
        return code and OPTION_GROUP_MASK_LO
    }
}