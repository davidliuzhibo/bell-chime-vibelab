package com.vrgames.bianzhong.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.bianzhong.core.BellKind
import com.vrgames.bianzhong.core.BellTopics
import com.vrgames.bianzhong.core.Judge
import com.vrgames.bianzhong.core.Practice
import com.vrgames.bianzhong.core.Levels
import com.vrgames.bianzhong.core.Note
import com.vrgames.bianzhong.core.Part
import com.vrgames.bianzhong.core.Performance
import com.vrgames.bianzhong.core.Progress
import com.vrgames.bianzhong.core.Rack
import com.vrgames.bianzhong.core.Restore
import com.vrgames.bianzhong.core.Song
import com.vrgames.bianzhong.core.Songs
import com.vrgames.bianzhong.core.Tutorial
import com.vrgames.bianzhong.render.HallView
import com.vrgames.bianzhong.render.Music
import com.vrgames.bianzhong.render.Sfx
import com.vrgames.bianzhong.render.Sound

/**
 * State the stage, its panels and both windows share (one process). Every pinch on a bell comes to
 * [pinch] with its time; what it means depends on the phase: a lesson step, a bell to swap, a stroke
 * judged against the song, or free playing. Songs run on the uptime clock from [songStart].
 */
object Game {
    private const val PREFS = "bianzhong"
    private const val TAG = "BellGame"

    enum class Phase { CARD, INTRO, TUTORIAL, PRACTICE, RESTORE, COUNTIN, SONG, RESULT, FREE, SHOW, EXHIBIT }

    val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var prefs: android.content.SharedPreferences? = null
    var progress = Progress(); private set
    var level by mutableStateOf<Levels.Level?>(null); private set
    /** The song being played (a level's, or the hidden one). */
    var song by mutableStateOf<Song?>(null); private set
    var tutorial: Tutorial? = null; private set
    var restore: Restore? = null; private set
    var perf: Performance? = null; private set
    var songStart = 0L; private set
    var speed by mutableStateOf(1.0); private set

    var phase by mutableStateOf(Phase.CARD)
    /** The two-bell warm-up before the restore chapter. */
    var practice by mutableStateOf<Practice?>(null); private set
    /** Which topic the bell exhibit shows. */
    var topicIndex by mutableStateOf(0); private set
    val topic get() = BellTopics.ALL[topicIndex]
    /** The narration clip now playing, or null. */
    var speaking by mutableStateOf<String?>(null); private set
    private var narrator: android.media.MediaPlayer? = null
    private var appContext: android.content.Context? = null
    /** Strokes accept the right bell at either part; on by default, off for players who want it strict. */
    var partAssist by mutableStateOf(true)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var lastJudge by mutableStateOf<Judge?>(null)
    var combo by mutableIntStateOf(0)
    var bestCombo = 0
    var selected by mutableStateOf<Int?>(null)
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    /** The pinch latency correction (ms, added to the song's clock). */
    var calibMs by mutableIntStateOf(0); private set
    var trophiesBefore = 0
    var resultStars = 0
    var resultAccuracy = 0.0
    var music: Music? = null
    var sound: Sound? = null
    var view: HallView? = null

    /** Free play's recording: (ms from the first stroke, bell, part). */
    val recording = ArrayList<Triple<Long, Int, Part>>()
    var recordOn by mutableStateOf(false); private set
    private var recordStart = 0L

