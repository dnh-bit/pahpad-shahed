package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.LevelDef

/** تولید بذر تصادفی برای ساخت دنیای ماموریت */
object Seeds {
    fun forMission(level: LevelDef, mode: MissionMode, save: SaveManager): Long = when (mode) {
        MissionMode.STORY -> level.id.hashCode().toLong() * 7919L + 13L
        MissionMode.DAILY -> save.dailySeed()
        MissionMode.ENDLESS -> System.currentTimeMillis()
    }
}
