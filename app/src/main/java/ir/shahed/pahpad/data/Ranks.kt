package ir.shahed.pahpad.data

/** رتبه‌بندی بازیکن بر اساس امتیاز تجربه */
object Ranks {

    class Rank(val title: String, val minXp: Int, val maxXp: Int) {
        /** پیشرفت در رتبه فعلی بین ۰ و ۱ */
        fun progress(xp: Int): Float {
            if (maxXp <= minXp) return 1f
            return ((xp - minXp).toFloat() / (maxXp - minXp).toFloat()).coerceIn(0f, 1f)
        }
    }

    val all = listOf(
        Rank("سرباز", 0, 1_000),
        Rank("ستوان", 1_000, 5_000),
        Rank("سرگرد", 5_000, 15_000),
        Rank("سرتیپ", 15_000, 30_000),
        Rank("فرمانده", 30_000, Int.MAX_VALUE)
    )

    fun forXp(xp: Int): Rank = all.lastOrNull { xp >= it.minXp } ?: all.first()

    fun next(rank: Rank): Rank? {
        val i = all.indexOf(rank)
        return if (i >= 0 && i < all.size - 1) all[i + 1] else null
    }
}
