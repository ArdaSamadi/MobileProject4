
package uk.org.rc0.logmygsm

import android.util.FloatMath
import android.util.Log
import java.util.Arrays
import java.util.ArrayList
import java.util.BitSet
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.LinkedList
import java.util.Set
import uk.org.rc0.logmygsm.Waypoints.Routing
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class Linkages(_points: ArrayList<Waypoints.Point>?,
                        _destination: Waypoints.Point?,
                        _struts: ArrayList<Waypoints.Strut>?) {
    // Turns a set of points into a set of "linkages" (in the mechanical sense)
    //
    // Coherency (if the set of points changes) must be managed by the caller
    // ---------------------------
    class Edge(_m0: Merc28?, _m1: Merc28?) {
        var m0: Merc28?
        var m1: Merc28?

        init {
            m0 = _m0
            m1 = _m1
        }
    }

    // ---------------------------
    private class WorkingEdge(var i0: Int, var i1: Int, points: Array<Merc28?>, var d: Float) : Edge(points[i0], points[i1]) {
        var alive = true
    }

    // ---------------------------
    // Cached result for later reads
    private var edges: Array<Edge?>
    private var full_edges: Array<WorkingEdge?>
    private var points: Array<Merc28?>
    private var distances: FloatArray

    // ---------------------------
    private fun compute_pruned(np: Int, fussy: Set<WorkingEdge>) {
        // remove edges from the overly-'fussy' initial set.  An edge is surplus to
        // requirements <=> the nodes at both end have >=3 neighbours, and there is
        // another path from one to the other through the network even if this edge
        // is culled.
        //
        val n: Int = fussy.size()
        val n_neigh = IntArray(np)
        val eqclass = IntArray(np)
        val working: Array<WorkingEdge> = fussy.toArray(arrayOfNulls<WorkingEdge>(0))
        Arrays.sort(working, edge_comparator)
        for (i in 0 until np) n_neigh[i] = 0
        for (i in working.indices) {
            ++n_neigh[working[i].i0]
            ++n_neigh[working[i].i1]
        }
        var n_alive = working.size
        for (candidate in working.indices) {
            val i0 = working[candidate].i0
            val i1 = working[candidate].i1
            if (n_neigh[i0] >= 3 && n_neigh[i1] >= 3) {
                for (i in 0 until np) eqclass[i] = i
                var active = false
                do {
                    active = false
                    for (e in working.indices) {
                        if (e == candidate) continue
                        if (working[e].alive) {
                            val e0 = working[e].i0
                            val e1 = working[e].i1
                            // Double dereference to get log-order iteration count (I think)
                            // even if the edges are sorted in the worst possible order along
                            // a chain
                            val h0 = eqclass[e0]
                            val h1 = eqclass[e1]
                            val c0 = eqclass[h0]
                            val c1 = eqclass[h1]
                            val cc = if (c0 < c1) c0 else c1
                            if (eqclass[e0] != cc) {
                                eqclass[e0] = cc
                                active = true
                            }
                            if (eqclass[e1] != cc) {
                                eqclass[e1] = cc
                                active = true
                            }
                        }
                    }
                } while (active && eqclass[i0] != eqclass[i1])
                if (eqclass[i1] == eqclass[i0]) {
                    working[candidate].alive = false
                    --n_neigh[i0]
                    --n_neigh[i1]
                    --n_alive
                }
            }
        }

        //Log.i(TAG, "Start with " + working.length + ", " + n_alive + " remain");
        edges = arrayOfNulls(n_alive)
        full_edges = arrayOfNulls(n_alive)
        var i: Int
        var j: Int
        i = 0.also { j = it }
        while (i < working.size) {
            if (working[i].alive) {
                edges[j] = working[i]
                full_edges[j] = working[i]
                j++
            }
            i++
        }
    }

    // ---------------------------
    private fun do_meshing(_points: ArrayList<Waypoints.Point>?,
                           _struts: ArrayList<Waypoints.Strut>?) {
        // defensive copy of the points fed in
        points = arrayOfNulls<Merc28>(_points.size())
        for (i in 0 until _points.size()) {
            points[i] = Merc28(_points.get(i))
        }
        val mesh = compute_mesh(points, _struts)
        compute_pruned(points.size, mesh) // sets up 'edges' and 'full_edges'
    }

    // ---------------------------
    fun nearest_edge(pos: Merc28): IntArray? {
        var best: WorkingEdge? = null
        // The distance to the side of the edge
        var best_distance = -1.0
        val n = full_edges.size
        for (i in 0 until n) {
            val e = full_edges[i]
            val dx = (e.m1.X - e.m0.X) as Float
            val dy = (e.m1.Y - e.m0.Y) as Float
            val d: Float = FloatMath.sqrt(dx * dx + dy * dy)
            if (d == 0f) {
                return null
            }
            val ux = dx / d
            val uy = dy / d
            val vx = -uy
            val proj_along = (pos.X - e.m0.X) as Float * ux + (pos.Y - e.m0.Y) as Float * uy
            if (proj_along >= 0 && proj_along <= d) {
                var proj_to_side = (pos.X - e.m0.X) as Float * vx + (pos.Y - e.m0.Y) as Float * ux
                proj_to_side = Math.abs(proj_to_side)
                if (proj_to_side < 0.25f * d) { // safeguard to avoid picking edges miles away
                    if (best == null || proj_to_side < best_distance) {
                        best = e
                        best_distance = proj_to_side.toDouble()
                    }
                }
            }
        }
        if (best != null) {
            val result = IntArray(2)
            result[0] = best.i0
            result[1] = best.i1
            return result
        }
        return null
    }

    // ---------------------------
    private class Endpoint {
        var index // point index number
                = 0
        var siblings // the other endpoints at the same physical point
                : Array<Endpoint?>
        var peer // the other end of the line segment
                : Endpoint? = null
        var via // the path to the other end
                : Segment? = null
        var downstream: BitSet? = null
        var distance_ok // valid distance coming into this endpoint from ref point
                = false
        var distance // distance to ref point via this endpoint
                = 0f
        var pending // already on todo queue
                = false

        fun sibling_distance(): Float {
            var d = 0.0f
            var ok = false
            for (i in siblings.indices) {
                val sib = siblings[i]
                if (sib!!.distance_ok) {
                    if (!ok || sib.distance < d) {
                        d = sib.distance
                        ok = true
                    }
                }
            }
            // This should not get called unless one of the siblings has a defined distance!
            return d
        }

        fun nearest_sibling(): Int {
            var best_index = -1
            var d = 0.0f
            var ok = false
            for (i in siblings.indices) {
                val sib = siblings[i]
                if (sib!!.distance_ok) {
                    if (!ok || sib.distance < d) {
                        d = sib.distance
                        best_index = i
                        ok = true
                    }
                }
            }
            // This should not get called unless one of the siblings has a defined distance!
            return best_index
        }

        fun take_new_distance(d: Float, via: Segment?, _downstream: BitSet?): Boolean {
            return if (!distance_ok || d < distance) {
                distance_ok = true
                distance = d
                downstream = BitSet()
                downstream.or(_downstream)
                downstream.set(via!!.index, true)
                true
            } else {
                false
            }
        }

        fun is_destination(): Boolean {
            return if (distance_ok && distance == 0.0f) true else false
        }
    }

    private class Segment internal constructor(var e0: Endpoint?, var e1: Endpoint?, var index: Int) {
        var distance = 0f
    }

    // ---------------------------
    private var endpoints: Array<Array<Endpoint?>>?
    private var segments: Array<Segment?>

    // ---------------------------
    private fun do_distances(_destination: Int) {
        val n_ep: IntArray
        val np = points.size
        val ne = edges.size
        n_ep = IntArray(np)
        for (i in 0 until np) {
            n_ep[i] = 0
        }
        for (i in 0 until ne) {
            ++n_ep[full_edges[i]!!.i0]
            ++n_ep[full_edges[i]!!.i1]
        }

        // Now build siblings
        endpoints = arrayOfNulls(np)
        for (i in 0 until np) {
            endpoints.get(i) = arrayOfNulls(n_ep[i])
            for (j in 0 until n_ep[i]) {
                endpoints.get(i)[j] = Endpoint()
                val e = endpoints.get(i)[j]
                e!!.index = i
                e.siblings = arrayOfNulls(n_ep[i] - 1)
                e.distance_ok = false
                e.pending = false
            }
            for (j in 0 until n_ep[i]) {
                var k: Int
                var m: Int
                k = 0.also { m = it }
                while (k < n_ep[i]) {
                    if (j != k) {
                        endpoints.get(i)[j]!!.siblings[m++] = endpoints.get(i)[k]
                    }
                    k++
                }
            }
        }
        val used_ep = IntArray(np)
        for (i in 0 until np) {
            used_ep[i] = 0
        }
        segments = arrayOfNulls(ne)
        for (i in 0 until ne) {
            val i0 = full_edges[i]!!.i0
            val i1 = full_edges[i]!!.i1
            val u0 = used_ep[i0]++
            val u1 = used_ep[i1]++
            val e0 = endpoints.get(i0)[u0]
            val e1 = endpoints.get(i1)[u1]
            segments[i] = Segment(e0, e1, i)
            val s = segments[i]
            e0!!.peer = e1
            e1!!.peer = e0
            e1.via = s
            e0.via = e1.via
            s!!.distance = full_edges[i]!!.d
        }
        val todo: LinkedList<Endpoint> = LinkedList<Endpoint>()
        for (i in 0 until n_ep[_destination]) {
            val e = endpoints.get(_destination)[i]
            e!!.distance = 0.0f
            e.distance_ok = true
            e.downstream = BitSet()
            e.pending = true
            todo.add(e)
        }
        var count = 0

        // Propagate distances through the mesh
        while (todo.size() > 0) {
            count++
            if (count > 16384) {
                // safety net
                break
            }
            val e: Endpoint = todo.removeFirst()
            e.pending = false
            val e2 = e.peer
            var expand_e2 = false
            val best_index = e.nearest_sibling()
            if (best_index >= 0) {
                val best_sibling = e.siblings[best_index]
                val distance = best_sibling!!.distance + e.via!!.distance
                if (!best_sibling.downstream.get(e.via!!.index) &&
                        e2!!.take_new_distance(distance, e.via, best_sibling.downstream)) {
                    expand_e2 = true
                }
            } else {
                val distance = e.via!!.distance
                if (e2!!.take_new_distance(distance, e.via, BitSet())) {
                    expand_e2 = true
                }
            }
            if (expand_e2) {
                for (i in e2!!.siblings.indices) {
                    val ee = e2.siblings[i]
                    if (!ee!!.pending) {
                        todo.add(ee)
                        ee.pending = true
                    }
                }
            }
        }
        distances = FloatArray(np)
        for (i in 0 until np) {
            distances[i] = minimum_endpoint_distance(endpoints.get(i))
        }
    }

    // ---------------------------
    fun get_edges(): Array<Edge?> {
        // note - allows corruption of the shared Merc28 points underneath
        return edges
    }

    private fun gather(r0: Routing?, r1: Routing?): Array<Routing?>? {
        val result: Array<Routing?>?
        if (r0 != null) {
            if (r1 != null) {
                result = arrayOfNulls<Routing>(2)
                result.get(0) = r0
                result.get(1) = r1
            } else {
                result = arrayOfNulls<Routing>(1)
                result.get(0) = r0
            }
        } else {
            if (r1 != null) {
                result = arrayOfNulls<Routing>(1)
                result.get(0) = r1
            } else {
                result = null
            }
        }
        return result
    }

    fun get_routings(pos: Merc28?): Array<Routing?>? {
        if (endpoints == null) {
            return null
        }
        return if (points.size == 0) {
            null
        } else if (points.size == 1) {
            // points[0] is necessarily the destination
            val r0 = Routing(pos, points[0], 0.0f)
            gather(r0, null)
        } else {
            // find the segment such that 'pos' subtends the largest angle at its endpoints
            var best_index = -1
            var best_ca = 1.0f
            for (i in segments.indices) {
                val i0 = segments[i]!!.e0!!.index
                val i1 = segments[i]!!.e1!!.index
                val ca = cos_subtended(pos, points[i0], points[i1])
                if (ca < best_ca) {
                    best_ca = ca
                    best_index = i
                }
            }
            if (best_ca < 0.0) {
                // The best segment subtends > 90 degrees at 'pos' : assume we're close to it
                var r0: Waypoints.Routing? = null
                var r1: Waypoints.Routing? = null
                val e0 = segments[best_index]!!.e0
                val e1 = segments[best_index]!!.e1
                if (e0!!.is_destination()) {
                    r0 = Routing(pos, points[e0.index], 0.0f)
                } else {
                    val d0 = calculate_distance(e0, segments[best_index])
                    if (d0 >= 0.0f) {
                        r0 = Routing(pos, points[e0.index], d0)
                    }
                }
                if (e1!!.is_destination()) {
                    r1 = Routing(pos, points[e1.index], 0.0f)
                } else {
                    val d1 = calculate_distance(e1, segments[best_index])
                    if (d1 >= 0.0f) {
                        r1 = Routing(pos, points[e1.index], d1)
                    }
                }
                gather(r0, r1)
            } else {
                // The so-called 'best' segment subtends less than 90 degrees.  We're
                // too far away from it, or too close to one of its ends.
                //
                // For this case, seek the closest point in the mesh.  Then find which
                // pair of its neighbours subtend the maximum angle at 'pos', and show
                // the routings through those
                var best_pt_index = -1
                var best_distance = 0.0f
                for (i in points.indices) {
                    val distance = pos!!.metres_away(points[i]) as Float
                    if (best_pt_index < 0 ||
                            distance < best_distance) {
                        best_distance = distance
                        best_pt_index = i
                    }
                }
                var r0: Routing? = null
                var r1: Routing? = null
                if (endpoints!![best_pt_index].length == 1) {
                    // at the end of the chain
                    val nearest = endpoints!![best_pt_index][0]
                    val next = nearest!!.peer
                    if (nearest.is_destination()) {
                        r0 = Routing(pos, points[nearest.index], 0.0f)
                    } else if (next!!.is_destination()) {
                        r0 = Routing(pos, points[next.index], 0.0f)
                    } else {
                        val dist = calculate_distance(next, nearest.via)
                        r0 = Routing(pos, points[next.index], dist)
                    }
                } else {
                    val ea = endpoints!![best_pt_index]
                    if (ea[0]!!.is_destination()) { // could check any index
                        r0 = Routing(pos, points[best_pt_index], 0.0f)
                    } else {
                        var best_ep0: Endpoint?
                        var best_ep1: Endpoint?
                        best_ca = 1.0f
                        best_ep1 = null
                        best_ep0 = best_ep1
                        val n = ea.size
                        for (i in 0 until n) {
                            for (j in i + 1 until n) {
                                val ix0 = ea[i]!!.peer!!.index
                                val ix1 = ea[j]!!.peer!!.index
                                val ca = cos_subtended(pos, points[ix0], points[ix1])
                                if (best_ep0 == null ||
                                        ca < best_ca) {
                                    best_ca = ca
                                    best_ep0 = ea[i]!!.peer
                                    best_ep1 = ea[j]!!.peer
                                }
                            }
                        }
                        if (best_ep0!!.is_destination()) {
                            r0 = Routing(pos, points[best_ep0.index], 0.0f)
                        } else {
                            val d0: Float
                            d0 = calculate_distance(best_ep0)
                            r0 = if (d0 >= 0.0f) Routing(pos, points[best_ep0.index], d0) else null
                        }
                        if (best_ep1!!.is_destination()) {
                            r1 = Routing(pos, points[best_ep1.index], 0.0f)
                        } else {
                            val d1: Float
                            d1 = calculate_distance(best_ep1)
                            r1 = if (d1 >= 0.0f) Routing(pos, points[best_ep1.index], d1) else null
                        }
                    }
                }
                gather(r0, r1)
            }
        }
    }

    companion object {
        private const val TAG = "Linkages"

        // ---------------------------
        private val edge_comparator: Comparator<WorkingEdge> = object : Comparator<WorkingEdge?>() {
            fun compare(e0: WorkingEdge, e1: WorkingEdge): Int {
                return if (e0.d > e1.d) -1 else if (e0.d < e1.d) +1 else if (e0.i0 < e1.i0) -1 else if (e0.i0 > e1.i0) +1 else if (e0.i1 < e1.i1) -1 else if (e0.i1 > e1.i1) +1 else 0
            }
        }

        // ---------------------------
        private fun compute_mesh(p: Array<Merc28?>, _struts: ArrayList<Waypoints.Strut>?): Set<WorkingEdge> {
            val result: Set<WorkingEdge> = HashSet<WorkingEdge>()
            val n = p.size
            var i: Int
            var j: Int
            var k: Int
            val dist2 = Array(n) { FloatArray(n) }
            i = 0
            while (i < n) {
                dist2[i][i] = 0.0f
                j = i + 1
                while (j < n) {
                    val dx = (p[j]!!.X - p[i]!!.X) as Float
                    val dy = (p[j]!!.Y - p[i]!!.Y) as Float
                    dist2[j][i] = dx * dx + dy * dy
                    dist2[i][j] = dist2[j][i]
                    j++
                }
                i++
            }

            // Make all the 'strut' edges appear to be ludicrously long, so that they
            // won't get picked as edges to keep
            val ns: Int = _struts.size()
            //Log.i(TAG, "Doing " + ns + " struts");
            i = 0
            while (i < ns) {
                val s: Waypoints.Strut = _struts.get(i)
                val i0: Int = s.p0.index
                val i1: Int = s.p1.index
                //Log.i(TAG, "Struct between indices " + i0 + " and " + i1);
                dist2[i0][i1] *= 1000000.0f
                dist2[i1][i0] *= 1000000.0f
                i++
            }
            i = 0
            while (i < n) {
                j = i + 1
                while (j < n) {

                    // candidate line is i, j
                    var ok = true
                    k = 0
                    while (k < n) {
                        if (k != i && k != j) {
                            // Detect if the i,j side is the hypotenuse of the i,j,k triangle
                            // and subtends > 90 degrees at vertex k
                            if (dist2[i][j] > dist2[i][k] + dist2[j][k]) {
                                ok = false
                                break
                            }
                        }
                        k++
                    }
                    if (ok) {
                        // For the distance values shown to the user on-screen, use real
                        // distances, not the up-scaled ones.
                        val d = p[i]!!.metres_away(p[j]) as Float
                        result.add(WorkingEdge(i, j, p, d))
                    }
                    j++
                }
                i++
            }
            return result
        }

        // ---------------------------
        fun minimum_endpoint_distance(eps: Array<Endpoint?>): Float {
            var d = 0.0f
            var ok = false
            for (i in eps.indices) {
                if (eps[i]!!.distance_ok) {
                    if (!ok || eps[i]!!.distance < d) {
                        d = eps[i]!!.distance
                        ok = true
                    }
                }
            }
            return d
        }

        // ---------------------------
        // ---------------------------
        // Code to work out the distances through the mesh to a given 'destination' node.
        fun calculate_distance(e: Endpoint?): Float {
            var found = false
            var dist = 0.0f
            for (i in e!!.siblings.indices) {
                if (e.siblings[i]!!.distance_ok) {
                    if (!found || e.siblings[i]!!.distance < dist) {
                        dist = e.siblings[i]!!.distance
                        found = true
                    }
                }
            }
            return if (found) dist else -1.0f
        }

        fun calculate_distance(e: Endpoint?, exclude_seg: Segment?): Float {
            var found = false
            var dist = 0.0f
            for (i in e!!.siblings.indices) {
                if (e.siblings[i]!!.distance_ok &&
                        !e.siblings[i]!!.downstream.get(exclude_seg!!.index)) {
                    if (!found || e.siblings[i]!!.distance < dist) {
                        dist = e.siblings[i]!!.distance
                        found = true
                    }
                }
            }
            return if (found) dist else -1.0f
        }

        // Return cosine of angle subtended at p by the lines from p to p0, p1 respectively
        fun cos_subtended(p: Merc28?, p0: Merc28?, p1: Merc28?): Float {
            val dx0 = p0!!.X as Float - p!!.X as Float
            val dx1 = p1!!.X as Float - p!!.X as Float
            val dy0 = p0!!.Y as Float - p!!.Y as Float
            val dy1 = p1!!.Y as Float - p!!.Y as Float
            return (dx0 * dx1 + dy0 * dy1) / FloatMath.sqrt((dx0 * dx0 + dy0 * dy0) * (dx1 * dx1 + dy1 * dy1))
        }
    }

    // ---------------------------
    init {
        do_meshing(_points, _struts)
        if (_destination != null) {
            do_distances(_destination.index)
        }
    }
}