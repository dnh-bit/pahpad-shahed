package ir.shahed.pahpad.data

import ir.shahed.pahpad.core.Theme

/** انواع ارتقای پهباد */
enum class UpgradeKind(
    val key: String,
    val title: String,
    val description: String,
    val baseCost: Int
) {
    RANGE("range", "برد پرواز", "مسافتی که پهباد پیش از اتمام سوخت می‌پیماید", 120),
    SPEED("speed", "سرعت", "حداکثر سرعت پرواز پهباد", 140),
    GUIDANCE("guidance", "دقت هدایت", "چابکی و نرمی فرمان‌پذیری پهباد", 160),
    BLAST("blast", "قدرت انفجار", "شعاع اثر سرجنگی در لحظه برخورد", 180),
    ARMOR("armor", "مقاومت", "تعداد ضربه‌های پدافندی قابل تحمل", 200);

    fun costFor(nextLevel: Int): Int {
        if (nextLevel > 10) return 0
        val n = (nextLevel - 1).coerceAtLeast(0)
        return Math.round(baseCost * (1f + n * 0.85f + n * n * 0.12f))
    }

    fun valueAt(level: Int, model: DroneModels.Model): String {
        val lvl = level.coerceIn(1, 10)
        return when (this) {
            RANGE -> ir.shahed.pahpad.core.Fa.num(Math.round(model.rangeAt(lvl))) + " متر"
            SPEED -> ir.shahed.pahpad.core.Fa.num(Math.round(model.speedAt(lvl))) + " کیلومتر/ساعت"
            GUIDANCE -> ir.shahed.pahpad.core.Fa.num(model.guidanceAt(lvl), 1) + "×"
            BLAST -> ir.shahed.pahpad.core.Fa.num(Math.round(model.blastAt(lvl))) + " متر"
            ARMOR -> ir.shahed.pahpad.core.Fa.num(model.armorAt(lvl)) + " ضربه"
        }
    }
}

object DroneModels {

    class Model(
        val id: String,
        val name: String,
        val requiredStars: Int,
        val tagline: String,
        val color: Int,
        private val rangeMul: Float,
        private val speedMul: Float,
        private val guidanceMul: Float,
        private val blastMul: Float,
        private val armorBonus: Int,
        val hasEcm: Boolean
    ) {
        fun rangeAt(level: Int) = DroneModels.anchored(level, 900f, 1600f, 2600f) * rangeMul
        fun speedAt(level: Int) = DroneModels.anchored(level, 150f, 250f, 400f) * speedMul
        fun guidanceAt(level: Int) = DroneModels.anchored(level, 1.0f, 1.5f, 2.2f) * guidanceMul
        fun blastAt(level: Int) = DroneModels.anchored(level, 18f, 30f, 45f) * blastMul
        fun armorAt(level: Int): Int =
            (Math.round(DroneModels.anchored(level, 1f, 3f, 5f)) + armorBonus).coerceAtLeast(1)
    }

    /** درون‌یابی خطی بین سه لنگر سطح ۱ ، ۵ و ۱۰ */
    fun anchored(level: Int, a1: Float, a5: Float, a10: Float): Float {
        val l = level.coerceIn(1, 10)
        return if (l <= 5) a1 + (a5 - a1) * ((l - 1) / 4f)
        else a5 + (a10 - a5) * ((l - 5) / 5f)
    }

    val all = listOf(
        Model(
            id = "shahed131",
            name = "شاهد-۱۳۱",
            requiredStars = 0,
            tagline = "پهباد پایه؛ سبک، چابک و مناسب آموزش",
            color = Theme.MINT,
            rangeMul = 1.0f, speedMul = 1.0f, guidanceMul = 1.0f, blastMul = 1.0f,
            armorBonus = 0, hasEcm = false
        ),
        Model(
            id = "shahed136",
            name = "شاهد-۱۳۶",
            requiredStars = 10,
            tagline = "برد بیشتر و سرعت بالاتر برای ماموریت‌های دور",
            color = Theme.AMBER,
            rangeMul = 1.35f, speedMul = 1.20f, guidanceMul = 1.05f, blastMul = 1.15f,
            armorBonus = 1, hasEcm = false
        ),
        Model(
            id = "shahed238",
            name = "شاهد-۲۳۸",
            requiredStars = 25,
            tagline = "سرجنگی سنگین همراه با مقابله با جنگ الکترونیک",
            color = Theme.BLUE,
            rangeMul = 1.45f, speedMul = 1.55f, guidanceMul = 1.15f, blastMul = 1.45f,
            armorBonus = 1, hasEcm = true
        ),
        Model(
            id = "shahedx",
            name = "شاهد-ایکس",
            requiredStars = 50,
            tagline = "نسخه فوق پیشرفته با تمام قابلیت‌ها",
            color = Theme.VIOLET,
            rangeMul = 1.8f, speedMul = 1.7f, guidanceMul = 1.35f, blastMul = 1.7f,
            armorBonus = 2, hasEcm = true
        )
    )

    fun byId(id: String): Model? = all.firstOrNull { it.id == id }
}
