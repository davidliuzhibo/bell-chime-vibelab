package com.vrgames.bianzhong.core

import kotlin.math.abs
import kotlin.math.max

enum class Judge(val label: String, val score: Double) { PERFECT("正好", 1.0), GOOD("稍早", 0.7), LATE("稍晚", 0.7), MISS("漏掉", 0.0) }

/**
 * Playing a song in time: every stroke and damp of the [song] judged by how near its time the player
 * pinched the right bell at the right part. [speed] stretches the song (1 the written tempo; above 1
 * faster). Times are seconds from the song's first beat (the count-in comes before 0).
 *
 * A pinch is matched with the nearest pending stroke on that bell within [GOOD_S]; on a bell that is
 * due to be damped, a pinch near the damp's time damps it. A pinch matching nothing (a wrong bell, or
 * the wrong part) is a stray: each costs [STRAY_COST] of the accuracy. Strokes not played by
 * [MISS_AFTER] seconds past their time are missed.
 */
class Performance(val song: Song, val speed: Double = 1.0) {
    val notes: List<Note> = song.notes.map { it.copy(time = it.time / speed) }
    val judged = arrayOfNulls<Judge>(notes.size)
    var strays = 0; private set
    val length = song.length / speed

    sealed interface Hit {
        data class Struck(val index: Int, val judge: Judge) : Hit
        data class Damped(val index: Int, val judge: Judge) : Hit
        /** A pinch no stroke wanted: [why] says what was expected nearest in time, if anything. */
        data class Stray(val why: String?) : Hit
    }

    /**
     * The player pinched [bell] at time [t]. With [partAssist] the right bell is enough, since a gaze
     * aimed at a bell's middle or edge is hard to hold at speed; without it [part] has to match too.
     */
    fun hit(t: Double, bell: Bell, part: Part, partAssist: Boolean = false): Hit {
        var best = -1
        var bestDt = Double.MAX_VALUE
        for (i in notes.indices) {
            if (judged[i] != null) continue
            val n = notes[i]
            if (n.bell.id != bell.id) continue
            if (!n.damp && !partAssist && n.part != part) continue
            val dt = abs(t - n.time)
            if (dt <= GOOD_S && dt < bestDt) {
                best = i
                bestDt = dt
            }
        }
        if (best < 0) {
            strays++
            val near = notes.indices.filter { judged[it] == null }.minByOrNull { abs(notes[it].time - t) }?.let { notes[it] }
            val why = near?.takeIf { abs(it.time - t) < 0.6 }?.let { n ->
                when {
                    n.bell.id != bell.id -> "该敲另一口钟"
                    n.damp -> null
                    n.part != part -> "该敲${n.part.label}"
                    else -> null
                }
            }
            return Hit.Stray(why)
        }
        val n = notes[best]
        val d = t - n.time
        val j = when {
            abs(d) <= PERFECT_S -> Judge.PERFECT
            d < 0 -> Judge.GOOD
            else -> Judge.LATE
        }
        judged[best] = j
        return if (n.damp) Hit.Damped(best, j) else Hit.Struck(best, j)
    }

    /** Marks as missed every stroke left unplayed long enough before [t]; returns their indices. */
    fun advance(t: Double): List<Int> {
        val out = ArrayList<Int>()
        for (i in notes.indices) {
            if (judged[i] == null && t - notes[i].time > MISS_AFTER) {
                judged[i] = Judge.MISS
                out += i
            }
        }
        return out
    }

    fun done(t: Double) = t > length + MISS_AFTER + 0.5 || judged.all { it != null } && t > length

    /** Share of the song played well (0..1): perfects count fully, near ones 70%, strays cost a little. */
    fun accuracy(): Double {
        if (notes.isEmpty()) return 0.0
        val sum = judged.sumOf { it?.score ?: 0.0 }
        return max(0.0, sum / notes.size - strays * STRAY_COST)
    }

    fun count(j: Judge) = judged.count { it == j }

    /** 0 (not passed) to 3 stars. */
    fun stars(): Int = stars(accuracy())

    companion object {
        const val PERFECT_S = 0.09
        const val GOOD_S = 0.2
        const val MISS_AFTER = 0.25
        const val STRAY_COST = 0.01
        const val PASS = 0.7
        const val TWO = 0.85
        const val THREE = 0.95

        /** Stars for an accuracy (a hair of rounding allowed at each bound). */
        fun stars(acc: Double) = when {
            acc >= THREE - 1e-9 -> 3
            acc >= TWO - 1e-9 -> 2
            acc >= PASS - 1e-9 -> 1
            else -> 0
        }
    }
}
