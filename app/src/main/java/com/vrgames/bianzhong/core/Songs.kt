package com.vrgames.bianzhong.core

/**
 * A stroke to play: at [time] seconds from the song's first beat, on [bell], at [part]; or a damp
 * ([damp], the palm stopping a bell that is still sounding). [degree] is the jianpu digit shown.
 */
data class Note(val time: Double, val bell: Bell, val part: Part, val damp: Boolean = false, val degree: String = "")

/**
 * A tune in jianpu: [tonic] is 1 (do) as MIDI, [bpm] quarter beats a minute, [score] the notes as
 * space-separated tokens "<degree>[:<beats>][x]" — degree 1..7 with ' for an octave up and _ for an
 * octave down (b or # before it for a flat or sharp), "0" a rest; beats default 1; a trailing x damps
 * the bell at the end of the note. "|" bars are ignored. All public-domain melodies, arranged here.
 */
class Song(val id: String, val title: String, val about: String, val tonic: Int, val bpm: Double, score: String) {
    val beat = 60.0 / bpm
    val notes: List<Note>
    val length: Double

    init {
        val out = ArrayList<Note>()
        var t = 0.0
        for (tok in score.split(" ", "\n").map { it.trim() }.filter { it.isNotEmpty() && it != "|" }) {
            val damp = tok.endsWith("x")
            val body = tok.removeSuffix("x")
            val deg = body.substringBefore(":")
            val beats = body.substringAfter(":", "1").toDouble()
            if (deg != "0") {
                val midi = tonic + semis(deg)
                val (bell, part) = Rack.find(midi) ?: error("$id: no bell plays ${Rack.name(midi)} ($deg)")
                out += Note(t * beat, bell, part, false, deg)
                if (damp) out += Note((t + beats) * beat, bell, part, true, deg)
            }
            t += beats
        }
        notes = out
        length = t * beat
    }

    companion object {
        private val SCALE = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        fun semis(deg: String): Int {
            var s = deg
            var acc = 0
            if (s.startsWith("#")) { acc += 1; s = s.drop(1) }
            if (s.startsWith("b")) { acc -= 1; s = s.drop(1) }
            val d = s.first().digitToInt()
            for (c in s.drop(1)) acc += if (c == '\'') 12 else if (c == '_') -12 else 0
            return SCALE[d - 1] + acc
        }
    }
}

object Songs {
    /** 小星星 (the old French air Mozart varied), 1 = G4. */
    val TWINKLE = Song(
        "twinkle", "小星星", "一首人人会唱的老歌，旋律来自十八世纪的法国民谣。", Rack.midi("G4"), 76.0,
        """
        1 1 5 5 | 6 6 5:2 | 4 4 3 3 | 2 2 1:2x |
        5 5 4 4 | 3 3 2:2 | 5 5 4 4 | 3 3 2:2 |
        1 1 5 5 | 6 6 5:2 | 4 4 3 3 | 2 2 1:2x
        """,
    )

    /** 茉莉花 (Jiangsu folk song), 1 = G4: the 3s are the G bell's side tone. */
    val JASMINE = Song(
        "jasmine", "茉莉花", "江苏民歌。清朝时就传到了欧洲，被写进了歌剧《图兰朵》。", Rack.midi("G4"), 62.0,
        """
        3 3:0.5 5:0.5 6:0.5 1':0.5 1':0.5 6:0.5 | 5 5:0.5 6:0.5 5:2 |
        3 3:0.5 5:0.5 6:0.5 1':0.5 1':0.5 6:0.5 | 5 5:0.5 6:0.5 5:2x |
        5 5 5 3:0.5 5:0.5 | 6 6 5:2 |
        3 2:0.5 3:0.5 5 3:0.5 2:0.5 | 1 1:0.5 2:0.5 1:2x
        """,
    )

    /** 送别 (J. P. Ordway's 1851 melody, sung in China with Li Shutong's words), 1 = C4: the big bells. */
    val FAREWELL = Song(
        "farewell", "送别", "“长亭外，古道边”。旋律来自一首十九世纪的美国歌曲，李叔同填上了中文词。", Rack.midi("C4"), 70.0,
        """
        5 3:0.5 5:0.5 1':2 | 6 1' 5:2 | 5 1:0.5 2:0.5 3 2:0.5 1:0.5 | 2:4x |
        5 3:0.5 5:0.5 1':1.5 7:0.5 | 6 1' 5:2 | 5 2:0.5 3:0.5 4:1.5 7_:0.5 | 1:4x |
        6 1' 1':2 | 7 6:0.5 7:0.5 1':2 | 6:0.5 7:0.5 1':0.5 6:0.5 6:0.5 5:0.5 3:0.5 1:0.5 | 2:4x |
        5 3:0.5 5:0.5 1':1.5 7:0.5 | 6 1' 5:2 | 5 2:0.5 3:0.5 4:1.5 7_:0.5 | 1:4x
        """,
    )

    /** 欢乐颂 (Beethoven's Ninth), 1 = C5: the 4s are the D bell's side tone. */
    val JOY = Song(
        "joy", "欢乐颂", "贝多芬第九交响曲里的旋律，如今在世界各地的庆典上演奏。", Rack.midi("C5"), 92.0,
        """
        3 3 4 5 | 5 4 3 2 | 1 1 2 3 | 3:1.5 2:0.5 2:2x |
        3 3 4 5 | 5 4 3 2 | 1 1 2 3 | 2:1.5 1:0.5 1:2x |
        2 2 3 1 | 2 3:0.5 4:0.5 3 1 | 2 3:0.5 4:0.5 3 2 | 1 2 5_:2x |
        3 3 4 5 | 5 4 3 2 | 1 1 2 3 | 2:1.5 1:0.5 1:2x
        """,
    )

    /** 铃儿响叮当 (James Lord Pierpont, 1857), 1 = C5: the silver trophy's hidden tune. */
    val JINGLE = Song(
        "jingle", "铃儿响叮当", "1857 年写成的歌，原本是为感恩节写的，后来成了最有名的圣诞歌。", Rack.midi("C5"), 104.0,
        """
        3 3 3:2 | 3 3 3:2 | 3 5 1:1.5 2:0.5 | 3:4x |
        4 4 4:1.5 4:0.5 | 4 3 3 3:0.5 3:0.5 | 3 2 2 3 | 2:2 5:2x |
        3 3 3:2 | 3 3 3:2 | 3 5 1:1.5 2:0.5 | 3:4x |
        4 4 4:1.5 4:0.5 | 4 3 3 3:0.5 3:0.5 | 5 5 4 2 | 1:4x
        """,
    )

    val ALL = listOf(TWINKLE, JASMINE, FAREWELL, JOY, JINGLE)
    fun byId(id: String) = ALL.first { it.id == id }
}
