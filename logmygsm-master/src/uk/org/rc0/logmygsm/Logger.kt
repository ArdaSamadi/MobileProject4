
package uk.org.rc0.logmygsm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.IBinder
import android.location.GpsStatus
import android.location.GpsSatellite
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.CellLocation
import android.telephony.SignalStrength
import android.telephony.ServiceState
import android.telephony.gsm.GsmCellLocation
import android.util.Log
import android.widget.Toast
import java.lang.Iterable
import java.util.Iterator
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class Logger : Service() {
    private var myQuitReceiver: QuitReceiver? = null
    private var myTelephonyManager: TelephonyManager? = null
    private var myLocationManager: LocationManager? = null
    private var myNotificationManager: NotificationManager? = null
    private var myNotification: Notification? = null
    private val myNotificationRef = 1

    // mainlog is null until we've got the first GPS fix - so we don't open the
    // logfile needlessly.
    private var mainlog: Backend? = null
    private var rawlog: RawLogger? = null
    private var power_manager: PowerManager? = null
    private var wake_lock: PowerManager.WakeLock? = null

    // --- CID history
    inner class RecentCID {
        var cid: Int
        var lac = 0
        var network_type = 0.toChar()
        var state = 0.toChar()
        var dbm = 0
        var handoff = 0

        // Time this CID was last encountered
        var lastMillis: Long = 0

        init {
            cid = -1
        }
    }

    // This is only called once for the service lifetime
    @Override
    fun onCreate() {
        stop_tracing = false
        init_state()
        init_recent_cids()
        rawlog = RawLogger(false) // 'true' to re-enable raw logs for debug
        mTrail = Trail(this)
        mOdometer = Odometer()
        mWaypoints = Waypoints()
        mLandmarks = Landmarks()
        bookmark_count = 1
        bookmark_next_time = false
        myProvider = LocationManager.GPS_PROVIDER
        myNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        val srvcName: String = Context.TELEPHONY_SERVICE
        myTelephonyManager = getSystemService(srvcName) as TelephonyManager?
        simOperator = myTelephonyManager.getSimOperator()
        // Log.i("Logger", "SIM operator = " + simOperator);
        val context: String = Context.LOCATION_SERVICE
        myLocationManager = getSystemService(context) as LocationManager?
        power_manager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        wake_lock = power_manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "LogMyGSM")
        startListening()
    }

    private fun init_recent_cids() {
        recent_cids = arrayOfNulls(MAX_RECENT)
        for (i in 0 until MAX_RECENT) {
            recent_cids!![i] = RecentCID()
        }
    }

    private fun init_state() {
        nReadings = 0
        nHandoffs = 0
        lastCid = 0
        lastLac = 0
        lastdBm = 0
        lastNetworkType = '?'
        validFix = false
    }

    // We don't care if this gets called multiple times as it doesn't do anything.
    // We don't need to accept any 'args' from the outside.
    @Override
    fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    @Override
    fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @Override
    fun onDestroy() {
        stopListening()
        mTrail!!.save_state_to_file()
        mWaypoints!!.save_state_to_file()
        mLandmarks!!.save_state_to_file()
    }

    // --------------------------------------------------------------------------------
    private fun startNotification() {
        val icon: Int = R.drawable.notification
        val notifyText = "Logger running"
        val `when`: Long = System.currentTimeMillis()
        myNotification = Notification(icon, notifyText, `when`)
        myNotification.flags = myNotification.flags or Notification.FLAG_ONGOING_EVENT
        updateNotification()
    }

    private fun stopNotification() {
        if (myNotificationManager != null) {
            myNotificationManager.cancel(myNotificationRef)
        }
    }

    private fun updateNotification() {
        val context: Context = getApplicationContext()
        val expandedText: String = String.format("%c%d, %dasu/%c %dm %d/%d/%d",
                lastNetworkType, lastCid,
                lastASU, lastState,
                lastAcc,
                last_fix_sats, last_ephem_sats, last_alman_sats)
        val expandedTitle: String = String.format("GSM Logger running (%d)", nReadings)
        val intent = Intent(this, MainActivity::class.java)

        // The next line is to stop Android from creating multiple activities - it
        // jus thas to go back to the one it was using before
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val launchIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
        myNotification.setLatestEventInfo(context, expandedTitle, expandedText, launchIntent)
        if (myNotificationManager != null) {
            startForeground(myNotificationRef, myNotification)
            // myNotificationManager.notify(myNotificationRef, myNotification);
        }
    }

    // --------------------------------------------------------------------------------
    private fun logCellHistory() {
        var pos = -1
        var match: RecentCID? = null
        for (i in 0 until MAX_RECENT) {
            // should LAC be checked too?
            if (recent_cids!![i]!!.cid == lastCid) {
                pos = i
                match = recent_cids!![i]
                break
            }
        }
        if (pos == -1) {
            pos = MAX_RECENT - 1
        }
        if (pos > 0) {
            for (i in pos downTo 1) {
                recent_cids!![i] = recent_cids!![i - 1]
            }
        }
        // If pos==0 we just overwrite the newest record anyway
        if (match != null) {
            recent_cids!![0] = match
        } else {
            recent_cids!![0] = RecentCID()
            recent_cids!![0]!!.cid = lastCid
            recent_cids!![0]!!.lac = lastLac
            recent_cids!![0]!!.network_type = lastNetworkType
        }
        recent_cids!![0]!!.state = lastState
        recent_cids!![0]!!.dbm = lastdBm
        recent_cids!![0]!!.handoff = nHandoffs
        recent_cids!![0]!!.lastMillis = System.currentTimeMillis()
    }

    private fun startListening() {
        startNotification()
        myTelephonyManager.listen(myPhoneStateListener,
                PhoneStateListener.LISTEN_CELL_LOCATION or
                        PhoneStateListener.LISTEN_SERVICE_STATE or
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
        // Don't use continuous updates.  You keep getting callbacks, even if
        // you're stationary.
        try {
            myLocationManager.requestLocationUpdates(myProvider,
                    GPS_MIN_MILLISECONDS,
                    GPS_MIN_DISTANCE,
                    myLocationListener)
            myLocationManager.addGpsStatusListener(gpsListener)
        } catch (e: Exception) {
        }
        val quit_filter = IntentFilter(QUIT_LOGGER)
        myQuitReceiver = QuitReceiver()
        registerReceiver(myQuitReceiver, quit_filter)
        wake_lock.acquire()
    }

    // --------------------------------------------------------------------------------
    private fun stopListening() {
        wake_lock.release()
        myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        try {
            myLocationManager.removeGpsStatusListener(gpsListener)
            myLocationManager.removeUpdates(myLocationListener)
        } catch (e: Exception) {
        }
        stopNotification()
        if (mainlog != null) {
            mainlog.close()
        }
        if (rawlog != null) {
            rawlog.close()
        }
        unregisterReceiver(myQuitReceiver)
    }

    private fun check_for_stale_GPS() {
        // Idea : if we get a callback from the cell radio, we check how long it is
        // since we had a GPS update.  If we were doing a significant speed, this
        // interval should not be too long.  This lets us catch the case where the
        // GPS has "stuck", which happens sometimes...
        //
        val age: Long = System.currentTimeMillis() - lastFixMillis
        if (lastSpeed > SPEED_THRESHOLD && age > 2000) {
            validFix = false
            val intent = Intent(UPDATE_GPS)
            sendBroadcast(intent)
        }
    }

    // --------------------------------------------------------------------------------
    private fun updateUIGPS() {
        updateNotification()
        val intent = Intent(UPDATE_GPS)
        sendBroadcast(intent)
        if (stop_tracing) {
            stopSelf()
        }
    }

    private fun updateUICell() {
        check_for_stale_GPS()
        updateNotification()
        val intent = Intent(UPDATE_CELL)
        sendBroadcast(intent)
        if (stop_tracing) {
            stopSelf()
        }
    }

    internal inner class QuitReceiver : BroadcastReceiver() {
        @Override
        fun onReceive(context: Context?, intent: Intent?) {
            mOdometer!!.reset()
            stopSelf()
        }
    }

    // --------------------------------------------------------------------------------
    private fun logToFile() {
        if (mainlog == null) {
            mainlog = Backend("", simOperator, this)
        }
        if (bookmark_next_time) {
            mainlog.write("""
    ## MARKER $bookmark_count

    """.trimIndent())
            bookmark_next_time = false
            ++bookmark_count
        }
        ++nReadings
        val data: String = String.format("%12.7f %12.7f %3d %c %c %10d %10d %3d %s %.1f %d\n",
                lastLat, lastLon, lastAcc,
                lastState,
                lastNetworkType, lastCid, lastLac,
                lastdBm,
                lastMccMnc,
                lastAlt,
                (lastTime / 1000).toInt())
        mainlog.write(data)
    }

    // --------------------------------------------------------------------------------
    private val myPhoneStateListener: PhoneStateListener = object : PhoneStateListener() {
        fun onCellLocationChanged(location: CellLocation?) {
            val newCid: Int
            super.onCellLocationChanged(location)
            if (location == null) {
                newCid = 0
                lastLac = 0
            } else {
                val gsmLocation: GsmCellLocation = location as GsmCellLocation
                newCid = gsmLocation.getCid()
                lastLac = gsmLocation.getLac()
            }
            if (newCid != lastCid) {
                ++nHandoffs
            }
            lastCid = newCid
            lastMccMnc = String(myTelephonyManager.getNetworkOperator())
            lastOperator = String(myTelephonyManager.getNetworkOperatorName())
            lastSimOperator = String(myTelephonyManager.getSimOperatorName())
            logCellHistory()
            rawlog!!.log_cell()
            updateUICell()
        }

        fun onSignalStrengthsChanged(strength: SignalStrength) {
            val asu: Int
            super.onSignalStrengthsChanged(strength)
            asu = strength.getGsmSignalStrength()
            lastASU = asu
            rawlog!!.log_asu()
            if (asu == 99) {
                lastdBm = 0
            } else {
                lastdBm = -113 + 2 * asu
            }
            lastBer = strength.getGsmBitErrorRate()
            logCellHistory()
            updateUICell()
        }

        fun onServiceStateChanged(newState: ServiceState) {
            super.onServiceStateChanged(newState)
            when (newState.getState()) {
                ServiceState.STATE_EMERGENCY_ONLY -> lastState = 'E'
                ServiceState.STATE_IN_SERVICE -> lastState = 'A'
                ServiceState.STATE_OUT_OF_SERVICE -> lastState = 'X'
                ServiceState.STATE_POWER_OFF -> lastState = 'O'
                else -> lastState = '?'
            }
            rawlog!!.log_service_state()
            updateUICell()
        }

        private fun handle_network_type(network_type: Int) {
            when (network_type) {
                TelephonyManager.NETWORK_TYPE_GPRS -> {
                    lastNetworkType = 'G'
                    lastNetworkTypeLong = "GPRS"
                }
                TelephonyManager.NETWORK_TYPE_EDGE -> {
                    lastNetworkType = 'E'
                    lastNetworkTypeLong = "EDGE"
                }
                TelephonyManager.NETWORK_TYPE_UMTS -> {
                    lastNetworkType = 'U'
                    lastNetworkTypeLong = "UMTS"
                }
                TelephonyManager.NETWORK_TYPE_HSDPA -> {
                    lastNetworkType = 'H'
                    lastNetworkTypeLong = "HSDPA"
                }
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> {
                    lastNetworkType = '-'
                    lastNetworkTypeLong = "----"
                }
                else -> {
                    lastNetworkType = '?'
                    lastNetworkTypeLong = "????"
                }
            }
            lastNetworkTypeRaw = network_type
            rawlog!!.log_network_type()
            updateUICell()
        }

        fun onDataConnectionStateChanged(state: Int, network_type: Int) {
            super.onDataConnectionStateChanged(state, network_type)
            handle_network_type(network_type)
        }
    }

    // --------------------------------------------------------------------------------
    // Ensure we re-read the cell information every time we log a GPS update, on
    // the offchance that we've lost a callback along the way and we're not
    // changing cells very much
    private fun sample_cell_info() {
        val loc: GsmCellLocation = myTelephonyManager.getCellLocation() as GsmCellLocation
        if (loc != null) {
            lastCid = loc.getCid()
            lastLac = loc.getLac()
        }
    }

    // --------------------------------------------------------------------------------
    private val myLocationListener: LocationListener = object : LocationListener() {
        fun onLocationChanged(location: Location?) {
            if (location == null) {
                validFix = false
                rawlog!!.log_bad_location()
            } else {
                validFix = true
                lastLat = location.getLatitude()
                lastLon = location.getLongitude()
                lastAlt = location.getAltitude()
                lastTime = location.getTime()
                lastBearing = location.getBearing()
                lastSpeed = location.getSpeed()
                if (location.hasAccuracy()) {
                    lastAcc = (0.5 + location.getAccuracy()) as Int
                } else {
                    lastAcc = 0
                }
                // lastFixMillis = location.getTime();
                lastFixMillis = System.currentTimeMillis()

                // sample_cell_info();
                logToFile()
                rawlog!!.log_raw_location()
                val m28_point = Merc28(lastLat, lastLon)
                mTrail!!.add_point(m28_point)
                mOdometer!!.append(m28_point)
            }
            updateUIGPS()
        }

        fun onProviderDisabled(provider: String?) {
            validFix = false
            rawlog!!.log_location_disabled()
            updateUIGPS()
        }

        fun onProviderEnabled(provider: String?) {
            rawlog!!.log_location_enabled()
        }

        fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            rawlog!!.log_location_status()
        }
    }

    // --------------------------------------------------------------------------------
    private val gpsListener: GpsStatus.Listener = object : Listener() {
        fun onGpsStatusChanged(event: Int) {
            val gpsStatus: GpsStatus = myLocationManager.getGpsStatus(null)
            when (event) {
                GpsStatus.GPS_EVENT_FIRST_FIX -> {
                }
                GpsStatus.GPS_EVENT_SATELLITE_STATUS -> {
                    val satellites: Iterable<GpsSatellite> = gpsStatus.getSatellites()
                    val sati: Iterator<GpsSatellite> = satellites.iterator()
                    last_n_sats = 0
                    last_fix_sats = 0
                    last_ephem_sats = 0
                    last_alman_sats = 0
                    while (sati.hasNext()) {
                        val sat: GpsSatellite = sati.next()
                        ++last_n_sats
                        if (sat.usedInFix()) {
                            ++last_fix_sats
                        } else if (sat.hasEphemeris()) {
                            ++last_ephem_sats
                        } else if (sat.hasAlmanac()) {
                            ++last_alman_sats
                        }
                    }
                    rawlog!!.log_location_status()
                }
                GpsStatus.GPS_EVENT_STARTED -> {
                }
                GpsStatus.GPS_EVENT_STOPPED -> {
                }
            }
        }
    }

    // --------------------------------------------------------------------------------
    fun announce(text: String?) {
        val context: Context = getApplicationContext()
        val duration: Int = Toast.LENGTH_SHORT
        val toast: Toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    companion object {
        // Flag that's set by the UI to tell us to cut the power when we next get
        // called back by the framework.
        var stop_tracing = false
        var mTrail: Trail? = null
        var mOdometer: Odometer? = null
        var mWaypoints: Waypoints? = null
        var mLandmarks: Landmarks? = null

        // -----------------
        // Variables shared with the Activity
        // -----------------
        //
        const val UPDATE_CELL = "LogMyGSM_Update_Cell"
        const val UPDATE_GPS = "LogMyGSM_Update_GPS"
        const val QUIT_LOGGER = "LogMyGSM_Quit_Logger"

        // --- Telephony
        var simOperator: String? = null
        var lastNetworkType = 0.toChar()
        var lastNetworkTypeLong: String? = null
        var lastNetworkTypeRaw = 0
        var lastState = 0.toChar()
        var lastCid = 0
        var lastLac = 0
        var lastMccMnc: String? = null
        var lastOperator: String? = null
        var lastSimOperator: String? = null
        var lastdBm = 0
        var lastASU = 0
        var lastBer = 0

        // --- GPS
        var validFix = false
        var myProvider: String? = null
        var nReadings = 0
        var nHandoffs = 0
        var lastLat = 0.0
        var lastLon = 0.0
        var lastAlt = 0.0
        var lastTime: Long = 0
        var lastAcc = 0
        var lastBearing = 0
        var lastSpeed = 0f
        var lastFixMillis: Long = 0

        // --- GPS fix info
        var last_n_sats = 0
        var last_fix_sats = 0
        var last_ephem_sats = 0
        var last_alman_sats = 0

        // --- Bookmarks
        private var bookmark_count = 0
        private var bookmark_next_time = false
        const val MAX_RECENT = 32
        var recent_cids: Array<RecentCID?>?

        // --------------------------------------------------------------------------------
        private const val GPS_MIN_MILLISECONDS: Long = 500
        private const val GPS_MIN_DISTANCE = 1.0f

        // --------------------------------------------------------------------------------
        // 15mph in m/s
        private const val SPEED_THRESHOLD = 6.7f

        // --------------------------------------------------------------------------------
        fun get_metres_covered(): Double {
            return if (mOdometer != null) {
                mOdometer.get_metres_covered()
            } else {
                0.0
            }
        }

        fun do_bookmark(context: Context?) {
            bookmark_next_time = true
            announce(context, "Marker " + bookmark_count)
        }

        fun announce(context: Context?, text: String?) {
            val duration: Int = Toast.LENGTH_SHORT
            val toast: Toast = Toast.makeText(context, text, duration)
            toast.show()
        }
    }
}