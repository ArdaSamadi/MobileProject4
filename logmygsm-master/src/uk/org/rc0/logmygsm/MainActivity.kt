
package uk.org.rc0.logmygsm
//import android.R.drawable;
import android.app.Activity
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.widget.TextView
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class MainActivity : Activity(), Map.PositionListener {
    private var latText: TextView? = null
    private var lonText: TextView? = null
    private var accText: TextView? = null
    private var ageText: TextView? = null
    private var satText: TextView? = null
    private var cidText: TextView? = null
    private var twrText: TextView? = null
    private var netlacmncText: TextView? = null
    private var asuText: TextView? = null
    private var daOffsetText: TextView? = null
    private var countText: TextView? = null
    private var odoText: TextView? = null
    private var cidHistoryText: TextView? = null
    private var gridRefText: TextView? = null
    private var myCellReceiver: CellUpdateReceiver? = null
    private var myGPSReceiver: GPSUpdateReceiver? = null
    private var mMap: Map? = null
    private var mTileScalingToggle: MenuItem? = null
    private var mTowerlineToggle: MenuItem? = null

    /** Called when the activity is first created.  */
    @Override
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        latText = findViewById(R.id.latitude) as TextView?
        lonText = findViewById(R.id.longitude) as TextView?
        accText = findViewById(R.id.accuracy) as TextView?
        ageText = findViewById(R.id.age) as TextView?
        satText = findViewById(R.id.sat) as TextView?
        cidText = findViewById(R.id.cid) as TextView?
        twrText = findViewById(R.id.twr) as TextView?
        netlacmncText = findViewById(R.id.net_lac_mnc) as TextView?
        asuText = findViewById(R.id.asu) as TextView?
        countText = findViewById(R.id.count) as TextView?
        odoText = findViewById(R.id.odo) as TextView?
        cidHistoryText = findViewById(R.id.cid_history) as TextView?
        cidHistoryText.setMovementMethod(ScrollingMovementMethod())
        daOffsetText = findViewById(R.id.da_offset) as TextView?
        gridRefText = findViewById(R.id.grid_ref) as TextView?
        mMap = findViewById(R.id.map) as Map?
        mMap!!.restore_state_from_file(PREFS_FILE)
        mMap!!.register_position_listener(this)
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
        filter = IntentFilter(Logger.UPDATE_CELL)
        myCellReceiver = CellUpdateReceiver()
        registerReceiver(myCellReceiver, filter)
        filter = IntentFilter(Logger.UPDATE_GPS)
        myGPSReceiver = GPSUpdateReceiver()
        registerReceiver(myGPSReceiver, filter)
        updateCellDisplay()
        updateGPSDisplay()
        mMap!!.update_map()
        super.onResume()
    }

    @Override
    fun onPause() {
        unregisterReceiver(myCellReceiver)
        unregisterReceiver(myGPSReceiver)
        // It seems wasteful to do this here, but there is no other safe opportunity to do so -
        // in effect we are 'committing' the user's changes at this point.
        mMap!!.save_state_to_file(PREFS_FILE)
        // Dump the old tiles that haven't been rescued yet - avoid the most gratuituous memory wastage
        TileStore.sleep_invalidate()
        super.onPause()
    }

    private fun updateCidHistory(current_time: Long) {
        val out = StringBuffer()
        // There's no point in showing the current cell as that's shown in other fields
        for (i in 1 until Logger.MAX_RECENT) {
            if (Logger.recent_cids != null &&
                    Logger.recent_cids.get(i) != null &&
                    Logger.recent_cids.get(i).cid >= 0) {
                val age: Long = (500 + current_time - Logger.recent_cids.get(i).lastMillis) / 1000
                if (age < 60) {
                    val temp: String = String.format("%9d   0:%02d %4d\n",
                            Logger.recent_cids.get(i).cid,
                            age,
                            Logger.recent_cids.get(i).handoff)
                    out.append(temp)
                } else {
                    val temp: String = String.format("%9d %3d:%02d %4d\n",
                            Logger.recent_cids.get(i).cid,
                            age / 60,
                            age % 60,
                            Logger.recent_cids.get(i).handoff)
                    out.append(temp)
                }
            }
        }
        cidHistoryText.setText(out)
    }

    private fun bad_cid(cid: Int): Boolean {
        return when (cid) {
            0 -> true
            else -> false
        }
    }

    private fun odd_cid(cid: Int): Boolean {
        return when (cid) {
            50594049 -> true
            else -> false
        }
    }

    private fun tower_update() {
        val tow_off: Map.LocationOffset = mMap!!.get_tower_offset()
        if (tow_off.known === false) {
            twrText.setText("TOWER?")
            twrText.setTextColor(Color.RED)
        } else {
            val distance: String
            val bearing: String
            val relative: String
            if (tow_off.metres < 1000.0) {
                distance = String.format("%3dm", tow_off.metres as Int)
            } else {
                distance = String.format("%.1fkm", tow_off.metres * 0.001)
            }
            bearing = String.format("%03d\u00B0", tow_off.bearing as Int)
            if (Logger.validFix && !tow_off.dragged) {
                var angle: Int = tow_off.bearing as Int - Logger.lastBearing
                if (angle < -180) {
                    angle += 360
                }
                if (angle >= 180) {
                    angle -= 360
                }
                if (angle < 0) {
                    relative = String.format(" %03dL", -angle)
                } else {
                    relative = String.format(" %03dR", angle)
                }
            } else {
                relative = ""
            }
            twrText.setText("$distance $bearing$relative")
            twrText.setTextColor(Color.WHITE)
        }
    }

    private fun position_update() {
        if (Logger.validFix) {
            val daOffsetString: String
            val da_offset_m: Double = mMap!!.da_offset_metres()
            if (da_offset_m == 0.0) {
                daOffsetString = String.format("%5.1f mph",
                        Logger.lastSpeed * 2.237)
            } else if (da_offset_m < 10000) {
                daOffsetString = String.format("DA %5dm", da_offset_m.toInt())
            } else {
                daOffsetString = String.format("DA %5.1fkm", 0.001 * da_offset_m)
            }
            daOffsetText.setText(daOffsetString)
        } else {
            daOffsetText.setText("DA -----")
        }
        val odoString: String = Util.pretty_distance(Logger.get_metres_covered() as Float)
        val odoString2: String = String.format("%7s %2d", odoString, mMap!!.current_zoom())
        odoText.setText(odoString2)
        val gridString: String = mMap!!.current_grid_ref()
        gridRefText.setText(gridString)
    }

    override fun display_position_update() {
        position_update()
        tower_update()
    }

    private fun updateCellDisplay() {
        val current_time: Long = System.currentTimeMillis()
        val cidString: String = String.format("%d",
                Logger.lastCid)
        cidText.setText(cidString)
        when (Logger.lastState) {
            'A' -> if (bad_cid(Logger.lastCid)) {
                cidText.setTextColor(Color.RED)
            } else if (odd_cid(Logger.lastCid)) {
                cidText.setTextColor(Color.YELLOW)
            } else {
                cidText.setTextColor(Color.WHITE)
            }
            else -> cidText.setTextColor(Color.RED)
        }
        val mnc_string: String
        val mcc_string: String
        if (Logger.lastMccMnc != null &&
                Logger.lastMccMnc.length() === 5) {
            mnc_string = Logger.lastMccMnc.substring(3, 5)
            mcc_string = Logger.lastMccMnc.substring(0, 3)
        } else {
            mnc_string = ""
            mcc_string = ""
        }
        val netlacmncString: String = String.format("%1c%5d %3s",
                Logger.lastNetworkType,
                Logger.lastLac,
                mnc_string)
        val asuString: String = String.format("%dasu", Logger.lastASU)
        netlacmncText.setText(netlacmncString)
        asuText.setText(asuString)
        if (Logger.lastASU === 99) {
            asuText.setTextColor(Color.RED)
        } else {
            asuText.setTextColor(Color.WHITE)
        }
        updateCidHistory(current_time)
    }

    private fun updateGPSDisplay() {
        val current_time: Long = System.currentTimeMillis()
        if (Logger.validFix) {
            val age: Long = (500 + current_time - Logger.lastFixMillis) / 1000
            val latString: String = String.format("%+9.4f", Logger.lastLat)
            val lonString: String = String.format("%+9.4f", Logger.lastLon)
            val accString: String = String.format("%dm", Logger.lastAcc)
            val ageString: String
            if (age < 90) {
                ageString = String.format(" %2ds %03d", age, Logger.lastBearing)
            } else if (age < 90 * 60) {
                ageString = String.format(" %2dm %03d", age / 60, Logger.lastBearing)
            } else {
                ageString = String.format(" %2dh %03d", age / 3600, Logger.lastBearing)
            }
            latText.setText(latString)
            lonText.setText(lonString)
            accText.setText(accString)
            ageText.setText(ageString)
            latText.setTextColor(Color.WHITE)
            lonText.setTextColor(Color.WHITE)
            accText.setTextColor(Color.WHITE)
            ageText.setTextColor(Color.WHITE)
        } else {
            latText.setText("GPS?")
            lonText.setText("GPS?")
            accText.setText("GPS?")
            ageText.setText("GPS?")
            daOffsetText.setText("DA -----")
            latText.setTextColor(Color.RED)
            lonText.setTextColor(Color.RED)
            accText.setTextColor(Color.RED)
            ageText.setTextColor(Color.RED)
        }
        display_position_update()
        val satString: String = String.format("%d/%d/%d/%d",
                Logger.last_fix_sats,
                Logger.last_ephem_sats, Logger.last_alman_sats,
                Logger.last_n_sats)
        satText.setText(satString)
        val countString: String
        // But it's so approximate that it can't be used for accurate purposes anyway.
        if (Logger.validFix) {
            countString = String.format("%dp %dm", Logger.nReadings,
                    Merc28.odn(Logger.lastAlt, Logger.lastLat, Logger.lastLon) as Int)
        } else {
            countString = String.format("%dp GPS?", Logger.nReadings)
        }
        countText.setText(countString)
    }

    // --------------------------------------------------------------------------
    //
    inner class CellUpdateReceiver : BroadcastReceiver() {
        @Override
        fun onReceive(context: Context?, intent: Intent?) {
            // update the map in case the current cell has changed.
            updateCellDisplay()
            tower_update()
            if (TowerLine.is_active()) {
                // The map only depends on the RF behaviour if there has been a handoff
                // when the tower-line is shown
                mMap!!.update_map()
            }
        }
    }

    inner class GPSUpdateReceiver : BroadcastReceiver() {
        @Override
        fun onReceive(context: Context?, intent: Intent?) {
            updateGPSDisplay()
            mMap!!.update_map()
        }
    }

    @Override
    fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Top row
        val toggles: Array<MenuItem?> = Menus2.insert_maps_menu(menu)
        mTileScalingToggle = toggles[0]
        mTowerlineToggle = toggles[1]
        Menus2.insert_download_menu(menu)
        val m_share: MenuItem = menu.add(Menu.NONE, OPTION_SHARE, Menu.NONE, "Share OS ref")
        m_share.setIcon(android.R.drawable.ic_menu_share)
        // Bottom row
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
        mTileScalingToggle.setChecked(mMap!!.is_scaled())
        mTowerlineToggle.setChecked(TowerLine.is_active())
        return true
    }

    private inner class TrailDeleter : Confirm.Callback {
        override fun do_when_confirmed() {
            mMap!!.clear_trail()
        }
    }

    @Override
    fun onOptionsItemSelected(item: MenuItem): Boolean {
        val code: Int = item.getItemId()
        val group: Int = Menus2.group(code)
        val option: Int = Menus2.option(code)
        //Log.i(TAG, "code=" + code + " group=" + group + " option=" + option);
        return if (group == Menus2.OPTION_DOWNLOAD_BASE) {
            Menus2.decode_download_option(option, this, mMap)
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
                    mMap!!.share_grid_ref(this)
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

    companion object {
        private const val PREFS_FILE = "prefs.txt"
        private const val TAG = "MainActivity"

        // --------------------------------------------------------------------------
        //
        private val OPTION_CLEAR_TRAIL: Int = Menus2.OPTION_LOCAL_BASE or 0x1
        private val OPTION_SHARE: Int = Menus2.OPTION_LOCAL_BASE or 0x2
        private val OPTION_LOG_MARKER: Int = Menus2.OPTION_LOCAL_BASE or 0x3
        private val OPTION_EXIT: Int = Menus2.OPTION_LOCAL_BASE or 0x4
    }
}