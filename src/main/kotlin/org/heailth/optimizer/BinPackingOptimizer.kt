package org.heailth.optimizer

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import org.heailth.model.ParsedRow
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*

class BinPackingOptimizer(private val timeoutSeconds: Int) {

    init {
        Loader.loadNativeLibraries()
    }

    fun solve(cases: List<ParsedRow>, capacity: Int, actualRooms: Set<String>, siteName: String, clusterSites: List<String>? = null): OptimizationResult {
        if (cases.isEmpty()) return OptimizationResult(0, 0, "EMPTY")

        // 1. Pre-calculate Integer Time Scales
        val caseTimes = cases.map { 
            val start = it.occupancyStart!!.toSecondOfDay() / 60
            val end = it.occupancyEnd!!.toSecondOfDay() / 60
            start to end
        }

        // 2. Strengthen Lower Bound Check (Max Concurrency)
        val maxConcurrency = calculateMaxConcurrency(cases.indices.toList(), cases)
        val totalMinutes = cases.sumOf { it.durationMinutes }
        val minRoomsByVolume = Math.ceil(totalMinutes.toDouble() / capacity).toInt()
        val lowerBound = Math.max(minRoomsByVolume, maxConcurrency)

        if (lowerBound >= actualRooms.size) {
            return OptimizationResult(actualRooms.size, 0, "OK_LOWER_BOUND_REACHED")
        }

        // 3. Heuristic Pre-Check (FFD Early Return)
        val ffdResult = solveFFD(cases, capacity, actualRooms, siteName, clusterSites)
        if (ffdResult.roomsUsed <= lowerBound) {
            return ffdResult.copy(status = "FFD_OPTIMAL")
        }

        // 4. Constraint-based solving: transition back to manual boolean constraints 
        // to support the "Implicit Safety Rule" while maintaining solver hinting.
        return solveWithConstraints(cases, capacity, actualRooms, siteName, "DEFAULT", clusterSites, ffdResult)
    }

    fun solveFFD(cases: List<ParsedRow>, capacity: Int, actualRooms: Set<String>, siteName: String, clusterSites: List<String>? = null): OptimizationResult {
        val sortedIndices = cases.indices.sortedByDescending { cases[it].durationMinutes }
        val bins = mutableListOf<MutableList<Int>>()
        
        for (idx in sortedIndices) {
            val case = cases[idx]
            var placed = false
            for (bin in bins) {
                val currentLoad = bin.sumOf { cases[it].durationMinutes }
                if (currentLoad + case.durationMinutes <= capacity) {
                    val hasOverlap = bin.any { otherIdx ->
                        val other = cases[otherIdx]
                        val overlaps = other.occupancyStart!!.isBefore(case.occupancyEnd) && case.occupancyStart!!.isBefore(other.occupancyEnd)
                        if (overlaps) {
                            val sameOriginalRoom = other.record?.orName == case.record?.orName && other.record?.site == case.record?.site
                            val originallyOverlapped = other.record!!.patientIn.isBefore(case.record!!.patientOut) && case.record!!.patientIn.isBefore(other.record!!.patientOut)
                            !(sameOriginalRoom && !originallyOverlapped)
                        } else false
                    }

                    if (!hasOverlap) {
                        bin.add(idx)
                        placed = true
                        break
                    }
                }
            }
            if (!placed) bins.add(mutableListOf(idx))
        }
        
        val roomsUsed = bins.size
        val siteOfBin = getSiteOfBinMapping(roomsUsed, actualRooms, clusterSites)
        for (i in bins.indices) {
            val roomLetter = ('A' + i).toString()
            val binSiteName = siteOfBin[i]
            bins[i].forEach { idx ->
                val displaySite = binSiteName ?: cases[idx].record!!.site
                cases[idx].assignedRoomName = "$displaySite - Consolidated Room $roomLetter" 
            }
        }
        
        return OptimizationResult(roomsUsed, Math.max(0, actualRooms.size - roomsUsed), "FFD_HEURISTIC", bins)
    }

