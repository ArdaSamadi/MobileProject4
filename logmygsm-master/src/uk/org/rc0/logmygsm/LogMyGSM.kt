
package uk.org.rc0.logmygsm

import android.app.Application
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

class LogMyGSM : Application() {
    @Override
    fun onCreate() {
        super.onCreate()
        MapSource.init()
        TileStore.init(this)
        Downloader.init(this)
        TowerLine.init(this)
    }

    @Override
    fun onLowMemory() {
        TileStore.invalidate()
    }
}