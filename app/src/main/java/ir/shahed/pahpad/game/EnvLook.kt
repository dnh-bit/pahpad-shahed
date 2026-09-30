package ir.shahed.pahpad.game

import ir.shahed.pahpad.data.Env
import kotlin.math.sqrt

/**
 * Art direction per environment (0.0.9). One place for sky, fog, sun and ambient
 * so that the sky dome, the ground haze, fogged geometry and the horizon PNGs
 * all agree. `fog` MUST match FOG in tools/gen_atmosphere.py.
 */
class EnvLook(
    val zenith: Int,
    val upper: Int,
    val glow: Int,
    val fog: Int,
    val groundFallback: Int,
    sunX: Float, sunY: Float, sunZ: Float,
    /** Linear sun colour, roughly 0..1.2 per channel. */
    val sunR: Float, val sunG: Float, val sunB: Float,
    val skyR: Float, val skyG: Float, val skyB: Float,
    val bounceR: Float, val bounceG: Float, val bounceB: Float,
    /** Multiplier and additive tint for the shared cloud deck. */
    val cloudMul: Int, val cloudAdd: Int, val cloudAlpha: Int,
    val sunDisc: Int, val sunHalo: Int,
    val stars: Boolean,
    /** Lit-window colour and chance; dusk/night cities glow. */
    val windowLit: Int, val windowGlass: Int, val litChance: Int,
    val fogStart: Float, val fogEnd: Float
) {
    val sunX: Float; val sunY: Float; val sunZ: Float
    init {
        val l = sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ).coerceAtLeast(1e-4f)
        this.sunX = sunX / l; this.sunY = sunY / l; this.sunZ = sunZ / l
    }

    companion object {
        /** Golden afternoon; sun front-left so the flight path is side lit. */
        val DESERT = EnvLook(
            zenith = 0xFF2C5A92.toInt(), upper = 0xFF6B98C4.toInt(), glow = 0xFFF0D6AA.toInt(), fog = 0xFFE6D2AE.toInt(),
            groundFallback = 0xFFB08F63.toInt(),
            sunX = -.58f, sunY = .46f, sunZ = .67f,
            sunR = .98f, sunG = .86f, sunB = .68f,
            skyR = .50f, skyG = .56f, skyB = .66f,
            bounceR = .46f, bounceG = .38f, bounceB = .28f,
            cloudMul = 0xFFFFF4E4.toInt(), cloudAdd = 0xFF100804.toInt(), cloudAlpha = 210,
            sunDisc = 0xFFFFF4D6.toInt(), sunHalo = 0xFFFFD99A.toInt(), stars = false,
            windowLit = 0xFF9FB7BE.toInt(), windowGlass = 0xFF3E5863.toInt(), litChance = 6,
            fogStart = 320f, fogEnd = 1650f
        )
        /** Hazy city dusk; low warm sun on the right, cool ambient. */
        val URBAN = EnvLook(
            zenith = 0xFF1F2A44.toInt(), upper = 0xFF566A8E.toInt(), glow = 0xFFE9AC7C.toInt(), fog = 0xFFC4AE9C.toInt(),
            groundFallback = 0xFF6E6D66.toInt(),
            sunX = .66f, sunY = .34f, sunZ = .67f,
            sunR = 1.02f, sunG = .72f, sunB = .48f,
            skyR = .44f, skyG = .49f, skyB = .62f,
            bounceR = .34f, bounceG = .30f, bounceB = .28f,
            cloudMul = 0xFFF2C6B4.toInt(), cloudAdd = 0xFF1A0E10.toInt(), cloudAlpha = 200,
            sunDisc = 0xFFFFE2BE.toInt(), sunHalo = 0xFFFFA468.toInt(), stars = false,
            windowLit = 0xFFFFCB84.toInt(), windowGlass = 0xFF33414F.toInt(), litChance = 4,
            fogStart = 280f, fogEnd = 1500f
        )
        /** Clear maritime noon; high sun behind-left keeps ships readable. */
        val NAVAL = EnvLook(
            zenith = 0xFF1B5896.toInt(), upper = 0xFF5E9ED2.toInt(), glow = 0xFFD9EAF1.toInt(), fog = 0xFFCFE0E8.toInt(),
            groundFallback = 0xFF245C74.toInt(),
            sunX = -.42f, sunY = .70f, sunZ = .58f,
            sunR = 1.0f, sunG = .97f, sunB = .92f,
            skyR = .50f, skyG = .59f, skyB = .70f,
            bounceR = .30f, bounceG = .38f, bounceB = .44f,
            cloudMul = 0xFFFFFFFF.toInt(), cloudAdd = 0xFF000000.toInt(), cloudAlpha = 225,
            sunDisc = 0xFFFFFFF2.toInt(), sunHalo = 0xFFFFF1CF.toInt(), stars = false,
            windowLit = 0xFFA9C4CF.toInt(), windowGlass = 0xFF3B5663.toInt(), litChance = 6,
            fogStart = 360f, fogEnd = 1750f
        )
        /** Blue-hour special ops: stars overhead, ember horizon, lit windows. */
        val SPECIAL = EnvLook(
            zenith = 0xFF0B1030.toInt(), upper = 0xFF303A6C.toInt(), glow = 0xFFD38672.toInt(), fog = 0xFF9A7C88.toInt(),
            groundFallback = 0xFF5E5A50.toInt(),
            sunX = -.70f, sunY = .26f, sunZ = .66f,
            sunR = .95f, sunG = .58f, sunB = .42f,
            skyR = .38f, skyG = .41f, skyB = .60f,
            bounceR = .26f, bounceG = .23f, bounceB = .27f,
            cloudMul = 0xFFB9A2C0.toInt(), cloudAdd = 0xFF140A14.toInt(), cloudAlpha = 170,
            sunDisc = 0xFFFFD6B0.toInt(), sunHalo = 0xFFFF8A5C.toInt(), stars = true,
            windowLit = 0xFFFFC874.toInt(), windowGlass = 0xFF252C3E.toInt(), litChance = 3,
            fogStart = 260f, fogEnd = 1400f
        )

        fun of(env: Env): EnvLook = when (env) {
            Env.URBAN -> URBAN
            Env.NAVAL -> NAVAL
            Env.SPECIAL -> SPECIAL
            else -> DESERT
        }
    }
}
