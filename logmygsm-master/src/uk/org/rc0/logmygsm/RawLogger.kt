
package uk.org.rc0.logmygsm

import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class RawLogger(private val enabled: Boolean) {
    private var log: Backend? = null
    private fun write(tag: String, data: String) {
        if (log == null) {
            log = Backend("raw_", "", null)
        }
        val now: Long = System.currentTimeMillis()
        val seconds = now / 1000
        val millis = now % 1000
        val all: String = String.format("%10d.%03d %2s %s\n",
                seconds, millis,
                tag, data)
        log.write(all)
    }

    fun close() {
        if (log != null) {
            log.close()
        }
    }

    fun log_asu() {
        if (enabled) {
            val data: String = String.format("%d", Logger.lastASU)
            write("AS", data)
        }
    }

    fun log_cell() {
        if (enabled) {
            val data: String = String.format("%10d %10d %s",
                    Logger.lastCid, Logger.lastLac,
                    Logger.lastMccMnc)
            write("CL", data)
        }
    }

    fun log_service_state() {
        if (enabled) {
            val data: String = String.format("%c", Logger.lastState)
            write("ST", data)
        }
    }

    fun log_network_type() {
        if (enabled) {
            val data: String = String.format("%c %d", Logger.lastNetworkType, Logger.lastNetworkTypeRaw)
            write("NT", data)
        }
    }

    fun log_bad_location() {
        if (enabled) {
            write("LB", "-- bad --")
        }
    }

    fun log_raw_location() {
        if (enabled) {
            val data: String = String.format("%12.7f %12.7f %3d",
                    Logger.lastLat, Logger.lastLon, Logger.lastAcc)
            write("LC", data)
        }
    }

    fun log_location_disabled() {
        if (enabled) {
            write("LD", "-- disabled --")
        }
    }

    fun log_location_enabled() {
        if (enabled) {
            write("LE", "-- enabled --")
        }
    }

    fun log_location_status() {
        if (enabled) {
            // This seems to log every second - very wasteful!
            //String data = String.format("%d %d %d %d",
            //    last_n_sats, last_fix_sats, last_ephem_sats, last_alman_sats);
            //writeRaw("LS", data);
        }
    }
}