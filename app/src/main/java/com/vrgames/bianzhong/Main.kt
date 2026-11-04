package com.vrgames.bianzhong

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.bianzhong.content.GuideWindow
import com.vrgames.bianzhong.content.HallStage
import com.vrgames.bianzhong.content.ScoreWindow

const val SCORE_ID = "Score"
const val GUIDE_ID = "Guide"

/**
 * Three linked spaces: the music hall with its rack of bells all around (a full-immersion Stage),
 * the 乐谱 (chapters, stars, the tune in jianpu, the trophies) and the 编钟图鉴 (true notes on the
 * Marquis Yi of Zeng's bells). They share [com.vrgames.bianzhong.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { HallStage() }
        }
        WindowContainer(SCORE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { ScoreWindow() }
        }
        WindowContainer(GUIDE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { GuideWindow() }
        }
    }
