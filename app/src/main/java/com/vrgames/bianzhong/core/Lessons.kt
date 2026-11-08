package com.vrgames.bianzhong.core

/**
 * The first chapter: one stroke at a time, no clock. Each step names a bell and a part (or a damp);
 * a wrong pinch is a mistake. 0 or 1 mistakes earn three stars, up to 4 two, more one.
 */
class Tutorial {
    data class Step(val bell: Bell, val part: Part, val damp: Boolean, val say: String)

    val steps: List<Step> = run {
        fun b(n: String) = Rack.BELLS.first { it.main == Rack.midi(n) }
        listOf(
            Step(b("C5"), Part.MIDDLE, false, "看着发亮的这口钟的正中间，捏一下：敲它的正面。"),
            Step(b("A4"), Part.MIDDLE, false, "再敲这一口的正面。"),
            Step(b("G4"), Part.MIDDLE, false, "这口钟的正面……"),
            Step(b("G4"), Part.SIDE, false, "……再看着它的侧边（蓝色光点）捏一下：同一口钟，换了一个音！这就是“一钟双音”。"),
            Step(b("E5"), Part.MIDDLE, false, "敲这一口。"),
            Step(b("E5"), Part.MIDDLE, true, "它还在响：趁着余音，再捏它一下，就是用手掌按住，让声音停下（止音）。"),
            Step(b("G4"), Part.MIDDLE, false, "最后，按顺序敲出五个音：宫……"),
            Step(b("A4"), Part.MIDDLE, false, "商……"),
            Step(b("G4"), Part.SIDE, false, "角（G 钟的侧面）……"),
            Step(b("D5"), Part.MIDDLE, false, "徵……"),
            Step(b("E5"), Part.MIDDLE, false, "羽。"),
            Step(b("G5"), Part.MIDDLE, false, "再高一个八度的宫，收尾！"),
        )
    }
    var at = 0; private set
    var mistakes = 0; private set
    val done get() = at >= steps.size
    val current get() = steps.getOrNull(at)

    /** A pinch on [bell] at [part]; [ringing] whether that bell is still sounding. True if it was the step. */
    fun pinch(bell: Bell, part: Part, ringing: Boolean): Boolean {
        val s = current ?: return false
        val ok = s.bell.id == bell.id && (if (s.damp) ringing else s.part == part)
        if (ok) at++ else mistakes++
        return ok
    }

    fun stars() = when {
        !done -> 0
        mistakes <= 1 -> 3
        mistakes <= 4 -> 2
        else -> 1
    }
}

/**
 * The second chapter: five bells of the middle row were hung in the wrong places and look alike; by
 * ear, swap them back into rising order. [order] holds the bell ids at the scrambled slots (left to
 * right). Three stars for at most one swap more than the fewest possible and no hint.
 */
class Restore {
    /** The middle row's slots that were scrambled, and the bells that belong there in order. */
    val slots = listOf(2, 3, 4, 5, 6)
    val goal: List<Int> = slots.map { s -> Rack.tierOf(1).first { it.slot == s }.id }
    val order: MutableList<Int> = listOf(goal[3], goal[2], goal[4], goal[0], goal[1]).toMutableList()
    var swaps = 0; private set
    var hints = 0; private set
    val fewest = fewestSwaps(order, goal)
    val solved get() = order == goal

    /** Swaps the bells hanging at scrambled positions [a] and [b] (indices into [order]). */
    fun swap(a: Int, b: Int) {
        if (a == b || solved) return
        val t = order[a]; order[a] = order[b]; order[b] = t
        swaps++
    }

    /** A hint: the first position holding the wrong bell, and where that bell belongs. */
    fun hint(): Pair<Int, Int>? {
        val i = order.indices.firstOrNull { order[it] != goal[it] } ?: return null
        hints++
        return i to goal.indexOf(order[i])
    }

    fun stars() = when {
        !solved -> 0
        swaps <= fewest + 1 && hints == 0 -> 3
        swaps <= fewest + 4 && hints <= 1 -> 2
        else -> 1
    }

    companion object {
        fun fewestSwaps(from: List<Int>, to: List<Int>): Int {
            val a = from.toMutableList()
            var n = 0
            for (i in a.indices) {
                while (a[i] != to[i]) {
                    val j = to.indexOf(a[i])
                    val t = a[i]; a[i] = a[j]; a[j] = t
                    n++
                }
            }
            return n
        }
    }
}

/**
 * The warm-up before the restore chapter: only two bells, and every step spelled out. Listen to the
 * left one, listen to the right one, notice the right is lower, then swap them. Exists because the
 * full puzzle gives no way in for a player who has never compared two pitches by ear.
 */
class Practice(private val lowBell: Int, private val highBell: Int) {
    enum class Step { LISTEN_LEFT, LISTEN_RIGHT, PICK_LOW, PICK_HIGH, DONE }

