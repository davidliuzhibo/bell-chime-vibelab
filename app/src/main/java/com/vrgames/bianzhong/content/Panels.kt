package com.vrgames.bianzhong.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.bianzhong.GUIDE_ID
import com.vrgames.bianzhong.SCORE_ID
import com.vrgames.bianzhong.core.Judge
import com.vrgames.bianzhong.core.Levels
import com.vrgames.bianzhong.core.Performance
import com.vrgames.bianzhong.core.Songs
import com.vrgames.bianzhong.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_FREE = "free"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val LabelInk = Color(0xFFF3EBDD)
val Bronze = Color(0xFFE2B05A)
val Dim = Color(0xFFBDB2A0)
val Jade = Color(0xFF8FD3B0)
val Azure = Color(0xFF7FC8F0)
val Cinnabar = Color(0xFFE0603F)
private val PanelBg = Color(0xE6140F0B)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2140F0B)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0012f), Color(0x66E2B05A), shape).padding(c.m(0.014f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) { content() }
}

/** The step that moves the game on, filled in bronze-gold with dark text. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).background(Bronze).border(c.m(0.002f), Color(0xFFFFF1C9), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.02f),
        color = Color(0xFF1B1408),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.011f), vertical = c.m(0.007f)),
        fontSize = c.sz(0.015f),
        color = color,
    )
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val pr = Game.progress
    PanelBox(c, 0.5f) {
        Text("编钟乐坊", fontSize = c.sz(0.034f), color = Bronze)
        Text("你坐在一座先秦宫殿的乐堂里，一架编钟围在身边：上层小钟清脆，中层在眼前，下层大钟低沉。先把乱了的乐坊整理好，再为一场大典演奏。", fontSize = c.sz(0.016f), color = LabelInk)
        Text("怎么玩", fontSize = c.sz(0.018f), color = Jade)
        Text(
            "· 看着一口钟的正中间捏一下，敲它的正面；看着它的侧边捏一下，敲侧面：同一口钟，两个音。\n· 钟还在响的时候再捏它一下，就是止音。\n· 演奏时光点会落到要敲的钟上，在它落下的那一刻敲：金色正面，蓝色侧面，白圈止音。",
            fontSize = c.sz(0.015f), color = LabelInk,
        )
        if (pr.stars > 0) Text("已得 ${pr.stars} / ${pr.maxStars} 颗星。", fontSize = c.sz(0.014f), color = Jade)
        Text("集齐星星，乐堂里有三座奖杯等着你。", fontSize = c.sz(0.013f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (pr.stars > 0) "继续 →" else "开始 →") { Game.start() }
            if (pr.freePlay) Pill(c, "自由演奏", Jade) { Game.free() }
        }
    }
}

/** Before a level: its chapter, what to do, and the buttons to begin or listen first. */
@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level
    val s = Game.song
    PanelBox(c, 0.48f) {
        if (l != null) {
            Text(Levels.CHAPTERS[l.chapter], fontSize = c.sz(0.016f), color = Dim)
            Text(l.title, fontSize = c.sz(0.03f), color = Bronze)
            Text(l.intro, fontSize = c.sz(0.016f), color = LabelInk)
        } else if (s != null) {
            Text("隐藏曲目", fontSize = c.sz(0.016f), color = Dim)
            Text(s.title, fontSize = c.sz(0.03f), color = Bronze)
        }
        if (s != null) Text(s.about, fontSize = c.sz(0.014f), color = Dim)
        val best = l?.let { Game.progress.best[it.id] } ?: Game.progress.best["jingle"]
        if (best != null && best > 0) Text("最好成绩 ${stars(best)}", fontSize = c.sz(0.015f), color = Jade)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, "开始 →") { Game.begin() }
            if (s != null) Pill(c, "先听一遍", Jade) { Game.listen() }
            if (s != null && (best ?: 0) > 0) Pill(c, "高手谱（快三成）", Cinnabar) { Game.begin(fast = true) }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

/** Low in front: what is going on now (the step, the swaps, the judge and combo of a song). */
@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    PanelBox(c, 0.42f) {
        when (Game.phase) {
            Game.Phase.TUTORIAL -> {
                val t = Game.tutorial ?: return@PanelBox
                Text("认识编钟　第 ${(t.at + 1).coerceAtMost(t.steps.size)} / ${t.steps.size} 步", fontSize = c.sz(0.02f), color = Bronze)
                Text("错了 ${t.mistakes} 次（不超过 1 次得三星）", fontSize = c.sz(0.014f), color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) { CallToAction(c, "提示") { Game.hint() } }
            }
            Game.Phase.RESTORE -> {
                val r = Game.restore ?: return@PanelBox
                Text("乐坊复原　换了 ${r.swaps} 次", fontSize = c.sz(0.02f), color = Bronze)
                Text("最少 ${r.fewest} 次就能换好；不超过 ${r.fewest + 1} 次、不用提示得三星。", fontSize = c.sz(0.014f), color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                    CallToAction(c, "听示范（正确顺序）") { Game.demoScale() }
                    Pill(c, "提示", Bronze) { Game.hint() }
                }
            }
            Game.Phase.COUNTIN, Game.Phase.SONG -> {
                val s = Game.song ?: return@PanelBox
                val p = Game.perf
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title + if (Game.speed > 1) "（高手谱）" else "", fontSize = c.sz(0.02f), color = Bronze)
                    Spacer(Modifier.weight(1f))
                    val j = Game.lastJudge
                    if (j != null) Text(j.label, fontSize = c.sz(0.024f), color = when (j) { Judge.PERFECT -> Jade; Judge.MISS -> Cinnabar; else -> Azure })
                }
                if (p != null) {
                    val played = p.judged.count { it != null }
                    Text("连击 ${Game.combo}　已奏 $played / ${p.notes.size}　准确 ${(p.accuracy() * 100).toInt()}%", fontSize = c.sz(0.015f), color = LabelInk)
                }
                Text("金色敲正面　蓝色敲侧面　白圈止音", fontSize = c.sz(0.013f), color = Dim)
            }
            Game.Phase.SHOW -> {
                Text("自动演奏中……", fontSize = c.sz(0.02f), color = Bronze)
                Pill(c, "停止") { Game.toCard() }
            }
            else -> Unit
        }
        Pill(c, "乐谱", Bronze) { toggle(SCORE_ID) }
    }
}

