
package uk.org.rc0.logmygsm
// Deal with fetching map tiles off the net
//
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Handler
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import java.lang.Runnable
import java.net.URI
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.LinkedList
import java.util.Set
import java.util.HashSet
import java.util.Iterator
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal object Downloader {
    private var is_busy = false
    private var mContext: Context? = null
    private var mHandler: Handler? = null
    private const val TAG = "Downloader"
    private const val USER_AGENT_STRING = "LogMyGsm"
    fun init(_app_context: Context?) {
        mContext = _app_context
        mHandler = Handler()
        is_busy = false
    }

    private fun download(the_url: String, the_dest: String, forced: Boolean): Boolean {
        var result: Boolean
        result = false
        try {
            val file = File(the_dest)
            val dir: File = file.getParentFile()
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if ((forced || !file.exists()) &&
                    file.lastModified() < TileStore.get_epoch()) {
                val uri = URI("http", the_url, null)
                val get = HttpGet(uri)
                get.setHeader("User-Agent", USER_AGENT_STRING)
                val client: HttpClient = DefaultHttpClient()
                val response: HttpResponse = client.execute(get)
                val entity: HttpEntity = response.getEntity()
                var out: OutputStream? = null
                try {
                    out = BufferedOutputStream(FileOutputStream(file))
                    entity.writeTo(out)
                    result = true
                } finally {
                    if (out != null) {
                        out.close()
                    }
                }
            }
        } catch (e: Exception) {
            //Log.i(TAG, "download excepted : " + e.getClass().getName() + " : " + e.getMessage());
        }
        return result
    }

    // forced=1 means download even if tile exists (e.g. if you know the map has been updated)
    // forced=0 means download only if the tile is missing
    // in either case, never download more than once during a run of the app
    fun start_multiple_fetch(targets: LinkedList<TileStore.TilePos?>?, forced: Boolean, context: Context?) {
        if (is_busy) {
            Logger.announce(context, "Already downloading")
            return
        }
        val jobs: LinkedList<OneJob?>
        jobs = LinkedList<OneJob>()
        while (targets.size() > 0) {
            val target: TileStore.TilePos = targets.removeFirst()
            val url: String = target.map_source!!.get_download_url(target.zoom, target.x, target.y)
            val dest: String = target.map_source!!.get_tile_path(target.zoom, target.x, target.y)
            if (url != null) {
                jobs.add(OneJob(url, dest, forced))
            }
        }
        is_busy = true
        DownloadThread(jobs).start()
    }

    private fun insert_targets(targets: Set<Target>, z: Int, x0: Int, y0: Int) {
        var dx: Int
        var dy: Int
        dx = -1
        while (dx <= 1) {
            val xx = x0 + dx shr 1 // reverse the extra zoom level
            dy = -1
            while (dy <= 1) {
                val yy = y0 + dy shr 1 // reverse the extra zoom level
                val t = Target(z, xx, yy)
                targets.add(t) // auto-discard targets we already have
                dy++
            }
            dx++
        }
    }

    // Consider the 'waypoints' trail (maybe user's intended route) currently on
    // the map, download the tiles required to cover the whole trail at the
    // current + all outer zoom levels, with a fuzz of +/- 0.5 tiles in all 8
    // directions around
    fun trigger_fetch_route(levels: Int, forced: Boolean, context: Context?, map: Map?) {
        val route_edges: Array<Linkages.Edge?> = Logger.mWaypoints!!.get_edges()
        val targets: Set<Target> = HashSet<Target>()
        var z: Int
        val max_zoom: Int = map!!.current_zoom()
        val source: MapSource = map!!.current_mapsource()
        z = Map.MIN_ZOOM
        while (z <= max_zoom) {
            val z1 = z + 1 // work 1 zoom level deeper to get the 0.5 tile fuzz
            val tile_shift = 28 - z1
            for (j in route_edges.indices) {
                val e: Linkages.Edge? = route_edges[j]
                val m0: Merc28 = e!!.m0
                val m1: Merc28 = e!!.m1
                var x0: Int = m0!!.X shr tile_shift
                var y0: Int = m0!!.Y shr tile_shift
                val x1: Int = m1!!.X shr tile_shift
                val y1: Int = m1!!.Y shr tile_shift

                // Bresenham algorithm (under 'simplification' on Wikipedia)
                val dx: Int = Math.abs(x1 - x0)
                val dy: Int = Math.abs(y1 - y0)
                val sx = if (x0 < x1) 1 else -1
                val sy = if (y0 < y1) 1 else -1
                var err = dx - dy
                while (true) {
                    insert_targets(targets, z, x0, y0)
                    if (x0 == x1 && y0 == y1) break
                    val e2 = 2 * err
                    if (e2 > -dy) {
                        err -= dy
                        x0 += sx
                    } else {
                        err += dx
                        y0 += sy
                    }
                }
            }
            z++
        }
        val tiles: LinkedList<TileStore.TilePos?>
        tiles = LinkedList<TileStore.TilePos>()
        val iter = targets.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            //Log.i(TAG, "Would get z=" + t.zoom + " x=" + t.x + " y=" + t.y);
            tiles.add(TilePos(t.zoom, t.x, t.y, source))
        }
        start_multiple_fetch(tiles, forced, context)
    }

    private class DownloadResponse internal constructor(private val response: Boolean) : Runnable {
        @Override
        fun run() {
            // Crude, needs to be made finer-grained maybe...
            // Toss the whole tile cache then redraw.
            // It's the only way to get the newly fetched tile to be used
            is_busy = false
            TileStore.invalidate()
            val intent = Intent(Logger.UPDATE_GPS)
            mContext.sendBroadcast(intent)
        }
    }

    private class OneJob internal constructor(var url: String, var dest: String, var forced: Boolean)
    private class DownloadThread internal constructor(_jobs: LinkedList<OneJob?>) : Thread() {
        private val jobs: LinkedList<OneJob?>
        @Override
        fun run() {
            var result: Boolean
            result = true // unused
            while (jobs.size() > 0) {
                val job: OneJob = jobs.removeFirst()
                result = download(job.url, job.dest, job.forced)
            }
            mHandler.post(DownloadResponse(result))
        }

        init {
            jobs = _jobs
            setPriority(Thread.MIN_PRIORITY)
        }
    }
}