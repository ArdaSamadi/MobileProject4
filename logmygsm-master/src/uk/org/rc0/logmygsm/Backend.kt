
package uk.org.rc0.logmygsm

import android.text.format.DateFormat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class Backend(prefix: String, netcode: String?, the_service: Logger?) {
    private var file: File? = null
    private var writer: FileWriter? = null
    private val mService: Logger?
    fun write(data: String?) {
        if (writer != null) {
            try {
                writer.append(data)
            } catch (e: IOException) {
            }
        }
    }

    fun close() {
        if (writer != null) {
            if (mService != null) {
                mService.announce("Closing logfile")
            }
            try {
                writer.flush()
                writer.close()
            } catch (e: IOException) {
            }
        }
        writer = null
    }

    // 'netcode' allows SIMs to be switched in and out of the handset whilst
    // keeping the logfiles from different operators' SIMs in separate directories
    init {
        val basePath = "/sdcard"
        val ourDir = "LogMyGsm/logs/$netcode"
        val cs: CharSequence = DateFormat.format("yyyyMMdd-kkmmss", System.currentTimeMillis())
        val timedFileName = "$prefix$cs.log"
        val fullPath = "$basePath/$ourDir/$timedFileName"
        mService = the_service
        try {
            val root = File(basePath, ourDir)
            if (!root.exists()) {
                root.mkdirs()
            }
            file = File(root, timedFileName)
            writer = FileWriter(file)
            if (mService != null) {
                mService.announce("Opened logfile")
            }
        } catch (e: IOException) {
            file = null
            writer = null
        }
        if (writer == null) {
            if (mService != null) {
                mService.announce("COULD NOT LOG TO $fullPath")
            }
        }
    }
}