package com.vrgames.bianzhong.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.bianzhong.core.Bell
import com.vrgames.bianzhong.core.Part
import com.vrgames.bianzhong.core.Rack
import com.vrgames.bianzhong.core.Vec3

/** Effects besides the bells, all made by tools/sfx_synth.py (see docs/素材来源.md). */
enum class Sfx(val file: String, val volume: Float) {
    /** The count-in before a song: a wooden knock. */
    COUNT("count", 0.7f),
    DRUM("drum", 0.8f),
    /** The stone chime (磬) of the accompanist. */
    QING("qing", 0.6f),
    /** A palm stopping a bell. */
    DAMP("damp", 0.6f),
    WRONG("wrong", 0.4f),
    FANFARE("fanfare", 0.8f),
    GONG("gong", 0.9f),
    SWAP("swap", 0.6f),
}

/**
 * Spatial sound: every bell's two tones come from where that bell hangs (an emitter per tone with
 * a prepared player, restarted on each stroke), and the other effects from where they happen.
 * Main thread only.
 */
class Sound(private val parent: Entity, private val view: HallView) {
    private val emitters = HashMap<String, Entity>()
    private val players = HashMap<String, AudioPlayerController>()
    private val ringingUntil = HashMap<Int, Long>()
    var enabled = true

    fun load() {
        for (b in Rack.BELLS) for (p in Part.entries) {
            val key = key(b, p)
            prepare(key, "bell_${b.id}_${if (p == Part.MIDDLE) "m" else "s"}", 1f)?.let { e -> e.setPos(view.strikePoint(b, p)) }
        }
        for (s in Sfx.entries) prepare(s.name, s.file, s.volume)
    }

    private fun prepare(key: String, file: String, volume: Float): Entity? = runCatching {
        val res = AudioResource.load(file, "asset://audio/$file.wav", LoadType.FROM_ASSETS)
        val e = Entity()
        e.components.set(ObjectAudioComponent(volume))
        parent.addChild(e)
        val c = e.prepareAudio(res)
        if (!c.valid) return@runCatching null
        emitters[key] = e
        players[key] = c
        e
    }.onFailure { android.util.Log.w(TAG, "sound $file: ${it.message}") }.getOrNull()

    private fun key(b: Bell, p: Part) = "b${b.id}${p.name}"

    private fun restart(key: String) {
        if (!enabled) return
        val c = players[key] ?: return
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    /** Strikes [b] at [part]; the bell then counts as ringing for a while (it can be damped). */
    fun strike(b: Bell, part: Part, now: Long) {
        restart(key(b, part))
        ringingUntil[b.id] = now + RING_MS
    }

    fun ringing(b: Bell, now: Long) = (ringingUntil[b.id] ?: 0) > now

    /** Stops [b]'s tones (a palm on the bell). */
    fun damp(b: Bell) {
        for (p in Part.entries) players[key(b, p)]?.let { c -> runCatching { if (c.isPlaying()) c.stop() } }
        ringingUntil.remove(b.id)
        play(Sfx.DAMP, view.strikePoint(b, Part.MIDDLE))
    }

    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 1.2, -1.0)) {
        emitters[s.name]?.setPos(at)
        restart(s.name)
    }

    companion object {
        private const val TAG = "BellSound"
        /** How long a struck bell is heard (and can be damped). */
        const val RING_MS = 2000L
    }
}
