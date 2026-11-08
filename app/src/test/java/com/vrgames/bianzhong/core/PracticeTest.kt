package com.vrgames.bianzhong.core

import org.junit.Assert.*
import org.junit.Test

class PracticeTest {
    private val low = Rack.tierOf(1)[3].id
    private val high = Rack.tierOf(1)[4].id

    @Test fun walksThroughListeningAndSwappingStepByStep() {
        val p = Practice(low, high)
        assertEquals(listOf(high, low), p.order)
        assertFalse("the wrong bell does nothing", p.pinch(low))
        assertEquals(Practice.Step.LISTEN_LEFT, p.step)
        for (id in listOf(high, low, low, high)) assertTrue(p.pinch(id))
        assertTrue(p.done)
        assertEquals(listOf(low, high), p.order)
    }

    @Test fun everyBellHasAKindAsOnTheOriginalRack() {
        assertEquals(1, Rack.BELLS.count { Rack.kind(it) == BellKind.BO })
        assertTrue(Rack.BELLS.filter { it.tier == 2 }.all { Rack.kind(it) == BellKind.NIU })
        assertTrue(Rack.BELLS.filter { it.tier == 1 }.all { Rack.kind(it) == BellKind.YONG })
    }

    @Test fun narrationTextsAreALengthThatPlaysInUnderHalfAMinute() {
        for (t in BellTopics.ALL) assertTrue("${t.id} ${t.narration.length}", t.narration.length in 60..110)
    }

    @Test fun partAssistAcceptsTheRightBellAtEitherPartButStrictDoesNot() {
        val song = Songs.TWINKLE
        val note = song.notes.first { !it.damp }
        val other = if (note.part == Part.MIDDLE) Part.SIDE else Part.MIDDLE
        assertTrue(Performance(song).hit(note.time, note.bell, other, partAssist = true) is Performance.Hit.Struck)
        assertTrue(Performance(song).hit(note.time, note.bell, other, partAssist = false) is Performance.Hit.Stray)
    }
}
