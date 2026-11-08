package com.vrgames.bianzhong.core

/**
 * The rack's bells. Like the Marquis Yi of Zeng's set, each bell sounds two tones: struck in the
 * middle of its face (正鼓) and struck at its side (侧鼓), a third apart. [main] and [side] are MIDI
 * notes. Tiers: 0 the big bells at the bottom, 1 the middle row at eye height, 2 the small bells on
 * top. [slot] is the bell's place along its tier, left to right.
 */
data class Bell(val id: Int, val tier: Int, val slot: Int, val main: Int, val side: Int) {
    /** How tall the bell is (meters): lower notes, bigger bells. */
    val height: Double get() = when (tier) {
        0 -> 0.5 - (main - 55) * 0.008
        1 -> 0.34 - (main - 67) * 0.0065
        else -> 0.2 - (main - 86) * 0.004
    }

    fun note(part: Part) = if (part == Part.MIDDLE) main else side
}

enum class Part(val label: String) { MIDDLE("正面"), SIDE("侧面") }

object Rack {
    private fun tier(t: Int, vararg mains: Pair<Int, Int>) = mains.mapIndexed { k, (m, gap) -> Triple(t, k, m to m + gap) }

    /** Pentatonic mains; each side tone fills in the scale (a major third above 宫 and 徵, minor elsewhere). */
    val BELLS: List<Bell> = (
        tier(0, 55 to 4, 60 to 4, 62 to 3, 64 to 3) +
            tier(1, 67 to 4, 69 to 3, 72 to 4, 74 to 3, 76 to 3, 79 to 4, 81 to 3, 84 to 4) +
            tier(2, 86 to 3, 88 to 3, 91 to 4, 93 to 3, 96 to 4, 98 to 3)
        ).mapIndexed { id, (t, k, n) -> Bell(id, t, k, n.first, n.second) }

    fun tierOf(t: Int) = BELLS.filter { it.tier == t }

    /** As on the original rack: small niu bells on top, yong bells below, one bo in the lowest row. */
    fun kind(b: Bell): BellKind = when {
        b.tier == 2 -> BellKind.NIU
        b.tier == 0 && b.slot == 1 -> BellKind.BO
        else -> BellKind.YONG
    }

    /**
     * The bell and part that sound [midi]: a bell's middle tone if one has it (the plainest stroke),
     * else a side tone. Null if no bell can.
     */
    fun find(midi: Int): Pair<Bell, Part>? =
        BELLS.firstOrNull { it.main == midi }?.let { it to Part.MIDDLE } ?: BELLS.firstOrNull { it.side == midi }?.let { it to Part.SIDE }

    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(midi: Int) = NAMES[midi % 12] + (midi / 12 - 1)

    /** Parses a note name like "G4" or "C#5" into MIDI. */
    fun midi(name: String): Int {
        val octave = name.last().digitToInt()
        val pc = NAMES.indexOf(name.dropLast(1))
        require(pc >= 0) { "bad note $name" }
        return (octave + 1) * 12 + pc
    }
}
