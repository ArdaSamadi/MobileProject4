
package uk.org.rc0.logmygsm

import java.lang.Math
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

// Helper class to work with my Merc28 coordinate system.
//
// 'Merc' is the from the 'Mercator-like' projection used by openstreetmap
// which maps -180W, 85.foo N to 0.0,0.0 and +180E, 85.foo S to 1.0,1.0
//
// The '28' is from scaling these fractional coordinates by 2**28 so we
// can work more cheaply with integer arithmetic (e.g. use right shift to get
// the X,Y of the tile containing a location).
class Merc28 {
    var X: Int
    var Y: Int

    constructor(lat: Double, lon: Double) {
        val x: Double
        val yy: Double
        val y: Double
        val XX: Double
        val YY: Double
        x = Math.toRadians(lon)
        yy = Math.toRadians(lat)
        y = Math.log(Math.tan(0.5 * yy + 0.25 * Math.PI))
        XX = 0.5 * (1.0 + x / Math.PI)
        YY = 0.5 * (1.0 - y / Math.PI)
        X = Math.floor(XX * scale)
        Y = Math.floor(YY * scale)
    }

    constructor(_X: Int, _Y: Int) {
        X = _X
        Y = _Y
    }

    constructor(orig: Merc28) {
        X = orig.X
        Y = orig.Y
    }

    fun copy_from(src: Merc28) {
        X = src.X
        Y = src.Y
    }

    fun metres_away(other: Merc28?): Double {
        // This approximation has worst-case error around 0.1% for distances up to
        // at least 200km and latitudes up to 71 deg
        val dx = (X - other!!.X).toDouble() * iscale
        val dy = (Y - other.Y).toDouble() * iscale
        val ay = 0.5 * iscale * (Y + other.Y).toDouble()
        val az = ay - 0.5
        val az2 = az * az
        val az4 = az2 * az2
        val scale_factor_y = J0 + J2 * az2 + J4 * az4
        val scale_factor_total = K0 + K2 * az2 + K4 * az4
        val Dy = dy * scale_factor_y
        val d: Double = Math.sqrt(dx * dx + Dy * Dy)
        return d / scale_factor_total
    }

    fun bearing_to(other: Merc28): Double {
        val zx = (other.X - X).toDouble()
        val zy = (other.Y - Y).toDouble()
        var za: Double = 180 / Math.PI * Math.atan2(zx, -zy)
        if (za < 0.0) {
            za += 360.0
        }
        return za
    }

    fun to_lon(): Double {
        val xwrap = (X and shift_mask.toDouble().toInt()).toDouble()
        val xx = iscale * xwrap
        return 360.0 * (xx - 0.5)
    }

    // See
    // http://rc0rc0.wordpress.com/2013/11/25/approximating-the-gudermannian-function/
    fun to_lat(): Double {
        val y = iscale * Y.toDouble()
        val z = 1.0 - 2.0 * y
        val z2 = z * z
        val z4 = z2 * z2
        val t: Double
        val b0: Double
        val b4: Double
        val b: Double
        val t2: Double
        b4 = Q4 + Q6 * z2
        b0 = Q0 + Q2 * z2
        t2 = P3 + P5 * z2
        b = b0 + b4 * z4
        t = P1 + t2 * z2
        return z * t / b
    }

