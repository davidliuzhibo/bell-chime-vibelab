package com.vrgames.bianzhong.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.bianzhong.GUIDE_ID
import com.vrgames.bianzhong.SCORE_ID
import com.vrgames.bianzhong.core.Levels
import com.vrgames.bianzhong.core.Part
import com.vrgames.bianzhong.core.Song
import com.vrgames.bianzhong.game.Game

private val CardBg = Color(0x26F3EBDD)

/** 乐谱: the chapters and their stars (pick one to play), the score of the current tune, the trophies. */
@Composable
fun ScoreWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val pr = Game.progress
    val context = LocalContext.current
    val treasure: ImageBitmap? = remember {
        runCatching { context.assets.open("textures/treasure.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    }
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WindowHeader(SCORE_ID, "乐谱", 32.sp)
        Text("总星数 ${pr.stars} / ${pr.maxStars}。点一关就能开始（或重来，把星星拿满）。", fontSize = 18.sp, color = Dim)
        for (l in Levels.ALL) {
            val open = pr.open(l)
            val s = pr.best[l.id] ?: 0
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Levels.CHAPTERS[l.chapter]}　${l.title}", fontSize = 20.sp, color = if (open) Bronze else Dim)
                    Text(if (!open) "还没开放" else if (s > 0) "★".repeat(s) + "☆".repeat(3 - s) else "还没玩过", fontSize = 16.sp, color = LabelInk)
                }
                if (open) Button(onClick = { Game.open(l); Game.closeWindow(SCORE_ID) }) { Text(if (s > 0) "重来" else "开始", fontSize = 20.sp) }
            }
        }
        if (pr.silver) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("隐藏曲目　铃儿响叮当", fontSize = 20.sp, color = Jade, modifier = Modifier.weight(1f))
            Button(onClick = { Game.openHidden(); Game.closeWindow(SCORE_ID) }) { Text("演奏", fontSize = 20.sp) }
        }
        Game.song?.let { JianPu(it) }
        Text("奖杯", fontSize = 24.sp, color = Jade)
        Trophy("铜奖杯", "五章全部完成。", pr.bronze, "一口铜色的小编钟。")
        Trophy("银奖杯", "拿到一半以上的星（${pr.maxStars / 2} 颗）。", pr.silver, "解锁隐藏曲目「铃儿响叮当」。")
        Trophy("金奖杯", "拿满全部 ${pr.maxStars} 颗星。", pr.gold, "金色小编钟、「大典演出」和珍藏画「金声玉振」。")
        if (pr.gold) {
            Button(onClick = { Game.grandShow(); Game.closeWindow(SCORE_ID) }) { Text("看大典演出", fontSize = 20.sp) }
            if (treasure != null) {
                Text("珍藏画 · 金声玉振", fontSize = 22.sp, color = Bronze)
                Image(treasure, "金声玉振", Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** The tune in jianpu, a bar per line; side-tone notes in blue, damped ones marked with ✋. */
@Composable
private fun JianPu(s: Song) {
    Text("《${s.title}》简谱", fontSize = 24.sp, color = Jade)
    Text("蓝色的音敲钟的侧面，✋ 是止音。", fontSize = 16.sp, color = Dim)
    val strokes = s.notes.filter { !it.damp }
    val damps = s.notes.filter { it.damp }.map { it.time }.toSet()
    val bar = 4 * s.beat
    val lines = strokes.groupBy { (it.time / (bar * 2) + 1e-6).toInt() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((_, notes) in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (n in notes) {
                    val ends = damps.any { it > n.time && it - n.time < 4.2 * s.beat && strokes.none { o -> o.time > n.time && o.time < it } }
                    Text(pretty(n.degree) + if (ends) "✋" else "", fontSize = 24.sp, color = if (n.part == Part.SIDE) Azure else LabelInk)
                }
            }
        }
    }
}

private fun pretty(d: String) = d.replace("'", "̇").replace("_", "̣")

@Composable
private fun Trophy(name: String, rule: String, got: Boolean, reward: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (got) "$name　已得到" else "$name　？", fontSize = 20.sp, color = if (got) Bronze else Dim)
        Text(rule, fontSize = 16.sp, color = LabelInk)
        Text(if (got) reward else "得到后揭晓。", fontSize = 16.sp, color = if (got) Jade else Dim)
    }
}

/** 编钟图鉴: true notes on the Marquis Yi of Zeng's bells and how bells work. */
@Composable
fun GuideWindow() {
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WindowHeader(GUIDE_ID, "编钟图鉴", 32.sp)
        for ((title, text) in GUIDE) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 24.sp, color = Bronze)
                Text(text, fontSize = 18.sp, color = LabelInk)
            }
        }
        Text("本作的乐堂、编钟和乐师是按曾侯乙编钟的形制重新制作的，不是文物原件的照片或录音；钟声由程序合成。曲目均为公有领域的旋律，由本作编配。", fontSize = 15.sp, color = Dim)
    }
}

private val GUIDE = listOf(
    "曾侯乙编钟" to "1978 年在湖北随州的曾侯乙墓出土，是两千四百多年前战国早期的乐器。一整套共 65 件，分三层八组，挂在曲尺形的钟架上，现在收藏在湖北省博物馆。",
    "一钟双音" to "敲钟的正面中间（正鼓）和侧边（侧鼓），会发出两个不同的音，大多相差三度。秘密在钟的形状：它的横截面像两片瓦合在一起（合瓦形），不同的位置激起不同的振动。",
    "十二个半音" to "这套编钟的音域跨越五个八度多，中间几个八度里十二个半音都齐全，可以转调，演奏各种曲子。",
    "钟上的字" to "钟体和钟架上刻着几千字的铭文，大部分讲的是音乐：音名、律名，以及曾国和楚国等各国乐律之间的对应关系，是研究古代乐律的珍贵资料。",
    "钟架和铜人" to "钟架由青铜佩剑武士和圆柱托着，横梁上装饰着青铜构件。上千年埋在地下，钟架依然能承重挂钟。",
    "怎么演奏" to "古代乐师用丁字形的木槌敲上层和中层的钟，用长木棒撞下层的大钟；演奏一整套编钟，需要好几位乐师一起配合。",
    "钟和磬、鼓" to "古代的宫廷雅乐里，钟、磬、鼓常常一起演奏。“金声玉振”说的就是用钟声开始、用磬声收束，后来比喻才学精妙、有始有终。",
)
