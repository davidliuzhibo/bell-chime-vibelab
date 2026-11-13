package com.vrgames.bianzhong.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.bianzhong.core.Bell
import com.vrgames.bianzhong.core.BellKind
import com.vrgames.bianzhong.core.Note
import com.vrgames.bianzhong.core.Part
import com.vrgames.bianzhong.core.Rack
import com.vrgames.bianzhong.core.Vec3
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The music hall around the seated player (full immersion). Frame: the player's feet at the origin,
 * facing -z. The rack's three tiers stand in arcs in front (the middle row at eye height, the big
 * bells low, the small ones high), held up by bronze swordsmen and lacquered posts; a drummer and a
 * stone-chime player sit to either side. Each bell is three pinch targets: its face ("bell:<id>:m")
 * and its two sides ("bell:<id>:s"). Strokes swing the bell, flash and show a mallet; coming notes
 * fall as lights onto the bell and the part to strike.
 */
class HallView(val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    private val hits = HashMap<Entity, String>()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val sparkle = Sparks("sparkle", 200, sphere, glow(color(1f, 0.82f, 0.45f, 1f)).shared()).also { root.addChild(it.entity) }
    private val holders = HashMap<Int, Entity>()
    private val swing = HashMap<Int, Double>()
    private val shells = HashMap<Int, Entity>()
    private val iblSource = Entity().also { root.addChild(it) }
    private var clock = 0.0

    private val lacquer = pbr(color(0.05f, 0.035f, 0.03f), roughness = 0.55f).shared()
    private val redTrim = pbr(color(0.62f, 0.1f, 0.07f), roughness = 0.4f).shared()
    private val bronze = pbr(color(0.45f, 0.5f, 0.38f), metallic = 0.8f, roughness = 0.45f).shared()
    private val floorMat = pbr(color(0.18f, 0.11f, 0.07f), roughness = 0.6f).shared()
    private val goldOrb = unlit(color(1f, 0.72f, 0.18f)).shared()
    private val sideOrb = unlit(color(0.25f, 0.7f, 1f)).shared()
    private val dampOrb = unlit(color(0.95f, 0.95f, 1f, 0.55f), com.pico.spatial.core.ecs.resource.BlendingMode.TRANSPARENT).shared()
    private val haloMat = glow(color(1f, 0.85f, 0.45f, 0.25f)).shared()
    private val targetMat = rim(color(1f, 0.8f, 0.35f, 0.9f)).shared()
    private val sideTargetMat = rim(color(0.4f, 0.8f, 1f, 0.9f)).shared()
    private val selectMat = rim(color(0.6f, 1f, 0.7f, 0.9f)).shared()
    private val orbs = ArrayList<Entity>()
    private val mallets = ArrayList<Pair<Entity, Double>>()
    private var drummer: Entity? = null
    private var chimer: Entity? = null
    private var drumBob = 0.0
    private var chimeBob = 0.0
    private val uniform = HashSet<Int>()
    private val pitchMat = unlit(color(1f, 0.78f, 0.25f)).shared()
    private val studMat = unlit(color(0.75f, 0.58f, 0.25f)).shared()
    private val sideStudMat = unlit(color(0.3f, 0.6f, 0.85f)).shared()
    private val cube = MeshResource.createBox(Vector3(1f, 1f, 1f)).shared()
    private val pitchBars = HashMap<Int, Entity>()
    /** Small dots on each bell's middle and sides; hovering one lights it, so the aimed part is visible. */
    private val studs = ArrayList<Entity>()
    private val exhibit = Entity().apply { enabled = false }
    private val exhibitBells = HashMap<BellKind, Entity>()
    private val exhibitRing = model(MeshResource.createTorus(1f, 0.97f), unlit(color(1f, 0.8f, 0.3f)).shared()).apply { enabled = false }
    private var exhibitSwing = HashMap<BellKind, Double>()

    init {
        build()
    }

    // ---- Layout ----

    /** Where bell [b] hangs: its top's middle (hall frame) and the yaw that turns its face to the player. */
    fun anchor(b: Bell): Pair<Vec3, Double> {
        val n = Rack.tierOf(b.tier).size
        val (r, spread, top) = TIERS[b.tier]
        val az = if (n == 1) 0.0 else -spread + 2 * spread * b.slot / (n - 1)
        val a = Math.toRadians(az)
        return Vec3(r * sin(a), top, -r * cos(a)) to -az
    }

    /** The point to strike on bell [b] at [part] (hall frame), for lights and sounds. */
    fun strikePoint(b: Bell, part: Part): Vec3 {
        val (top, yaw) = anchor(b)
        val h = bellHeight(b)
        val w = h * WIDTH
        val y = Math.toRadians(yaw)
        val face = Vec3(sin(y), 0.0, cos(y))
        val right = Vec3(cos(y), 0.0, -sin(y))
        val c = top + Vec3(0.0, -h * 0.62, 0.0) + face * (w * 0.28)
        return if (part == Part.MIDDLE) c else c + right * (w * 0.38)
    }

    private fun bellHeight(b: Bell) = if (b.id in uniform) Rack.tierOf(1).first { it.slot == 4 }.height else b.height

    private fun build() {
        // Sky (the hall panorama) and floor.
        val skyTex = tex["hall"]
        val sky = models["sky"]?.clone(Entity.CloneOptions(true, true))
        if (sky != null && skyTex != null) {
            sky.setScale(SKY_R / 12f)
            sky.setPos(Vector3(0f, 1.2f, 0f))
            setMaterials(sky, UnlitMaterial.create().apply { setBaseColorTexture(skyTex) })
            root.addChild(sky)
        } else {
            root.addChild(model(sphere, unlit(color(0.12f, 0.07f, 0.05f)).apply { setCullingMode(MaterialCullingMode.FRONT) }).apply { setScale(SKY_R) })
        }
        buildExhibit()
        val floor = tex["floor"]?.let { t ->
            PhysicallyBasedMaterial.create().apply { setBaseColorTexture(t); setEmissiveTexture(t); setEmissiveColor(color(0.3f, 0.3f, 0.3f)); setRoughness(0.55f) }
        } ?: floorMat
        root.addChild(model(MeshResource.createCylinder(0.02f, 3.2f), floor).apply { setPos(Vector3(0f, -0.01f, 0f)) })
        // The rack's beams and posts are one baked model (tools/build_rack.py), a single draw call;
        // the bronze swordsmen hold the lowest beam at both ends.
        models["rack"]?.clone(Entity.CloneOptions(true, true))?.let { root.addChild(it) }
        for (t in 0..2) {
            val (r, spread, top) = TIERS[t]
            if (t == 0) for (side in listOf(-1, 1)) {
                val a = Math.toRadians(side * (spread + 8))
                models["pillar"]?.clone(Entity.CloneOptions(true, true))?.let { man ->
                    man.setScale(top.toFloat() + 0.06f)
                    man.setPos(Vector3((r * sin(a)).toFloat(), 0f, (-r * cos(a)).toFloat()))
                    man.transform().eulerAngles = EulerAngles(0f, -Math.toDegrees(a).toFloat(), 0f)
                    root.addChild(man)
                }
            }
            for (b in Rack.tierOf(t)) buildBell(b)
        }
        // The musicians: a drummer on the left, a stone-chime player on the right.
        drummer = musician(-1)
        chimer = musician(1)
        receive(root)
    }

    private fun musician(side: Int): Entity {
        val a = Math.toRadians(side * 98.0)
        val r = 1.75
        val e = Entity().apply {
            setPos(Vector3((r * sin(a)).toFloat(), 0f, (-r * cos(a)).toFloat()))
            transform().eulerAngles = EulerAngles(0f, -side * 98f, 0f)
        }
        // The AI figure faces away along its z: turned round to face the player.
        models["musician"]?.clone(Entity.CloneOptions(true, true))?.let { it.setScale(0.75f); it.transform().eulerAngles = EulerAngles(0f, 180f, 0f); e.addChild(it) }
        val inst = models[if (side < 0) "drum" else "qing"]?.clone(Entity.CloneOptions(true, true))
        if (inst != null) {
            inst.setScale(if (side < 0) 1.05f else 0.8f)
            inst.setPos(Vector3(0f, 0f, 0.45f))
            // The drum's heads face along x: turned so one faces the player.
            inst.transform().eulerAngles = EulerAngles(0f, if (side < 0) 90f else 180f, 0f)
            e.addChild(inst)
        }
        root.addChild(e)
        return e
    }

    private fun buildBell(b: Bell) {
        // The pitch bar hangs on the holder and is destroyed with it: forget it too, or the next show or clear would touch
        // a destroyed entity (the crash after the restore level on the headset).
        holders[b.id]?.let { old -> hits.keys.removeAll { hitOwnedBy(it, old) }; pitchBars.remove(b.id); old.destroy() }
        val (top, yaw) = anchor(b)
        val h = bellHeight(b).toFloat()
        val w = h * WIDTH.toFloat()
        val holder = Entity().apply {
            setPos(Vector3(top.x.toFloat(), top.y.toFloat(), top.z.toFloat()))
            transform().eulerAngles = EulerAngles(0f, yaw.toFloat(), 0f)
        }
        // A short cord from the beam.
        holder.addChild(model(MeshResource.createCylinder(0.05f, 0.006f), lacquer).apply { setPos(Vector3(0f, 0.01f, 0f)) })
        val body = bellModel(b)
        val swingRoot = Entity()
        if (body != null) {
            // The model stands on its rim, its shank up: hang it so the shank's top meets the cord.
            body.setScale(h)
            body.setPos(Vector3(0f, -h, 0f))
            swingRoot.addChild(body)
        } else {
            swingRoot.addChild(model(MeshResource.createBox(Vector3(w, h * 0.8f, w * 0.6f), 0.02f), bronze).apply { setPos(Vector3(0f, -h * 0.6f, 0f)) })
        }
        holder.addChild(swingRoot)
        // Pinch targets, wider and taller than the bell itself so a gaze slightly off still lands:
        // the middle part and the two sides, each with a dot that lights when it is aimed at.
        fun target(id: String, x: Float, width: Float, side: Boolean) = Entity().also {
            it.setPos(Vector3(x, -h * 0.6f, w * 0.2f))
            it.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(width, h * 1.08f, w * 0.8f))), PhysicsMaterialResource()))
            it.components.set(InteractableComponent())
            it.components.set(HoverEffectComponent())
            hits[it] = id
            swingRoot.addChild(it)
            val stud = model(sphere, if (side) sideStudMat else studMat).apply {
                setPos(Vector3(if (side) (if (x < 0) w * 0.1f else -w * 0.1f) else 0f, 0f, w * 0.26f))
                setScale(if (side) 0.0075f else 0.009f)
                enabled = studsOn
            }
            it.addChild(stud)
            studs += stud
        }
        target("bell:${b.id}:m", 0f, w * 0.44f, false)
        target("bell:${b.id}:s", -w * 0.42f, w * 0.40f, true)
        target("bell:${b.id}:s", w * 0.42f, w * 0.40f, true)
        root.addChild(holder)
        holders[b.id] = holder
        swing[b.id] = 0.0
        receive(holder)
    }

    /** The model for a bell: which kind it is on the original rack. Stands on its rim, height 1. */
    private fun bellModel(b: Bell): Entity? =
        (models[Rack.kind(b).model] ?: models["yong"])?.clone(Entity.CloneOptions(true, true))

    private var studsOn = false

    /** The aim dots matter when the part struck matters and no light is falling on it. */
    fun showStuds(on: Boolean) {
        if (on == studsOn) return
        studsOn = on
        studs.forEach { it.enabled = on }
    }

    // ---- Pitch bars (the ladder that makes hearing visible) ----

    /** Under a bell just heard: a gold bar whose height is its pitch. It hangs with the bell when swapped. */
    fun showPitchBar(b: Bell) {
        val holder = holders[b.id] ?: return
        val bar = pitchBars.getOrPut(b.id) { model(cube, pitchMat).also { holder.addChild(it) } }
        val height = (0.05 + (b.main - 67) * 0.012).toFloat().coerceIn(0.03f, 0.24f)
        bar.transform().setScaleVector(Vector3(0.05f, height, 0.05f))
        bar.setPos(Vector3(0f, PITCH_BAR_FOOT + height / 2, 0.14f))
        bar.enabled = true
    }

    fun clearPitchBars() = pitchBars.values.forEach { it.enabled = false }

    // ---- The exhibit: the three kinds of bell on stands, in front of the player ----

    private fun buildExhibit() {
        root.addChild(exhibit)
        exhibit.addChild(exhibitRing)
        var x = -0.42f
        for (kind in BellKind.values()) {
            val h = when (kind) { BellKind.NIU -> 0.26f; BellKind.YONG -> 0.36f; BellKind.BO -> 0.32f }
            val stand = Entity().apply { setPos(Vector3(x, EXHIBIT_FOOT, EXHIBIT_Z)) }
            stand.addChild(model(MeshResource.createCylinder(EXHIBIT_FOOT, 0.11f), lacquer).apply { setPos(Vector3(0f, -EXHIBIT_FOOT / 2, 0f)) })
            stand.addChild(model(MeshResource.createCylinder(0.012f, 0.13f), redTrim).apply { setPos(Vector3(0f, 0f, 0f)) })
            val swinging = Entity().apply { setPos(Vector3(0f, 0.01f, 0f)) }
            models[kind.model]?.clone(Entity.CloneOptions(true, true))?.let { it.setScale(h); swinging.addChild(it) }
            swinging.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(h * 0.9f, h * 1.1f, h * 0.7f)).offsetByTranslation(Vector3(0f, h * 0.5f, 0f))), PhysicsMaterialResource()))
            swinging.components.set(InteractableComponent())
            swinging.components.set(HoverEffectComponent())
            hits[swinging] = "exhibit:${kind.name}"
            stand.addChild(swinging)
            exhibit.addChild(stand)
            exhibitBells[kind] = swinging
            x += 0.42f
        }
    }

    fun showExhibit(on: Boolean, selected: BellKind?) {
        if (exhibit.enabled != on) exhibit.enabled = on
        val stand = selected?.let { exhibitBells[it] }?.getParent()
        exhibitRing.enabled = on && stand != null
        if (stand != null) {
            val p = stand.components[com.pico.spatial.core.ecs.TransformComponent::class.java]!!.position
            exhibitRing.setPos(Vector3(p.x, EXHIBIT_FOOT + 0.014f, p.z))
            exhibitRing.setScale(0.15f)
        }
    }

    fun exhibitStruck(kind: BellKind) { exhibitSwing[kind] = 1.0 }

    private fun hitOwnedBy(e: Entity, owner: Entity): Boolean {
        var cur: Entity? = e
        while (cur != null) {
            if (cur === owner) return true
            cur = cur.getParent()
        }
        return false
    }

    private var pinchOn = true

    /**
     * Whether the bells take pinches. Off while a card stands in front of the rack (device test 2026-11-12: pinching the
     * start button struck a bell of the bottom row behind it instead).
     */
    fun pinchable(on: Boolean) {
        if (on == pinchOn) return
        pinchOn = on
        for (e in hits.keys) {
            if (on) e.components.set(InteractableComponent())
            else e.components.remove(InteractableComponent::class.java)
        }
    }

    fun hitOf(e: Entity?): String? {
        if (!pinchOn) return null
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    /** Hangs [ids] at the middle row's middle size (the look-alike bells of the restore chapter), or back. */
    fun lookAlike(ids: Set<Int>) {
        if (ids == uniform) return
        val changed = (ids + uniform) - (ids intersect uniform)
        uniform.clear()
        uniform.addAll(ids)
        for (b in Rack.BELLS) if (b.id in changed) buildBell(b)
    }

    /**
     * Hangs bell [bellId] at the place of [slotOf] (the restore chapter's swaps): moves its holder to
     * where the bell that belongs at that slot hangs.
     */
    fun hangAt(bellId: Int, slotBell: Bell) {
        val holder = holders[bellId] ?: return
        val (top, yaw) = anchor(slotBell)
        holder.setPos(Vector3(top.x.toFloat(), top.y.toFloat(), top.z.toFloat()))
        holder.transform().eulerAngles = EulerAngles(0f, yaw.toFloat(), 0f)
    }

    // ---- Lights ----

    /** Outlines: the bells to strike next (gold for the face, blue for the side) and a selected one (green). */
    fun outline(targets: Map<Int, Part?>, selected: Int?) {
        val want = HashMap<Int, Material>()
        for ((id, part) in targets) want[id] = if (part == Part.SIDE) sideTargetMat else targetMat
        selected?.let { want[it] = selectMat }
        for ((id, e) in shells.toList()) if (id !in want) { e.destroy(); shells.remove(id) }
        for ((id, m) in want) {
            shells[id]?.destroy()
            val holder = holders[id] ?: continue
            val b = Rack.BELLS[id]
            val h = bellHeight(b).toFloat()
            val body = bellModel(b)
            val shell = if (body != null) {
                body.setScale(h * 1.06f)
                body.setPos(Vector3(0f, -h * 1.03f, 0f))
                setMaterials(body, m)
                body
            } else model(MeshResource.createBox(Vector3(h * 0.7f, h * 0.85f, h * 0.45f), 0.02f), m).apply { setPos(Vector3(0f, -h * 0.6f, 0f)) }
            holder.getChildren().getOrNull(1)?.addChild(shell)
            shells[id] = shell
        }
    }

    /**
     * The lights of coming notes: each falls from above onto its strike point, landing at its time.
     * [now] is the song time; notes within [LEAD] seconds are drawn.
     */
    fun notes(upcoming: List<Note>, now: Double) {
        while (orbs.size < upcoming.size) orbs += model(sphere, goldOrb).also { root.addChild(it) }
        for (i in orbs.indices) {
            val o = orbs[i]
            val n = upcoming.getOrNull(i)
            if (n == null) {
                if (o.enabled) o.enabled = false
                continue
            }
            val left = (n.time - now).coerceIn(0.0, LEAD)
            // From the player's side straight into the bell's face, landing at the note's time.
            val (_, yaw) = anchor(n.bell)
            val y = Math.toRadians(yaw)
            val k = left / LEAD
            val p = strikePoint(n.bell, n.part) + Vec3(sin(y), 0.0, cos(y)) * (0.06 + k * FALL) + Vec3(0.0, k * 0.08, 0.0)
            o.enabled = true
            o.setPos(p)
            val s = (if (n.damp) 0.03 else 0.022) * (1 + 0.6 * (1 - left / LEAD))
            o.setScale(s.toFloat())
            val m = if (n.damp) dampOrb else if (n.part == Part.SIDE) sideOrb else goldOrb
            o.components[ModelComponent::class.java]?.materials?.let { mats -> if (mats.size > 0 && mats[0] !== m) mats[0] = m }
        }
    }

    // ---- Strokes ----

    /** A stroke on [b] at [part]: the bell swings, light flashes, a mallet shows where it was struck. */
    fun struck(b: Bell, part: Part, strong: Boolean = true) {
        swing[b.id] = if (strong) 1.0 else 0.5
        val p = strikePoint(b, part)
        sparkle.emit(p + Vec3(0.0, 0.0, 0.03), if (strong) 14 else 6, 0.35, 0.6, 0.007f)
        val (_, yaw) = anchor(b)
        val mallet = models["mallet"]?.clone(Entity.CloneOptions(true, true))?.also { it.setScale(0.32f) }
            ?: model(MeshResource.createCylinder(0.3f, 0.012f), redTrim)
        val holder = Entity().apply {
            val y = Math.toRadians(yaw)
            setPos(Vector3((p.x + sin(y) * 0.12).toFloat(), p.y.toFloat(), (p.z + cos(y) * 0.12).toFloat()))
            transform().eulerAngles = EulerAngles(0f, yaw.toFloat() + 90f, 0f)
            addChild(mallet)
        }
        root.addChild(holder)
        mallets += holder to 0.0
    }

    /** A damp: a soft white flash where the palm stops the bell. */
    fun damped(b: Bell) {
        swing[b.id] = 0.0
        sparkle.emit(strikePoint(b, Part.MIDDLE), 5, 0.12, 0.4, 0.005f)
    }

    fun musicianBeat(drum: Boolean) {
        if (drum) drumBob = 1.0 else chimeBob = 1.0
    }

    fun celebrate() {
        for (b in Rack.tierOf(1)) sparkle.emit(strikePoint(b, Part.MIDDLE) + Vec3(0.0, 0.3, 0.0), 8, 0.4, 1.2, 0.008f, Vec3(0.0, 0.2, 0.0))
    }

    fun frame(dt: Double) {
        clock += dt
        for ((id, s) in swing) {
            if (s <= 0.001) continue
            val v = s * exp(-dt * 2.2)
            swing[id] = v
            holders[id]?.getChildren()?.getOrNull(1)?.transform()?.eulerAngles = EulerAngles((v * 4 * sin(clock * 9)).toFloat(), 0f, 0f)
        }
        for ((kind, s) in exhibitSwing) {
            if (s <= 0.001) continue
            val v = s * exp(-dt * 2.2)
            exhibitSwing[kind] = v
            exhibitBells[kind]?.transform()?.eulerAngles = EulerAngles((v * 3 * sin(clock * 9)).toFloat(), 0f, 0f)
        }
        val it = mallets.iterator()
        val keep = ArrayList<Pair<Entity, Double>>()
        while (it.hasNext()) {
            val (e, age) = it.next()
            val a = age + dt
            if (a > 0.35) e.destroy() else {
                e.getChildren().firstOrNull()?.transform()?.eulerAngles = EulerAngles(0f, 0f, (-40 * (1 - a / 0.35)).toFloat())
                keep += e to a
            }
        }
        mallets.clear()
        mallets.addAll(keep)
        drumBob = max(0.0, drumBob - dt * 5)
        chimeBob = max(0.0, chimeBob - dt * 5)
        drummer?.getChildren()?.firstOrNull()?.setPos(Vector3(0f, (0.015 * drumBob).toFloat(), 0f))
        chimer?.getChildren()?.firstOrNull()?.setPos(Vector3(0f, (0.015 * chimeBob).toFloat(), 0f))
        sparkle.update(dt)
    }

    // ---- Light from the panorama ----

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 9f))
        receive(root)
    }

    fun lightUp(e: Entity) = receive(e)

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach(::receive)
    }

    companion object {
        /** Per tier: arc radius, half the spread (degrees), and the beam's height. */
        val TIERS = listOf(Triple(1.55, 45.0, 0.98), Triple(1.35, 56.0, 1.47), Triple(1.42, 45.0, 1.86))
        /** A bell's width over its height (the 合瓦 shape, seen from the front). */
        const val WIDTH = 0.72
        const val LEAD = 1.6
        const val FALL = 0.6
        const val SKY_R = 9f
        /** Where a pitch bar's foot sits, relative to the top of the bell it belongs to. */
        const val PITCH_BAR_FOOT = -0.62f
        const val EXHIBIT_FOOT = 0.62f
        const val EXHIBIT_Z = -0.78f

        fun setMaterials(e: Entity, m: Material) {
            e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
            e.getChildren().forEach { setMaterials(it, m) }
        }
    }
}
