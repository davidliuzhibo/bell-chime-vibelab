package com.vrgames.bianzhong.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.bianzhong.GUIDE_ID
import com.vrgames.bianzhong.SCORE_ID
import com.vrgames.bianzhong.core.Part
import com.vrgames.bianzhong.game.AutoPilot
import com.vrgames.bianzhong.game.Game
import com.vrgames.bianzhong.render.HallView
import com.vrgames.bianzhong.render.Music
import com.vrgames.bianzhong.render.Sound
import com.vrgames.bianzhong.render.setPos
import com.vrgames.bianzhong.render.shared
import com.vrgames.bianzhong.render.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The music hall around the seated player (a full-immersion Stage). Every pinch on a bell counts
 * at the moment the fingers close (the raw finger-down event, with its own time), so the rhythm
 * judges the pinch itself and not when a gesture was recognized.
 */
@Composable
fun HallStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<HallView?>(null) }
    val seat = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(SCORE_ID)
            Game.closeWindow(GUIDE_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
            // Home pressed mid-narration: the voice stops with the game (device test 2026-11-12). ON_STOP, as a window of
            // the game's own opening also pauses the stage.
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) Game.stopNarration()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 40) 9000 else 6000)
        if (Game.note == n) Game.note = null
    }
    // Panels: one explanation at a time.
    LaunchedEffect(Game.phase, Game.menuOpen, Game.note, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val ph = Game.phase
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_HUD, ph == Game.Phase.TUTORIAL || ph == Game.Phase.RESTORE || ph == Game.Phase.COUNTIN || ph == Game.Phase.SONG || ph == Game.Phase.SHOW)
        show(PANEL_FREE, ph == Game.Phase.FREE)
        show(PANEL_EXHIBIT, ph == Game.Phase.EXHIBIT)
        view?.showStuds(ph == Game.Phase.FREE || ph == Game.Phase.TUTORIAL)
        // While a card is up in front of the rack, a pinch is for its buttons, never for a bell behind it.
        view?.pinchable(ph != Game.Phase.CARD && ph != Game.Phase.INTRO && ph != Game.Phase.RESULT)
        show(PANEL_NOTE, Game.note != null)
        show(PANEL_MENU, true)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "score" -> if (Game.window != SCORE_ID) toggleWindow(SCORE_ID)
            "guide" -> if (Game.window != GUIDE_ID) toggleWindow(GUIDE_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                val v = view
                if (v != null) for (i in infos) {
                    if (!i.isDownEvent()) continue
                    val id = v.hitOf(i.targetedEntity) ?: continue
                    val f = id.split(":")
                    if (f.size == 2 && f[0] == "exhibit") {
                        val at = i.uptimeMillis
                        main.post { Game.pinchExhibit(com.vrgames.bianzhong.core.BellKind.valueOf(f[1]), Part.MIDDLE, at) }
                    } else if (f.size == 3 && f[0] == "bell") {
                        val at = i.uptimeMillis
                        Log.i(INPUT_TAG, "pinch $id at $at")
                        main.post { Game.pinch(f[1].toInt(), if (f[2] == "s") Part.SIDE else Part.MIDDLE, at) }
                    }
                }
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_FREE) { FreePanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_EXHIBIT) { ExhibitPanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE2B8).toColor4(), 700f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-50f, -25f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
                ibl = runCatching { TextureResource.load("textures/ibl_hall.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }
                    .onFailure { Log.i(TAG, "ibl: ${it.message}") }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS + PANEL_FRAMES.values.map { "panel_$it" }) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            val v = HallView(models, tex)
            ibl?.let { v.useIbl(it) }
            seat.root = v.root
            content.addEntity(v.root)
            attach(v, attachments, panels)
            Game.view = v
            Game.sound = Sound(v.root, v).also { it.load() }
            view = v
            AutoPilot.ready()
            var lastOutline = ""
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation)
                Game.tick()
                val (up, now) = Game.upcoming()
                v.notes(up, now)
                // Outlines: the lesson's next bell, the restore chapter's selection.
                val targets = HashMap<Int, Part?>()
                if (Game.phase == Game.Phase.TUTORIAL) Game.tutorial?.current?.let { targets[it.bell.id] = if (it.damp) null else it.part }
                if (Game.phase == Game.Phase.RESTORE) Game.restore?.let { r -> r.order.forEach { targets[it] = Part.MIDDLE } }
                if (Game.phase == Game.Phase.PRACTICE) Game.practice?.wanted?.let { targets[it] = Part.MIDDLE }
                val sel = if (Game.phase == Game.Phase.RESTORE || Game.phase == Game.Phase.PRACTICE) Game.selected else null
                val key = targets.entries.joinToString { "${it.key}${it.value}" } + "|$sel"
                if (key != lastOutline) {
                    lastOutline = key
                    v.outline(targets, sel)
                }
                v.frame(dt)
            }
        },
    )
}

