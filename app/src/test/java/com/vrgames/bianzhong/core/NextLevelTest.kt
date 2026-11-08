package com.vrgames.bianzhong.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** The result card's 下一关 opens the first level not yet cleared: after the restore it must be the third level. */
class NextLevelTest {
    @Test fun afterTheRestoreTheNextLevelIsTheThird() {
        val p = Progress()
        p.record(Levels.ALL[0].id, 3)
        p.record(Levels.ALL[1].id, 1)
        assertEquals(Levels.ALL[2].id, p.next()?.id)
    }

    @Test fun theNextLevelSurvivesASaveAndReload() {
        val p = Progress()
        p.record(Levels.ALL[0].id, 3)
        p.record(Levels.ALL[1].id, 2)
        assertEquals(Levels.ALL[2].id, Progress.load(p.save()).next()?.id)
    }
}