    private fun getSiteOfBinMapping(numBins: Int, actualRooms: Set<String>, clusterSites: List<String>?): Map<Int, String> {
        val siteOfBin = mutableMapOf<Int, String>()
        if (clusterSites != null && clusterSites.size > 1) {
            val roomsBySite = actualRooms.groupBy { it.split(" - ")[0] }
            val sortedSites = clusterSites.sortedByDescending { roomsBySite[it]?.size ?: 0 }
            
            var binIdx = 0
            for (site in sortedSites) {
                val siteRoomCount = roomsBySite[site]?.size ?: 0
                for (k in 0 until siteRoomCount) {
                    if (binIdx < numBins) {
                        siteOfBin[binIdx] = site
                        binIdx++
                    }
                }
            }
            while (binIdx < numBins) {
                siteOfBin[binIdx] = sortedSites.first()
                binIdx++
            }
        }
        return siteOfBin
    }

    fun solveWithConstraints(cases: List<ParsedRow>, capacity: Int, actualRooms: Set<String>, siteName: String, mode: String, clusterSites: List<String>? = null, ffdResult: OptimizationResult? = null): OptimizationResult {
        if (cases.isEmpty()) return OptimizationResult(0, 0, "EMPTY")

        val model = CpModel()
        val numCases = cases.size
        
        // 7. Tighten Search Space (Bin Reduction)
        val maxBins = if (clusterSites != null && clusterSites.size > 1) actualRooms.size else actualRooms.size
        val numBins = maxBins

        // Pre-calculate integer times
        val caseIntervals = cases.map { 
            val start = it.occupancyStart!!.toSecondOfDay() / 60
            val end = it.occupancyEnd!!.toSecondOfDay() / 60
            val duration = it.durationMinutes
            Triple(start, end, duration)
        }

        // x[i][j] is true if case i is in bin j
        val x = Array(numCases) { Array(numBins) { model.newBoolVar("x_${it}") } }
        // y[j] is true if bin j is used
        val y = Array(numBins) { model.newBoolVar("y_$it") }

        for (i in 0 until numCases) {
            val caseBins = mutableListOf<com.google.ortools.sat.BoolVar>()
            for (j in 0 until numBins) {
                caseBins.add(x[i][j])
            }
            model.addExactlyOne(caseBins)
        }

        for (j in 0 until numBins) {
            val binLoad = mutableListOf<LinearExpr>()
            for (i in 0 until numCases) {
                binLoad.add(LinearExpr.term(x[i][j], cases[i].durationMinutes.toLong()))
            }
            model.addLessOrEqual(LinearExpr.sum(binLoad.toTypedArray()), LinearExpr.term(y[j], capacity.toLong()))
        }

        // 4. Manual Conflict Constraints with Implicit Safety Rule
        // We avoid NoOverlap because it cannot handle conditional overlaps (safety rule).
        for (j in 0 until numBins) {
            for (i in 0 until numCases) {
                for (k in i + 1 until numCases) {
                    val c1 = cases[i]
                    val c2 = cases[k]
                    
                    val overlap = c1.occupancyStart!!.isBefore(c2.occupancyEnd) && c2.occupancyStart!!.isBefore(c1.occupancyEnd)
                    if (overlap) {
                        // Implicit Safety Rule: Allow padding-induced overlaps if they were originally in the same room and didn't overlap raw
                        val sameOriginalRoom = c1.record?.orName == c2.record?.orName && c1.record?.site == c2.record?.site
                        val originallyOverlapped = c1.record!!.patientIn.isBefore(c2.record!!.patientOut) && c2.record!!.patientIn.isBefore(c1.record!!.patientOut)
                        
                        if (!(sameOriginalRoom && !originallyOverlapped)) {
                            // Conflict detected and safety rule doesn't apply
                            // (x[i][j] AND x[k][j]) must be false
                            model.addAtMostOne(arrayOf(x[i][j], x[k][j]))
                        }
                    }
                }
            }
        }

        // 3. Solver Hinting (Warm Start)
        if (ffdResult != null && ffdResult.binIndices != null) {
            for (j in ffdResult.binIndices.indices) {
                if (j < numBins) {
                    model.addHint(y[j], 1L)
                    ffdResult.binIndices[j].forEach { i ->
                        model.addHint(x[i][j], 1L)
                    }
                }
            }
        }

        // Mapping bins to sites if it's a cluster
        if (clusterSites != null && clusterSites.size > 1) {
            val siteOfBin = getSiteOfBinMapping(numBins, actualRooms, clusterSites)

            val siteUsedVars = mutableMapOf<String, com.google.ortools.sat.BoolVar>()
            clusterSites.forEach { site ->
                siteUsedVars[site] = model.newBoolVar("site_used_$site")
            }

            // A site is used if ANY of its bins are used
            for (j in 0 until numBins) {
                val site = siteOfBin[j]!!
                model.addLessOrEqual(y[j], siteUsedVars[site]!!)
            }

            // Objective: minimize weighted sum of sites and rooms
            val objTerms = mutableListOf<LinearExpr>()
            clusterSites.forEachIndexed { index, site ->
                val weight = 1000L + index
                objTerms.add(LinearExpr.term(siteUsedVars[site]!!, weight))
            }
            y.forEach { objTerms.add(LinearExpr.term(it, 1L)) }
            
            model.minimize(LinearExpr.sum(objTerms.toTypedArray()))
        } else {
            // Standard symmetry breaking and room minimization
            for (j in 0 until numBins - 1) {
                model.addLessOrEqual(y[j + 1], y[j])
            }
            model.minimize(LinearExpr.sum(y.map { LinearExpr.term(it, 1L) }.toTypedArray()))
        }

        val solver = CpSolver()
        solver.parameters.maxTimeInSeconds = timeoutSeconds.toDouble()
        
        // 5. Tune Solver Parallelism
        solver.parameters.numSearchWorkers = 4
        
        val status = solver.solve(model)

        return if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            mapResults(solver, cases, actualRooms, x, y, clusterSites)
        } else {
            ffdResult ?: solveFFD(cases, capacity, actualRooms, siteName, clusterSites)
        }
    }

    fun solveVolumeBased(cases: List<ParsedRow>, capacity: Int, actualRooms: Set<String>, siteName: String, clusterSites: List<String>? = null): OptimizationResult {
        val totalMinutes = cases.sumOf { it.durationMinutes }
        val minRoomsByVolume = Math.ceil(totalMinutes.toDouble() / capacity).toInt()
        
        val maxConcurrency = calculateMaxConcurrency(cases.indices.toList(), cases)
        val lowerBound = Math.max(minRoomsByVolume, maxConcurrency)
        
        val res = solveFFD(cases, capacity, actualRooms, siteName, clusterSites)
        return OptimizationResult(res.roomsUsed, res.roomsSaved, "VOLUME_BASED_LOWER_BOUND_$lowerBound")
    }

    private fun mapResults(solver: CpSolver, cases: List<ParsedRow>, actualRooms: Set<String>, x: Array<Array<com.google.ortools.sat.BoolVar>>, y: Array<com.google.ortools.sat.BoolVar>, clusterSites: List<String>? = null): OptimizationResult {
        var binsUsed = 0
        val siteOfBin = getSiteOfBinMapping(y.size, actualRooms, clusterSites)

        for (j in y.indices) {
            if (solver.value(y[j]) == 1L) {
                val roomLetter = ('A' + binsUsed).toString()
                binsUsed++
                val binSiteName = siteOfBin[j]
                
                for (i in cases.indices) {
                    if (solver.value(x[i][j]) == 1L) {
                        val displaySite = binSiteName ?: cases[i].record!!.site
                        cases[i].assignedRoomName = "$displaySite - Consolidated Room $roomLetter"
                    }
                }
            }
        }
        return OptimizationResult(binsUsed, Math.max(0, actualRooms.size - binsUsed), "OR_TOOLS_SAT")
    }

    private fun calculateMaxConcurrency(indices: List<Int>, cases: List<ParsedRow>): Int {
        val events = mutableListOf<Event>()
        for (idx in indices) {
            val c = cases[idx]
            // Use raw patient entry/exit times for true concurrency lower bound
            // This prevents padding from artificially raising the required room count
            events.add(Event(c.record!!.patientIn, 1))
            events.add(Event(c.record!!.patientOut, -1))
        }
        events.sortWith(compareBy<Event> { it.time }.thenBy { it.type })

        var maxRooms = 0
        var currentRooms = 0
        for (e in events) {
            currentRooms += e.type
            if (currentRooms > maxRooms) maxRooms = currentRooms
        }
        return maxRooms
    }

    private data class Event(val time: LocalTime, val type: Int)

    data class OptimizationResult(val roomsUsed: Int, val roomsSaved: Int, val status: String, val binIndices: List<List<Int>>? = null)
}