/** Textures loaded if present (see docs/素材来源.md). */
private val TEXTURES = listOf("hall", "floor")
private val MODELS = listOf("sky", "niu", "yong", "bo", "pillar", "rack", "drum", "qing", "mallet", "musician")
/** Thick bronze plaques behind the card panels (tools/panel3d); [PANEL_SIZES] are their insides. */
private val PANEL_FRAMES = mapOf(PANEL_CARD to "card", PANEL_INTRO to "intro", PANEL_RESULT to "result", PANEL_EXHIBIT to "exhibit", PANEL_FREE to "free")

private fun attach(v: HallView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        v.root.addChild(e)
        e.setPos(pos)
        PANEL_FRAMES[id]?.let { name -> v.models["panel_$name"]?.let { frame -> frame.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(frame); v.lightUp(frame) } }
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Hall frame (the player's feet at the origin, facing -z). Cards at eye height in front of the
    // middle row; the hud low in front, tilted up, clear of the bells; notes above the middle row.
    put(PANEL_CARD, Vector3(0f, 1.18f, -0.85f), 0f)
    put(PANEL_INTRO, Vector3(0f, 1.18f, -0.85f), 0f)
    put(PANEL_RESULT, Vector3(0f, 1.18f, -0.85f), 0f)
    put(PANEL_HUD, Vector3(0f, 0.72f, -0.62f), -38f)
    put(PANEL_FREE, Vector3(0f, 0.72f, -0.62f), -38f)
    put(PANEL_NOTE, Vector3(0f, 1.66f, -1.05f), 8f)
    put(PANEL_EXHIBIT, Vector3(0f, 1.28f, -0.92f), 0f)
    put(PANEL_MENU, Vector3(-0.42f, 0.7f, -0.5f), -40f, 30f)
}

/**
 * Where the player sits and which way the hall faces: turned once to the mean heading of the first
 * second of tracking, and again on 「把编钟转到我正前方」.
 */
private class Seat {
    var root: Entity? = null
    private var placed = false
    private val headings = ArrayList<Float>()
    private var started = 0L

    fun again() {
        placed = false
        headings.clear()
    }

    private var placedAt = Vector3(0f, 0f, 0f)
    private var facing = 0f

    fun frame(eye: Vector3?, rot: Quat?) {
        // Debug only: turn and lift the hall so the emulator's standing camera sees it as seated.
        val view = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (view != null && view.size >= 2) {
            root?.setPos(Vector3(placedAt.x, view[1], placedAt.z))
            root?.transform()?.eulerAngles = EulerAngles(0f, facing + view[0], 0f)
            return
        }
        val look = Game.debugLook
        if (look != null) {
            root?.transform()?.eulerAngles = EulerAngles(0f, look.first.toFloat(), 0f)
            return
        }
        if (placed || rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        if (hypot(f.x, f.z) < 0.2f) return
        if (headings.isEmpty()) started = android.os.SystemClock.uptimeMillis()
        headings += Math.toDegrees(atan2(-f.x, -f.z).toDouble()).toFloat()
        if (android.os.SystemClock.uptimeMillis() - started < 1000) return
        val s = headings.sumOf { sin(Math.toRadians(it.toDouble())) }
        val c = headings.sumOf { cos(Math.toRadians(it.toDouble())) }
        facing = Math.toDegrees(atan2(s, c)).toFloat()
        placed = true
        placedAt = Vector3(eye.x, 0f, eye.z)
        root?.let {
            it.setPos(placedAt)
            it.transform().eulerAngles = EulerAngles(0f, facing, 0f)
        }
        Log.i(TAG, "facing ${headings.size} samples, mean $facing, eye $eye")
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "BellStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "BellInput"