    private fun inner_grid_ref_5m(fmt: String): String {
        val x: Double
        val y: Double
        x = X.toDouble() / scale
        y = Y.toDouble() / scale
        val alpha: Double
        val beta: Double
        val t90: Double
        val t91: Double
        val t92: Double
        val t93: Double
        val t94: Double
        val t95: Double
        val t96: Double
        val t97: Double
        val t98: Double
        val t99: Double
        val t100: Double
        val t101: Double
        val t102: Double
        val t103: Double
        val t104: Double
        val t105: Double
        val t106: Double
        val t107: Double
        val t108: Double
        val t109: Double
        val alpha2: Double
        val beta2: Double
        val beta4: Double
        val E: Double
        val N: Double
        alpha = 61.000 * (x - 0.4944400930)
        beta = 36.000 * (y - 0.3126638550)
        if (alpha < -1.0 || alpha > 1.0 || beta < -1.0 || beta > 1.0) {
            return "NOT IN UK"
        }
        alpha2 = alpha * alpha
        beta2 = beta * beta
        beta4 = beta2 * beta2
        t90 = 400001.47 + -17.07 * beta
        t91 = 370523.38 + 53326.92 * beta
        t92 = 2025.68 + -241.27 * beta
        t93 = t91 + t92 * beta2
        t94 = t93 + -41.77 * beta4
        t95 = t90 + t94 * alpha
        t96 = -11.21 * beta
        t97 = t96 + 14.84 * beta2
        t98 = -237.68 + 82.89 * beta
        t99 = t98 + 41.21 * beta2
        t100 = t97 + t99 * alpha
        E = t95 + t100 * alpha2
        t101 = 649998.33 + -13.90 * alpha
        t102 = t101 + 15782.38 * alpha2
        t103 = -626496.42 + 1220.67 * alpha2
        t104 = t102 + t103 * beta
        t105 = -44898.11 + 10.01 * alpha
        t106 = t105 + -217.21 * alpha2
        t107 = -1088.27 + -49.59 * alpha2
        t108 = t106 + t107 * beta
        t109 = t104 + t108 * beta2
        N = t109 + 107.47 * beta4
        if (E < 0.0 || E >= 700000.0 || N < 0.0 || N >= 1300000) {
            return "NOT IN UK"
        }
        val e = (0.5 + E).toInt() / 10
        val n = (0.5 + N).toInt() / 10
        val e0 = e / 10000
        val e1 = e % 10000
        val n0 = n / 10000
        val n1 = n % 10000
        val c0 = letters0[3 * (e0 / 5) + n0 / 5]
        val c1 = letters1[5 * (e0 % 5) + n0 % 5]
        return String.format(fmt, c0, c1, e1, n1)
    }

    fun grid_ref_5m(): String {
        return inner_grid_ref_5m("%1c%1c %04d %04d")
    }

    fun grid_ref_5m_nosp(): String {
        return inner_grid_ref_5m("%1c%1c%04d%04d")
    }

    companion object {
        const val shift = 28
        const val shift_mask = (1 shl shift) - 1
        const val scale = (1 shl shift).toDouble()
        const val iscale = 1.0 / scale
        const val EARTH_RADIUS_IN_METRES = 6378137.0
        val K: Double = 2.0 * Math.PI * EARTH_RADIUS_IN_METRES
        val K0 = 1.0 / K
        val K2 = 19.42975297 / K
        val K4 = 74.22319781 / K
        const val J0 = 0.99330562
        const val J2 = 0.18663111
        const val J4 = -1.45510549
        const val P1 = 179.9989063857
        const val P3 = 507.2276380744
        const val P5 = 176.2675623673
        const val Q0 = 1.0000000000
        const val Q2 = 4.4623636863
        const val Q4 = 4.2727924855
        const val Q6 = 0.4175728442

        // double to_lat_old() {
        //   double t = (1.0 - 2.0*iscale*(double)Y) * Math.PI;
        //   double tt = (2.0 * Math.atan(Math.exp(t))) - 0.5*Math.PI;
        //   return Math.toDegrees(tt);
        // }
        // See
        // http://issuu.com/rc0rc0/docs/wmen_paper
        // which explains the approximations that follow
        // Convert GPS altitude into estimated height above mean sea level
        fun odn(alt: Double, lat: Double, lon: Double): Double {
            val P: Double
            val Q: Double
            P = 2.0 / 9.0 * (lat - 54.5)
            Q = 0.25 * (lon + 2.0)
            return alt - 50.1 + 6.1 * Q - 1.1 * P - 1.5 * P * Q
        }

        // ------------------------------------------------------------------
        // Deal with grid references
        val letters1: CharArray = "VQLFAWRMGBXSNHCYTOJDZUPKE".toCharArray()
        val letters0: CharArray = "SNHTOJ".toCharArray()
    }
}