    /** Notes played by themselves (a demo, a replay, the gold show), from [autoStart]. */
    private var auto: List<Note> = emptyList()
    private var autoAt = 0
    private var autoStart = 0L
    private var autoMusicians = false
    private var autoEnd: (() -> Unit)? = null
    private var lastBeat = -1

    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            calibMs = p.getInt("calib", 0)
            progress = Progress.load(p.getString("progress", null))
            p.getString("recording", null)?.split(";")?.forEach { e ->
                val f = e.split(",")
                if (f.size == 3) runCatching { recording += Triple(f[0].toLong(), f[1].toInt(), Part.valueOf(f[2])) }
            }
        }
        AutoPilot.register(context)
    }

    val trophies get() = progress.let { if (it.gold) 3 else if (it.silver) 2 else if (it.bronze) 1 else 0 }

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    fun changed() {
        version++
    }

    fun start() {
        open(progress.next() ?: Levels.ALL.last())
    }

    /** Shows a level's intro card (its first step waits for 开始). */
    fun open(l: Levels.Level) {
        if (!progress.open(l)) {
            say("这一关还没开放：先完成前面的。")
            return
        }
        stopAuto()
        level = l
        song = l.song
        speed = 1.0
        selected = null
        view?.lookAlike(emptySet())
        resetHang()
        phase = Phase.INTRO
        version++
    }

    /** The hidden tune (silver trophy). */
    fun openHidden() {
        if (!progress.silver && !AutoPilot.debug) return
        stopAuto()
        level = null
        song = Songs.JINGLE
        speed = 1.0
        phase = Phase.INTRO
        version++
    }

    /** From an intro card: begin the level (or the hidden tune), [fast] for the 高手谱. */
    fun begin(fast: Boolean = false) {
        stopAuto()
        val l = level
        combo = 0
        bestCombo = 0
        lastJudge = null
        when (l?.kind) {
            Levels.Kind.TUTORIAL -> {
                tutorial = Tutorial()
                phase = Phase.TUTORIAL
                say(tutorial!!.current!!.say)
            }
            Levels.Kind.RESTORE -> {
                val r = Restore()
                restore = r
                view?.lookAlike(r.goal.toSet())
                view?.clearPitchBars()
                if (progress.cleared(l.id)) startRestore() else {
                    // Two bells of the five, in the row's middle, the others hanging where they belong.
                    val p = Practice(r.goal[1], r.goal[2])
                    practice = p
                    hangPractice()
                    phase = Phase.PRACTICE
                    say(p.say)
                }
            }
            else -> {
                val s = song ?: return
                speed = if (fast) 1.3 else 1.0
                perf = Performance(s, speed)
                songStart = SystemClock.uptimeMillis() + (COUNT_BEATS * s.beat / speed * 1000).toLong()
                lastBeat = -COUNT_BEATS - 1
                phase = Phase.COUNTIN
                say(if (fast) "高手谱：快三成！" else "准备……")
            }
        }
        version++
    }

    private fun startRestore() {
        practice = null
        view?.clearPitchBars()
        hangRestore()
        phase = Phase.RESTORE
        say("捏一下钟听它的音，金色柱子会记下音高。再捏另一口，两口就换位置。要让柱子从左到右一级比一级高。")
    }

    private fun hangPractice() {
        val p = practice ?: return
        val v = view ?: return
        val r = restore ?: return
        r.goal.forEachIndexed { k, id -> v.hangAt(id, Rack.tierOf(1).first { it.slot == r.slots[k] }) }
        val a = r.slots[1]
        val b = r.slots[2]
        v.hangAt(p.order[0], Rack.tierOf(1).first { it.slot == a })
        v.hangAt(p.order[1], Rack.tierOf(1).first { it.slot == b })
    }

    // ---- The bell exhibit ----

    fun openExhibit() {
        stopAuto()
        level = null
        song = null
        phase = Phase.EXHIBIT
        topicIndex = 0
        view?.showExhibit(true, topic.kind)
        speak(topic.id)
        version++
    }

    fun showTopic(i: Int) {
        topicIndex = (i + BellTopics.ALL.size) % BellTopics.ALL.size
        view?.showExhibit(true, topic.kind)
        speak(topic.id)
        version++
    }

    fun closeExhibit() {
        stopNarration()
        view?.showExhibit(false, null)
        toCard()
    }

    /** A bell on the exhibit stand was pinched: sound it, and turn to its page. */
    fun pinchExhibit(kind: BellKind, part: Part, at: Long) {
        val b = Rack.BELLS.first { Rack.kind(it) == kind && (kind != BellKind.YONG || it.tier == 1) }
        sound?.strike(b, part, at)
        view?.exhibitStruck(kind)
        val i = BellTopics.ALL.indexOfFirst { it.kind == kind }
        if (i >= 0 && i != topicIndex) showTopic(i)
    }

    fun speak(id: String) {
        stopNarration()
        val c = appContext ?: return
        runCatching {
            val fd = c.assets.openFd("narration/$id.ogg")
            narrator = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopNarration() }
                prepare()
                start()
            }
            speaking = id
            music?.ducked = true
        }.onFailure { android.util.Log.i("BellStage", "narration $id: ${it.message}") }
    }

    fun stopNarration() {
        runCatching { narrator?.stop() }
        runCatching { narrator?.release() }
        narrator = null
        speaking = null
        music?.ducked = false
    }

    // ---- Pinches ----

    /** A pinch on bell [id] at [part], at uptime [at] (ms). */
    fun pinch(id: Int, part: Part, at: Long) {
        val b = Rack.BELLS.getOrNull(id) ?: return
        val s = sound
        val now = SystemClock.uptimeMillis()
        when (phase) {
            Phase.TUTORIAL -> {
                val t = tutorial ?: return
                val step = t.current ?: return
                val ringing = s?.ringing(b, now) ?: true
                val ok = t.pinch(b, part, ringing)
                if (ok && step.damp) {
                    s?.damp(b); view?.damped(b)
                } else {
                    s?.strike(b, part, now); view?.struck(b, part)
                }
                if (!ok) say(if (step.bell.id != b.id) "不是这一口：看发亮的那口钟。" else if (step.damp) "要趁它还在响的时候捏。" else "这口钟对了，换成${step.part.label}试试。")
                else if (t.done) finish(t.stars(), 1.0) else say(t.current!!.say)
            }
            Phase.PRACTICE -> {
                val p = practice ?: return
                s?.strike(b, Part.MIDDLE, now)
                view?.struck(b, Part.MIDDLE, false)
                if (id in p.order) view?.showPitchBar(b)
                if (!p.pinch(id)) {
                    say("捏发亮的那一口钟。")
                    return
                }
                selected = if (p.step == Practice.Step.PICK_HIGH) id else null
                if (p.done) {
                    hangPractice()
                    s?.play(Sfx.SWAP)
                    say(p.say)
                    main.postDelayed({ if (phase == Phase.PRACTICE) startRestore() }, 3500)
                } else say(p.say)
            }
            Phase.RESTORE -> {
                val r = restore ?: return
                s?.strike(b, Part.MIDDLE, now)
                view?.struck(b, Part.MIDDLE, false)
                if (id in r.order) view?.showPitchBar(b)
                val i = r.order.indexOf(id)
                if (i < 0) {
                    say("这口钟没挂乱。要换的是中层发亮的五口。")
                    return
                }
                val sel = selected
                when {
                    sel == null -> selected = id
                    sel == id -> selected = null
                    else -> {
                        val j = r.order.indexOf(sel)
                        r.swap(i, j)
                        hangRestore()
                        s?.play(Sfx.SWAP)
                        selected = null
                        if (r.solved) finish(r.stars(), 1.0)
                    }
                }
            }
            Phase.SONG, Phase.COUNTIN -> {
                val p = perf ?: return
                val t = (at - songStart + calibMs) / 1000.0
                when (val h = p.hit(t, b, part, partAssist)) {
                    is Performance.Hit.Struck -> {
                        val played = p.notes[h.index].part
                        s?.strike(b, played, now); view?.struck(b, played)
                        judged(h.judge)
                    }
                    is Performance.Hit.Damped -> {
                        s?.damp(b); view?.damped(b)
                        judged(h.judge)
                    }
                    is Performance.Hit.Stray -> {
                        s?.strike(b, part, now); view?.struck(b, part, false)
                        combo = 0
                        h.why?.let { lastJudge = null; say(it) }
                    }
                }
            }
            Phase.FREE -> {
                if (recordOn) {
                    if (recording.isEmpty()) recordStart = at
                    recording += Triple(at - recordStart, id, part)
                }
                s?.strike(b, part, now); view?.struck(b, part)
            }
            else -> {
                s?.strike(b, part, now); view?.struck(b, part, false)
            }
        }
        version++
    }

    private fun judged(j: Judge) {
        lastJudge = j
        if (j == Judge.MISS) combo = 0 else {
            combo++
            if (combo > bestCombo) bestCombo = combo
        }
    }

    /** Puts the restore chapter's bells where [Restore.order] says. */
    private fun hangRestore() {
        val r = restore ?: return
        val v = view ?: return
        r.order.forEachIndexed { k, id -> v.hangAt(id, Rack.tierOf(1).first { it.slot == r.slots[k] }) }
    }

    private fun resetHang() {
        val v = view ?: return
        for (b in Rack.tierOf(1)) v.hangAt(b.id, b)
    }

    fun hint() {
        when (phase) {
            Phase.RESTORE -> restore?.hint()?.let { (i, j) ->
                val r = restore!!
                selected = r.order[i]
                say("提示：发绿光的这口钟，应该挂到从左数第 ${r.slots[j] + 1} 个位置。")
            }
            Phase.TUTORIAL -> tutorial?.current?.let { say("提示：${it.say}") }
            else -> say("跟着光点：金色敲正面，蓝色敲侧面，白圈是止音。")
        }
        version++
    }

    /** Plays the restore chapter's five bells in the right order, as a model to compare. */
    fun demoScale() {
        val r = restore ?: return
        val bells = r.goal.map { Rack.BELLS[it] }
        playAuto(bells.mapIndexed { k, b -> Note(k * 0.6, b, Part.MIDDLE) }, musicians = false) {}
    }

    // ---- Frame ----

    /** Advances the clock-driven parts (main thread, every frame). */
    fun tick() {
        val now = SystemClock.uptimeMillis()
        runAuto(now)
        if (phase != Phase.COUNTIN && phase != Phase.SONG) return
        val p = perf ?: return
        val s = song ?: return
        val t = (now - songStart) / 1000.0
        val beatLen = s.beat / speed
        val beat = kotlin.math.floor(t / beatLen).toInt()
        if (beat != lastBeat) {
            lastBeat = beat
            if (beat < 0) sound?.play(Sfx.COUNT)
            else if (level?.musicians == true && t < p.length) {
                if (beat % 2 == 0) { sound?.play(Sfx.DRUM, DRUM_AT); view?.musicianBeat(true) } else { sound?.play(Sfx.QING, QING_AT); view?.musicianBeat(false) }
            }
        }
        if (phase == Phase.COUNTIN && t >= 0) {
            phase = Phase.SONG
            say(null)
        }
        val missed = p.advance(t + calibMs / 1000.0)
        if (missed.isNotEmpty()) {
            lastJudge = Judge.MISS
            combo = 0
            version++
        }
        if (p.done(t)) {
            val acc = p.accuracy()
            Log.i(TAG, "song ${s.id} done acc=${"%.3f".format(acc)} perfect=${p.count(Judge.PERFECT)} good=${p.count(Judge.GOOD) + p.count(Judge.LATE)} miss=${p.count(Judge.MISS)} strays=${p.strays}")
            finish(Performance.stars(acc), acc)
        }
    }

    /** The notes to light now: the coming ones within the lead, for the view. */
    fun upcoming(): Pair<List<Note>, Double> {
        val p = perf ?: return emptyList<Note>() to 0.0
        if (phase != Phase.SONG && phase != Phase.COUNTIN) return emptyList<Note>() to 0.0
        val t = (SystemClock.uptimeMillis() - songStart) / 1000.0
        return p.notes.filterIndexed { i, n -> p.judged[i] == null && n.time - t < HallView.LEAD && n.time - t > -0.3 }.take(8) to t
    }

    private fun finish(stars: Int, accuracy: Double) {
        trophiesBefore = trophies
        resultStars = stars
        resultAccuracy = accuracy
        val l = level
        if (stars > 0) {
            if (l != null) progress.record(l.id, stars) else progress.record("jingle", stars)
            save()
        }
        stopAuto()
        selected = null
        view?.lookAlike(emptySet())
        if (phase == Phase.RESTORE) resetHang()
        phase = Phase.RESULT
        sound?.play(if (stars > 0) Sfx.FANFARE else Sfx.WRONG)
        if (trophies > trophiesBefore) main.postDelayed({ sound?.play(Sfx.GONG) }, 900)
        if (stars > 0) view?.celebrate()
        Log.i(TAG, "finish ${l?.id ?: song?.id} stars=$stars")
        version++
    }

    fun next() {
        val n = progress.next()
        if (n != null) open(n) else {
            say("五章都完成了！可以在「乐谱」里重奏任何一首，把星星拿满。")
            phase = Phase.RESULT
            version++
        }
    }

    // ---- Playing by itself ----

    /** Listen first: the level's song played by itself (no score). */
    fun listen() {
        val s = song ?: return
        playAuto(s.notes, level?.musicians == true) { phase = Phase.INTRO; version++ }
        phase = Phase.SHOW
        version++
    }

    /** The gold trophy's 大典: the joy, then the jingle, played by the rack and the musicians. */
    fun grandShow() {
        if (!progress.gold && !AutoPilot.debug) return
        sound?.play(Sfx.GONG)
        say("大典开始！帷幔拉开，乐师就位……")
        val joy = Songs.JOY.notes
        val gap = Songs.JOY.length + 2.0
        val all = joy.map { it.copy(time = it.time + 2.0) } + Songs.JINGLE.notes.map { it.copy(time = it.time * 1.0 + gap + 2.0) }
        level = null
        song = Songs.JOY
        playAuto(all, musicians = true) {
            phase = Phase.RESULT
            say("大典礼成！")
            view?.celebrate()
            version++
        }
        phase = Phase.SHOW
        version++
    }

    fun free() {
        if (!progress.freePlay && !AutoPilot.debug) return
        stopAuto()
        level = null
        song = null
        phase = Phase.FREE
        say("自由演奏：随便敲。点「录音」把一段演奏录下来，再点「回放」听。")
        version++
    }

    fun toggleRecord() {
        if (recordOn) {
            recordOn = false
            saveRecording()
            say("录好了：${recording.size} 个音。点「回放」听一听。")
        } else {
            recording.clear()
            recordOn = true
            say("开始录音：现在敲的每个音都会录下来。")
        }
        version++
    }

    fun replay() {
        if (recording.isEmpty()) {
            say("还没有录音。")
            return
        }
        recordOn = false
        playAuto(recording.map { (ms, id, part) -> Note(ms / 1000.0 + 0.5, Rack.BELLS[id], part) }, musicians = false) {
            say("回放完了。")
        }
    }

    private fun playAuto(notes: List<Note>, musicians: Boolean, end: () -> Unit) {
        auto = notes.sortedBy { it.time }
        autoAt = 0
        autoStart = SystemClock.uptimeMillis()
        autoMusicians = musicians
        autoEnd = end
        lastBeat = -1
    }

    private fun stopAuto() {
        auto = emptyList()
        autoEnd = null
    }

    private fun runAuto(now: Long) {
        if (auto.isEmpty()) return
        val t = (now - autoStart) / 1000.0
        while (autoAt < auto.size && auto[autoAt].time <= t) {
            val n = auto[autoAt++]
            if (n.damp) { sound?.damp(n.bell); view?.damped(n.bell) } else { sound?.strike(n.bell, n.part, now); view?.struck(n.bell, n.part) }
        }
        if (autoMusicians) {
            val beat = kotlin.math.floor(t / Songs.JOY.beat).toInt()
            if (beat != lastBeat && t < (auto.lastOrNull()?.time ?: 0.0)) {
                lastBeat = beat
                if (beat % 2 == 0) { sound?.play(Sfx.DRUM, DRUM_AT); view?.musicianBeat(true) } else { sound?.play(Sfx.QING, QING_AT); view?.musicianBeat(false) }
            }
        }
        if (autoAt >= auto.size && t > (auto.lastOrNull()?.time ?: 0.0) + 1.5) {
            val e = autoEnd
            auto = emptyList()
            autoEnd = null
            e?.invoke()
        }
    }

    fun toCard() {
        stopNarration()
        view?.showExhibit(false, null)
        view?.clearPitchBars()
        practice = null
        stopAuto()
        phase = Phase.CARD
        version++
    }

    // ---- Settings ----

    fun calibrate(by: Int) {
        calibMs = (calibMs + by).coerceIn(-200, 200)
        prefs?.edit()?.putInt("calib", calibMs)?.apply()
        version++
    }

    private fun save() {
        prefs?.edit()?.putString("progress", progress.save())?.apply()
    }

    private fun saveRecording() {
        prefs?.edit()?.putString("recording", recording.joinToString(";") { "${it.first},${it.second},${it.third.name}" })?.apply()
    }

    fun restart() {
        stopAuto()
        progress = Progress()
        save()
        level = null
        song = null
        phase = Phase.CARD
        note = null
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(stars: Int) {
        for (l in Levels.ALL) progress.best[l.id] = stars
        save()
        version++
    }

    /** Debug: plays the current song perfectly by itself, as judged pinches. */
    fun autoplay() {
        val p = perf ?: return
        p.notes.forEach { n ->
            val at = songStart + (n.time * 1000).toLong() - calibMs
            main.postAtTime({ if (perf === p) pinch(n.bell.id, n.part, at) }, at)
        }
    }

    const val COUNT_BEATS = 4
    private val DRUM_AT = com.vrgames.bianzhong.core.Vec3(-1.7, 0.8, -0.3)
    private val QING_AT = com.vrgames.bianzhong.core.Vec3(1.7, 0.8, -0.3)

    // Debug hooks (AutoPilot).
    var debugOpen by mutableStateOf<String?>(null)
    var debugLook by mutableStateOf<Pair<Double, Double>?>(null)
    var debugView by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
}