@Composable
fun FreePanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    PanelBox(c, 0.42f) {
        Text("自由演奏", fontSize = c.sz(0.022f), color = Bronze)
        Text(if (Game.recordOn) "录音中：${Game.recording.size} 个音" else if (Game.recording.isNotEmpty()) "已录 ${Game.recording.size} 个音" else "随便敲，录下来再听。", fontSize = c.sz(0.015f), color = LabelInk)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            CallToAction(c, if (Game.recordOn) "停止录音" else "录音") { Game.toggleRecord() }
            if (Game.recording.isNotEmpty() && !Game.recordOn) Pill(c, "回放", Jade) { Game.replay() }
            Pill(c, "离开") { Game.toCard() }
        }
    }
}

/** After a level: its stars, a new trophy, and what next. */
@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val pr = Game.progress
    val l = Game.level
    PanelBox(c, 0.48f) {
        val title = l?.title ?: Game.song?.title ?: "演奏"
        if (Game.resultStars > 0) {
            Text("$title　${stars(Game.resultStars)}", fontSize = c.sz(0.028f), color = Bronze)
        } else {
            Text("$title　没有过关", fontSize = c.sz(0.028f), color = Cinnabar)
            Text("准确率要到 ${(Performance.PASS * 100).toInt()}%。先「先听一遍」，记住旋律再来。", fontSize = c.sz(0.015f), color = LabelInk)
        }
        val p = Game.perf
        if (l?.kind == Levels.Kind.SONG || l == null && p != null) {
            if (p != null) Text(
                "准确率 ${(Game.resultAccuracy * 100).toInt()}%　正好 ${p.count(Judge.PERFECT)}　稍早稍晚 ${p.count(Judge.GOOD) + p.count(Judge.LATE)}　漏掉 ${p.count(Judge.MISS)}　最高连击 ${Game.bestCombo}",
                fontSize = c.sz(0.014f), color = LabelInk,
            )
            if (Game.resultStars in 1..2) Text("三星要 ${(Performance.THREE * 100).toInt()}% 以上。", fontSize = c.sz(0.013f), color = Dim)
        }
        val t = Game.trophies
        if (t > Game.trophiesBefore) {
            Text(
                when (t) {
                    1 -> "得到铜奖杯！五章全部完成。"
                    2 -> "得到银奖杯！解锁隐藏曲目「铃儿响叮当」。"
                    else -> "得到金奖杯！解锁「大典演出」和珍藏画「金声玉振」。"
                },
                fontSize = c.sz(0.017f), color = Jade,
            )
        }
        Text("总星数 ${pr.stars} / ${pr.maxStars}", fontSize = c.sz(0.014f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            if (pr.next() != null && Game.resultStars > 0) CallToAction(c, "下一关 →") { Game.next() }
            if (l != null) Pill(c, "再来一次") { Game.open(l) } else if (Game.song == Songs.JINGLE) Pill(c, "再来一次") { Game.openHidden() }
            Pill(c, "乐谱", Bronze) { toggle(SCORE_ID) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            if (pr.silver) Pill(c, "隐藏曲目", Jade) { Game.openHidden() }
            if (pr.gold) Pill(c, "大典演出", Bronze) { Game.grandShow() }
            if (pr.freePlay) Pill(c, "自由演奏", Jade) { Game.free() }
        }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.56f)).clip(shape).background(PanelBg).padding(c.m(0.01f)), fontSize = c.sz(0.018f), color = Bronze)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Bronze) { Game.menuOpen = true }
        return
    }
    val pr = Game.progress
    PanelBox(c, 0.27f) {
        Pill(c, if (Game.window == SCORE_ID) "收起乐谱" else "乐谱（选曲、奖杯）", Bronze) { toggle(SCORE_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起编钟图鉴" else "编钟图鉴") { toggle(GUIDE_ID) }
        if (pr.freePlay) Pill(c, "自由演奏", Jade) { Game.menuOpen = false; Game.free() }
        if (pr.silver) Pill(c, "隐藏曲目", Jade) { Game.menuOpen = false; Game.openHidden() }
        if (pr.gold) Pill(c, "大典演出", Bronze) { Game.menuOpen = false; Game.grandShow() }
        Text("节奏校准 ${if (Game.calibMs > 0) "+" else ""}${Game.calibMs} 毫秒", fontSize = c.sz(0.013f), color = LabelInk)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.005f))) {
            Pill(c, "总是偏晚 −20") { Game.calibrate(-20) }
            Pill(c, "总是偏早 +20") { Game.calibrate(20) }
        }
        Pill(c, "把编钟转到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "背景声：开" else "背景声：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.012f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Cinnabar) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else {
        Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
    }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Bronze)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
