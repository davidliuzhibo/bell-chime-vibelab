package com.vrgames.bianzhong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rack covers every tune, the judge is fair, the lessons can be done well, and trophies follow. */
class BellTest {
    @Test
    fun everyBellHasTwoTonesAThirdApart() {
        assertEquals(18, Rack.BELLS.size)
        for (b in Rack.BELLS) assertTrue("${b.id}", b.side - b.main in 3..4)
        // Bigger bells for lower notes, within a tier.
        for (t in 0..2) {
            val bells = Rack.tierOf(t)
            for (k in 1 until bells.size) assertTrue(bells[k].height < bells[k - 1].height)
        }
    }

    @Test
    fun everyTuneIsPlayableAndInOrder() {
        for (s in Songs.ALL) {
            assertTrue("${s.id} has notes", s.notes.size > 20)
            for (k in 1 until s.notes.size) assertTrue("${s.id} in time order", s.notes[k].time >= s.notes[k - 1].time - 1e-9)
            assertTrue("${s.id} uses side tones or damps", s.notes.any { it.part == Part.SIDE || it.damp })
            // No two strokes on the same bell closer than a sixth of a second (a player can't).
            val strokes = s.notes.filter { !it.damp }
            for (k in 1 until strokes.size) assertTrue("${s.id} note $k playable", strokes[k].time - strokes[k - 1].time > 0.15)
        }
        assertEquals(Rack.midi("B4"), Rack.find(Rack.midi("B4"))!!.first.side)
        assertEquals(Part.MIDDLE, Rack.find(Rack.midi("C5"))!!.second)
    }

    @Test
    fun perfectPlayEarnsThreeStars() {
        for (s in Songs.ALL) {
            val p = Performance(s)
            for (n in p.notes) assertTrue(p.hit(n.time + 0.02, n.bell, n.part) !is Performance.Hit.Stray)
            p.advance(p.length + 1)
            assertEquals("${s.id}", 3, p.stars())
        }
    }

    @Test
    fun lateWrongAndMissedCost() {
        val s = Songs.TWINKLE
        val p = Performance(s)
        // Every note a bit late: all "稍晚", 70%: one star.
        for (n in p.notes) p.hit(n.time + 0.15, n.bell, n.part)
        p.advance(p.length + 1)
        assertEquals(0.7, p.accuracy(), 1e-9)
        assertEquals(1, p.stars())
        val q = Performance(s)
        // The wrong part of the right bell is a stray, and the note stays to be played.
        val side = q.notes.first { it.part == Part.MIDDLE && !it.damp }
        assertTrue(q.hit(side.time, side.bell, Part.SIDE) is Performance.Hit.Stray)
        assertEquals(1, q.strays)
        // Unplayed notes are missed once they are well past.
        val missed = q.advance(q.length + 1)
        assertEquals(q.notes.size, missed.size)
        assertEquals(0, q.stars())
    }

    @Test
    fun fasterTempoShrinksTheTimes() {
        val p = Performance(Songs.JOY, 1.3)
        assertEquals(Songs.JOY.length / 1.3, p.length, 1e-9)
        assertEquals(Songs.JOY.notes.last().time / 1.3, p.notes.last().time, 1e-9)
    }

    @Test
    fun theLessonCanBeDoneWithoutMistakes() {
        val t = Tutorial()
        for (s in t.steps) assertTrue(t.pinch(s.bell, s.part, true))
        assertTrue(t.done)
        assertEquals(3, t.stars())
        val u = Tutorial()
        val first = u.current!!
        assertFalse(u.pinch(Rack.BELLS.first { it.id != first.bell.id }, Part.MIDDLE, true))
        assertEquals(1, u.mistakes)
    }

    @Test
    fun theRestoreTakesThreeSwapsAtLeast() {
        val r = Restore()
        assertEquals(3, r.fewest)
        assertFalse(r.solved)
        while (!r.solved) {
            val i = r.order.indices.first { r.order[it] != r.goal[it] }
            r.swap(i, r.order.indexOf(r.goal[i]))
        }
        assertEquals(3, r.swaps)
        assertEquals(3, r.stars())
        // The scrambled bells are neighbors in pitch: only the ear tells them apart in the game.
        assertEquals(r.goal.sorted(), r.order.sorted())
    }

    @Test
    fun trophiesFollowTheStars() {
        val pr = Progress()
        assertTrue(pr.open(Levels.ALL[0]))
        assertFalse(pr.open(Levels.ALL[1]))
        assertTrue("free play is open from the start", pr.freePlay)
        for (l in Levels.ALL) pr.record(l.id, 1)
        assertTrue(pr.bronze); assertFalse(pr.silver); assertTrue(pr.freePlay)
        for (l in Levels.ALL.take(3)) pr.record(l.id, 3)
        assertTrue(pr.silver); assertFalse(pr.gold)
        for (l in Levels.ALL) pr.record(l.id, 3)
        assertTrue(pr.gold)
        pr.record("jingle", 2)
        assertEquals(pr.stars, Progress.load(pr.save()).stars)
    }
}
