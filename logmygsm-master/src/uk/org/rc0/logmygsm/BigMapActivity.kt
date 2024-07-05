
package uk.org.rc0.logmygsm

import android.app.Activity
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.app.AlertDialog
import android.content.DialogInterface
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class BigMapActivity : Activity(), Map.PositionListener {
    private var myCellReceiver: CellUpdateReceiver? = null
    private var myGPSReceiver: GPSUpdateReceiver? = null
    private var mMap: WaypointEditMap? = null
    private var mAddButton: ImageButton? = null
    private var mDeleteButton: ImageButton? = null
    private var mDeleteVisibleButton: ImageButton? = null
    private var mDeleteAllButton: ImageButton? = null
    private var mSetDestinationButton: ImageButton? = null
    private var mAddLMButton: ImageButton? = null
    private var mDeleteLMButton: ImageButton? = null
    private var mCutRouteButton: ImageButton? = null
    private var summaryText: TextView? = null
    private var gridRefText: TextView? = null
    private var mTileScalingToggle: MenuItem? = null
    private var mTowerlineToggle: MenuItem? = null
    @Override
    fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setContentView(R.layout.bigmap)
        mMap = findViewById(R.id.big_map) as WaypointEditMap?
        mMap.restore_state_from_file(PREFS_FILE)
        mAddButton = findViewById(R.id.add_button) as ImageButton?
        mDeleteButton = findViewById(R.id.delete_button) as ImageButton?
        mDeleteVisibleButton = findViewById(R.id.delete_visible_button) as ImageButton?
        mDeleteAllButton = findViewById(R.id.delete_all_button) as ImageButton?
        mSetDestinationButton = findViewById(R.id.set_destination_button) as ImageButton?
        mAddLMButton = findViewById(R.id.add_landmark_button) as ImageButton?
        mDeleteLMButton = findViewById(R.id.del_landmark_button) as ImageButton?
        mCutRouteButton = findViewById(R.id.cut_route_button) as ImageButton?
        summaryText = findViewById(R.id.big_summary) as TextView?
        gridRefText = findViewById(R.id.big_grid_ref) as TextView?
        mAddButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.add_waypoint()
            }
        })
        mDeleteButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.delete_waypoint()
            }
        })
        mDeleteVisibleButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.delete_visible_waypoints()
            }
        })
        mDeleteAllButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.delete_all_waypoints()
            }
        })
        mSetDestinationButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.set_destination_waypoint()
            }
        })
        mAddLMButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.add_landmark()
            }
        })
        mDeleteLMButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.delete_landmark()
            }
        })
        mCutRouteButton.setOnClickListener(object : OnClickListener() {
            fun onClick(v: View?) {
                mMap!!.cut_route()
            }
        })
        mMap.register_position_listener(this)
    }

    @Override
    fun onStart() {
        super.onStart()
    }

    @Override
    fun onStop() {
        super.onStop()
    }

    @Override
    fun onResume() {
        Logger.stop_tracing = false
        startService(Intent(this, Logger::class.java))
        var filter: IntentFilter?
        filter = IntentFilter(Logger.UPDATE_GPS)
        myGPSReceiver = GPSUpdateReceiver()
        registerReceiver(myGPSReceiver, filter)
        filter = IntentFilter(Logger.UPDATE_CELL)
        myCellReceiver = CellUpdateReceiver()
        registerReceiver(myCellReceiver, filter)
        mMap.update_map()
        super.onResume()
    }

    @Override
    fun onPause() {
        unregisterReceiver(myCellReceiver)
        unregisterReceiver(myGPSReceiver)
        // It seems wasteful to do this here, but there is no other safe opportunity to do so -
        // in effect we are 'committing' the user's changes at this point.
        mMap.save_state_to_file(PREFS_FILE)
        TileStore.sleep_invalidate()
        super.onPause()
    }

    @Override
    fun onCreateOptionsMenu(menu: Menu): Boolean {
        val toggles: Array<MenuItem?> = Menus2.insert_maps_menu(menu)
        mTileScalingToggle = toggles[0]
        mTowerlineToggle = toggles[1]
        Menus2.insert_download_menu(menu)
        val m_share: MenuItem = menu.add(Menu.NONE, OPTION_SHARE, Menu.NONE, "Share OS ref")
        m_share.setIcon(android.R.drawable.ic_menu_share)
        val m_logmark: MenuItem = menu.add(Menu.NONE, OPTION_LOG_MARKER, Menu.NONE, "Bookmark")
        m_logmark.setIcon(android.R.drawable.ic_menu_save)
        val m_clear_waypoints: MenuItem = menu.add(Menu.NONE, OPTION_CLEAR_TRAIL, Menu.NONE, "Clear trail")
        m_clear_waypoints.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
        val m_exit: MenuItem = menu.add(Menu.NONE, OPTION_EXIT, Menu.NONE, "Exit")
        m_exit.setIcon(android.R.drawable.ic_lock_power_off)
        return true
    }

    @Override
    fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        mTileScalingToggle.setChecked(mMap.is_scaled())
        mTowerlineToggle.setChecked(TowerLine.is_active())
        return true
    }

    private inner class TrailDeleter : Confirm.Callback {
        override fun do_when_confirmed() {
            mMap.clear_trail()
        }
    }

    @Override
    fun onOptionsItemSelected(item: MenuItem): Boolean {
        val code: Int = item.getItemId()
        val group: Int = Menus2.group(code)
        val option: Int = Menus2.option(code)
        return if (group == Menus2.OPTION_DOWNLOAD_BASE) {
            Menus2.decode_download_option(Menus2.option(code), this, mMap)
        } else if (group == Menus2.OPTION_MAP_BASE) {
            Menus2.decode_map_option(option, mMap)
        } else if (group == Menus2.OPTION_LOCAL_BASE) {
            when (code) {
                OPTION_EXIT -> {
                    Logger.stop_tracing = true

                    // If app stays in memory, start 'clean' next time wrt tile downloading
                    TileStore.refresh_epoch()
                    // avoid holding onto oodles of memory at Application level...
                    TileStore.invalidate()

                    // Send an intent to the Logger to get it to exit promptly even if there are no
                    // GPS or cell updates soon
                    val quit_intent = Intent(Logger.QUIT_LOGGER)
                    sendBroadcast(quit_intent)
                    finish()
                    true
                }
                OPTION_CLEAR_TRAIL -> {
                    val confirm = Confirm(this, "Clear the trail?", TrailDeleter())
                    true
                }
                OPTION_LOG_MARKER -> {
                    Logger.do_bookmark(this)
                    true
                }
                OPTION_SHARE -> {
                    mMap.share_grid_ref(this)
                    true
                }
                Menus2.OPTION_TOGGLE_TOWERLINE -> {
                    TowerLine.toggle_active()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

    private fun updateDisplay() {
        val summaryString: String
        if (Logger.validFix) {
            summaryString = String.format("%9d %2dasu %3dm %s",
                    Logger.lastCid,
                    Logger.lastASU,
                    Logger.lastAcc,
                    mMap.current_tile_string()
            )
        } else {
            summaryString = String.format("%9d %2dasu  GPS? %s",
                    Logger.lastCid,
                    Logger.lastASU,
                    mMap.current_tile_string()
            )
        }
        if (gridRefText != null) {
            summaryText.setText(summaryString)
            gridRefText.setText(String.format("%2s / %.4f %.4f",
                    mMap.current_grid_ref(),
                    mMap.current_lat(),
                    mMap.current_lon()))
        } else {
            summaryText.setText(String.format("%s %s %.4f %.4f",
                    summaryString, mMap.current_grid_ref(),
                    mMap.current_lat(), mMap.current_lon()))
        }
    }

    override fun display_position_update() {
        updateDisplay()
    }

    inner class GPSUpdateReceiver : BroadcastReceiver() {
        @Override
        fun onReceive(context: Context?, intent: Intent?) {
            mMap.update_map()
            updateDisplay()
        }
    }

    inner class CellUpdateReceiver : BroadcastReceiver() {
        @Override
        fun onReceive(context: Context?, intent: Intent?) {
            // update the map in case the current cell has changed.
            if (TowerLine.is_active()) {
                mMap.update_map()
            }
            updateDisplay()
        }
    }

    companion object {
        private const val PREFS_FILE = "prefs2.txt"
        private val OPTION_CLEAR_TRAIL: Int = Menus2.OPTION_LOCAL_BASE or 0x1
        private val OPTION_SHARE: Int = Menus2.OPTION_LOCAL_BASE or 0x2
        private val OPTION_LOG_MARKER: Int = Menus2.OPTION_LOCAL_BASE or 0x3
        private val OPTION_EXIT: Int = Menus2.OPTION_LOCAL_BASE or 0x4
    }
}