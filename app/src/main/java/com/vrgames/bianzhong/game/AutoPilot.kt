package com.vrgames.bianzhong.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.bianzhong.core.Levels
import com.vrgames.bianzhong.core.Part

/**
 * Test hook, debug builds only: plays the chapters without hands, so every one can be checked on
 * the emulator. Results go to logcat (tag BellTest).
 *
 *   adb shell am broadcast -a com.vrgames.bianzhong.TEST -p com.vrgames.bianzhong --es cmd walk
 *
 * cmd: start · open --es id <level id> · begin · solve (the current lesson, restore or song, played
 * right) · walk (every level in order) · pinch --es id "<bell>,m|s" · grant --es id <stars> · show ·
 * free · hidden · listen · view --es id "turn,lift" · look --es id "yaw,pitch" · window --es id
 * score|guide|menu|close · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.bianzhong.TEST"
    const val TAG = "BellTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "hall ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "start" -> Game.start()
            "open" -> Levels.ALL.firstOrNull { it.id == id }?.let { Game.open(it) }
            "begin" -> Game.begin()
            "solve" -> solve()
            "walk" -> {
                stop()
                walkAt = 0
                main.postDelayed(::walkStep, 300)
            }
            "pinch" -> id?.split(",")?.let { (b, p) -> Game.pinch(b.toInt(), if (p == "s") Part.SIDE else Part.MIDDLE, SystemClock.uptimeMillis()) }
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "show" -> Game.grandShow()
            "free" -> Game.free()
            "hidden" -> Game.openHidden()
            "listen" -> Game.listen()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "look" -> Game.debugLook = id?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            "window" -> Game.debugOpen = id
            "reset" -> {
                stop()
                Game.restart()
            }
            "stop" -> stop()
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private var walkAt = 0

    private fun stop() {
        main.removeCallbacksAndMessages(null)
    }

    /** Plays whatever is in front: the lesson's steps, the right swaps, or the song in time. */
    private fun solve() {
        when (Game.phase) {
            Game.Phase.TUTORIAL -> {
                var k = 0
                fun nextStep() {
                    val t = Game.tutorial ?: return
                    val s = t.current ?: return
                    Game.pinch(s.bell.id, s.part, SystemClock.uptimeMillis())
                    if (++k < 40 && Game.phase == Game.Phase.TUTORIAL) main.postDelayed({ nextStep() }, 450)
                }
                nextStep()
            }
            Game.Phase.RESTORE -> {
                fun nextSwap() {
                    val r = Game.restore ?: return
                    if (r.solved || Game.phase != Game.Phase.RESTORE) return
                    val i = r.order.indices.first { r.order[it] != r.goal[it] }
                    val j = r.order.indexOf(r.goal[i])
                    Game.pinch(r.order[i], Part.MIDDLE, SystemClock.uptimeMillis())
                    main.postDelayed({
                        Game.pinch(r.order[j], Part.MIDDLE, SystemClock.uptimeMillis())
                        main.postDelayed({ nextSwap() }, 500)
                    }, 400)
                }
                nextSwap()
            }
            Game.Phase.COUNTIN, Game.Phase.SONG -> Game.autoplay()
            else -> Log.i(TAG, "nothing to solve in ${Game.phase}")
        }
    }

    private fun walkStep() {
        if (walkAt >= Levels.ALL.size) {
            val pr = Game.progress
            Log.i(TAG, "walk done stars=${pr.stars}/${pr.maxStars} bronze=${pr.bronze} silver=${pr.silver} gold=${pr.gold}")
            return
        }
        val l = Levels.ALL[walkAt++]
        Game.open(l)
        main.postDelayed({
            Game.begin()
            main.postDelayed({
                solve()
                waitResult(l.id)
            }, 600)
        }, 800)
    }

    private fun waitResult(id: String) {
        if (Game.phase == Game.Phase.RESULT) {
            Log.i(TAG, "result $id stars=${Game.resultStars} acc=${"%.3f".format(Game.resultAccuracy)}")
            main.postDelayed(::walkStep, 1500)
        } else main.postDelayed({ waitResult(id) }, 500)
    }
}
