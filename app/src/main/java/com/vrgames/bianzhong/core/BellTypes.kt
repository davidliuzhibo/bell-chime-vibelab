package com.vrgames.bianzhong.core

/**
 * The three kinds of bell in the Marquis Yi of Zeng's set, and the two facts every visitor asks
 * about. [narration] is the text of the recorded clip narration/<id>.ogg (tools/make_narration.py).
 */
data class BellTopic(val id: String, val title: String, val subtitle: String, val narration: String, val kind: BellKind? = null)

enum class BellKind(val model: String, val label: String) { NIU("niu", "钮钟"), YONG("yong", "甬钟"), BO("bo", "镈钟") }

object BellTopics {
    val ALL = listOf(
        BellTopic(
            "overview", "曾侯乙编钟", "全套 65 件：19 钮钟 + 45 甬钟 + 1 镈钟",
            "1978 年，湖北随州出土了曾侯乙编钟，距今两千四百多年。全套 65 件，分三层八组挂在曲尺形的钟架上：上层 19 件钮钟，中下层 45 件甬钟，再加一件镈钟，钟的总重超过两吨半。",
        ),
        BellTopic(
            "niu", "钮钟", "上层 19 件 · 顶上一个方钮，直着挂",
            "钮钟，是编钟里最小巧的一种。它的顶上只有一个方形的钮，可以直直地挂在架子上。曾侯乙编钟的上层，19 件都是钮钟，个头小，音也最高，声音清亮。",
            BellKind.NIU,
        ),
        BellTopic(
            "yong", "甬钟", "中下层 45 件 · 顶上一根长柱，斜着挂",
            "甬钟，顶上有一根长长的圆柱，叫作“甬”，钟靠它斜挂在架子上。曾侯乙编钟的中层和下层一共有 45 件甬钟，是这套乐器的主力。钟身上那些突起的乳钉叫作“枚”，一般认为能让余音收得更干净。",
            BellKind.YONG,
        ),
        BellTopic(
            "bo", "镈钟", "全套仅 1 件 · 钟口是平的",
            "镈钟，钟口是平的，不像钮钟和甬钟那样向上弯成弧形，钟声也更浑厚。曾侯乙编钟里只有一件镈钟，挂在下层正中，是楚惠王送给曾侯乙的礼物，钟上的铭文记下了这件事。",
            BellKind.BO,
        ),
        BellTopic(
            "twotones", "一钟双音", "敲正面和敲侧面，是两个音",
            "编钟最奇妙的地方，是一口钟能敲出两个音。它的横截面像两片瓦合在一起，叫合瓦形。敲正面和敲侧面，振动的部位不同，就发出两个不同的音，通常相差三度。你可以在面前这几口钟上试试。",
        ),
    )
    fun byId(id: String) = ALL.first { it.id == id }
}