    /** Bell ids at the two positions, left to right. Starts wrong: the high bell on the left. */
    val order: MutableList<Int> = mutableListOf(highBell, lowBell)
    var step = Step.LISTEN_LEFT; private set
    val done get() = step == Step.DONE

    /** The bell this step wants. */
    val wanted: Int? get() = when (step) {
        Step.LISTEN_LEFT -> highBell
        Step.LISTEN_RIGHT -> lowBell
        Step.PICK_LOW -> lowBell
        Step.PICK_HIGH -> highBell
        Step.DONE -> null
    }

    val say: String get() = when (step) {
        Step.LISTEN_LEFT -> "小练习：只有两口钟。先捏发亮的左边这口，听它的音。金色柱子越高，音越高。"
        Step.LISTEN_RIGHT -> "再捏右边这口。听：它比左边的低，金色柱子也矮。"
        Step.PICK_LOW -> "规矩是从左到右由低到高，所以这两口要换位置。先捏矮柱子的这口，选中它。"
        Step.PICK_HIGH -> "再捏另一口：两口钟就交换了位置。"
        Step.DONE -> "换好了，柱子一级比一级高！正式关一样做：听音、比较、两两交换。"
    }

    /** A pinch on bell [id]; true if it was the one the step wants. */
    fun pinch(id: Int): Boolean {
        if (id != wanted) return false
        step = when (step) {
            Step.LISTEN_LEFT -> Step.LISTEN_RIGHT
            Step.LISTEN_RIGHT -> Step.PICK_LOW
            Step.PICK_LOW -> Step.PICK_HIGH
            Step.PICK_HIGH -> { order.reverse(); Step.DONE }
            Step.DONE -> Step.DONE
        }
        return true
    }
}

/** The chapters in order, and what the player has earned (best stars per level, trophies). */
object Levels {
    enum class Kind { TUTORIAL, RESTORE, SONG }

    data class Level(val id: String, val chapter: Int, val title: String, val kind: Kind, val song: Song? = null, val musicians: Boolean = false, val intro: String)

    val CHAPTERS = listOf("第一章 · 认识编钟", "第二章 · 乐坊复原", "第三章 · 跟谱演奏", "第四章 · 乐师合奏", "第五章 · 大典")

    val ALL = listOf(
        Level("tutorial", 0, "认识编钟", Kind.TUTORIAL, intro = "跟着亮光，学会敲正面、敲侧面和止音。"),
        Level("restore", 1, "乐坊复原", Kind.RESTORE, intro = "先做一个两口钟的小练习。中层有五口钟挂乱了，它们长得一模一样。捏一下钟就能听它的音：把它们从左到右按从低到高换回去。"),
        Level("twinkle", 2, "小星星", Kind.SONG, Songs.TWINKLE, intro = "光点落到哪口钟上，就在它落下的那一刻敲响。金色敲正面，蓝色敲侧面，白色的圈是止音。"),
        Level("jasmine", 2, "茉莉花", Kind.SONG, Songs.JASMINE, intro = "这首里的“3”是 G 钟的侧面：同一口钟正面、侧面交替。"),
        Level("farewell", 3, "送别", Kind.SONG, Songs.FAREWELL, musicians = true, intro = "鼓和磬的乐师来了。这首要用到下层的大钟，长音的结尾记得止音。"),
        Level("joy", 4, "大典 · 欢乐颂", Kind.SONG, Songs.JOY, musicians = true, intro = "大典开始！整首不能漏太多，长音结尾要止音。"),
    )

    fun byId(id: String) = ALL.first { it.id == id }
}

class Progress {
    val best = LinkedHashMap<String, Int>()

    fun record(id: String, stars: Int) {
        if (stars > (best[id] ?: 0)) best[id] = stars
    }

    fun cleared(id: String) = (best[id] ?: 0) > 0
    val stars get() = Levels.ALL.sumOf { best[it.id] ?: 0 }
    val maxStars get() = Levels.ALL.size * 3
    val bronze get() = Levels.ALL.all { cleared(it.id) }
    val silver get() = bronze && stars * 2 >= maxStars
    val gold get() = bronze && stars == maxStars

    /** Levels open in order; free play from the start. */
    fun open(l: Levels.Level) = Levels.ALL.indexOf(l) <= Levels.ALL.indexOfFirst { !cleared(it.id) }.let { if (it < 0) Levels.ALL.size else it }
    val freePlay get() = true
    fun next() = Levels.ALL.firstOrNull { !cleared(it.id) }

    fun save(): String = best.entries.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun load(s: String?) = Progress().apply {
            s?.split(",")?.forEach { e ->
                val p = e.split(":")
                if (p.size == 2 && Levels.ALL.any { it.id == p[0] || p[0] == "jingle" }) p[1].toIntOrNull()?.let { best[p[0]] = it.coerceIn(0, 3) }
            }
        }
    }
}
