package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.Bitmap
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * مدل‌های سه‌بعدی سخت‌افزار نظامی: تانک، نفربر، کامیون، ناوشکن، ناو هواپیمابر،
 * پرتابگر موشک، توپ پدافند، ایستگاه رادار، دکل خرپا، قطار و سازه‌های هدف.
 *
 * همه‌چیز هندسه‌ی رویه‌ای در همان رندرکننده‌ی Canvas موجود است؛ هیچ فایل مدل خارجی
 * (OBJ/GLTF) بارگذاری نمی‌شود، چون این موتور z-buffer و بارگذار مش ندارد.
 * ابعاد بر حسب متر نوشته شده‌اند و با `unitScale` مقیاس می‌گیرند.
 *
 * سطح جزئیات (detail): ۰ نزدیک، ۱ متوسط، ۲ دور.
 */
class Vehicles(private val context: Context) {

    private fun tex(name: String): Bitmap? = Gfx.get(context, name)

    private val camo: Bitmap? get() = tex("material_camo")
    private val metal: Bitmap? get() = tex("material_metal")
    private val deckTex: Bitmap? get() = tex("material_deck")
    private val trackTex: Bitmap? get() = tex("material_track")
    private val burnt: Bitmap? get() = tex("material_rust")
    private val concrete: Bitmap? get() = tex("material_concrete")
    private val wallTex: Bitmap? get() = tex("material_wall")
    private val roofTex: Bitmap? get() = tex("material_roof")

    /** نقطهٔ مرجع بایاس برای وجه‌های تختِ روی عرشه (نوار فرود و نشانه‌ها)؛
     *  face() آن را هم‌زمان مصرف می‌کند، پس بازاستفاده در هر فراخوانی امن است. */
    private val biasRefScratch = FloatArray(3)

    companion object {
        private val FREIGHT_COLORS = intArrayOf(
            0xFF8E4A3C.toInt(), 0xFF3E6A78.toInt(), 0xFF6E7A46.toInt(), 0xFF8A8A82.toInt()
        )
        const val SAND = 0xFF9C8B62.toInt()
        const val OLIVE = 0xFF5C6247.toInt()
        const val TRACK = 0xFF3B3B38.toInt()
        const val HAZE = 0xFF7D868B.toInt()
        const val DECK = 0xFF565E62.toInt()
        const val GLASS = 0xFF29373F.toInt()
        const val STEEL = 0xFF8E9691.toInt()
        const val PALE = 0xFFB6B9B1.toInt()
        const val DARK = 0xFF3A3E3C.toInt()
        const val CHAR = 0xFF33302C.toInt()
        const val REDBAND = 0xFF9E4438.toInt()
    }

    private fun skin(destroyed: Boolean, normal: Int): Int =
        if (destroyed) Theme.mix(normal, CHAR, 0.78f) else normal

    private fun skinTex(destroyed: Boolean, normal: Bitmap?): Bitmap? =
        if (destroyed) burnt else normal

    // ================================================================= زمینی

    /** تانک اصلی میدان نبرد. طول ≈ ۹٫۸ متر، عرض ≈ ۳٫۷ متر. */
    fun tank(
        rig: Rig, x: Float, z: Float, yaw: Float, unit: Float,
        turretYaw: Float, destroyed: Boolean, detail: Int
    ) {
        rig.place(x, 0f, z, yaw, unit)
        val body = skin(destroyed, SAND)
        val hide = skinTex(destroyed, camo)
        val dark = skin(destroyed, TRACK)

        // --- زیرمجموعه‌ی حرکت: شاسی، زنجیر و چرخ‌های راهنما
        for (s in -1..1 step 2) {
            val sx = s * 1.58f
            rig.box(sx, 0f, -0.1f, 0.60f, 1.05f, 7.30f, dark, skinTex(destroyed, trackTex))
            if (detail == 0) {
                for (i in 0..5) {
                    val wz = -3.1f + i * 1.24f
                    rig.tube(sx - 0.34f, 0.52f, wz, sx + 0.30f, 0.52f, wz, 0.44f, 6, Theme.shade(dark, 1.25f), false)
                }
                // چرخ محرک عقب و هرزگرد جلو بزرگ‌تر هستند
                rig.tube(sx - 0.36f, 0.62f, -3.9f, sx + 0.32f, 0.62f, -3.9f, 0.54f, 7, Theme.shade(dark, 1.1f), false)
                rig.tube(sx - 0.36f, 0.62f, 3.7f, sx + 0.32f, 0.62f, 3.7f, 0.54f, 7, Theme.shade(dark, 1.1f), false)
                // روکش خاک‌انداز
                rig.box(sx, 1.12f, -0.1f, 0.72f, 0.10f, 7.10f, Theme.shade(body, 0.9f), hide)
            }
        }

        // --- بدنه: کف، سینه‌ی شیب‌دار و سقف
        rig.box(0f, 0.45f, -0.6f, 3.10f, 0.80f, 6.60f, Theme.shade(body, 0.92f), hide)
        rig.wedge(0f, 1.25f, 2.45f, 3.10f, 2.40f, 0.20f, 0.92f, body, hide)
        rig.box(0f, 1.25f, -1.30f, 3.10f, 0.90f, 5.20f, body, hide)
        // موتور عقب با توری خنک‌کننده
        rig.box(0f, 2.15f, -2.95f, 2.86f, 0.34f, 1.90f, Theme.shade(body, 0.86f), hide)
        if (detail == 0) {
            rig.box(0f, 2.49f, -2.95f, 2.30f, 0.06f, 1.30f, dark)
            rig.tube(-1.25f, 2.42f, -3.60f, -1.25f, 2.42f, -1.90f, 0.16f, 6, DARK)
        }

        // --- برجک (چرخان)
        rig.place(x, 0f, z, if (destroyed) yaw + 0.55f else turretYaw, unit)
        val ty = 2.15f
        rig.taper(0f, ty, -0.25f, 1.42f, 1.85f, 1.02f, 1.35f, 0.86f, body, hide, hide, 0f, -0.10f)
        // ماسک لوله و لوله‌ی توپ ۱۲۵ میلی‌متری
        rig.box(0f, ty + 0.16f, 1.42f, 1.10f, 0.62f, 0.55f, Theme.shade(body, 1.04f), hide)
        rig.tube(0f, ty + 0.46f, 1.60f, 0f, ty + 0.52f, 6.05f, 0.13f, 7, Theme.shade(dark, 1.35f))
        rig.tube(0f, ty + 0.52f, 5.55f, 0f, ty + 0.54f, 6.35f, 0.19f, 7, Theme.shade(dark, 1.15f))
        if (detail == 0) {
            // دریچه‌ی فرمانده، تیربار و پرتابگر دودزا
            rig.tube(0.52f, ty + 0.86f, -0.55f, 0.52f, ty + 1.16f, -0.55f, 0.42f, 8, Theme.shade(body, 1.06f))
            rig.disc(0.52f, ty + 1.17f, -0.55f, 0f, 1f, 0f, 0.42f, 8, Theme.shade(body, 1.12f))
            rig.tube(0.52f, ty + 1.18f, -0.35f, 0.52f, ty + 1.22f, 0.85f, 0.06f, 5, DARK)
            for (s in -1..1 step 2) {
                rig.box(s * 1.02f, ty + 0.60f, 0.72f, 0.55f, 0.26f, 0.20f, Theme.shade(dark, 1.2f))
            }
            // سبد بار عقب برجک و بشکه‌های سوخت
            rig.box(0f, ty + 0.20f, -1.72f, 1.90f, 0.52f, 0.62f, Theme.shade(dark, 1.1f))
            rig.place(x, 0f, z, yaw, unit)
            for (s in -1..1 step 2) {
                rig.tube(s * 0.80f, 2.55f, -3.95f, s * 0.80f, 2.55f, -3.05f, 0.32f, 7, Theme.shade(body, 0.95f))
            }
            rig.tube(1.32f, 2.55f, -1.20f, 1.42f, 4.35f, -1.60f, 0.04f, 4, DARK)
        }
    }

    /** نفربر زرهی چرخ‌دار ۸×۸. */
    fun apc(rig: Rig, x: Float, z: Float, yaw: Float, unit: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, unit)
        val body = skin(destroyed, OLIVE)
        val hide = skinTex(destroyed, camo)
        rig.taper(0f, 0.95f, -0.4f, 1.48f, 3.90f, 1.30f, 3.60f, 1.10f, body, hide)
        rig.wedge(0f, 0.95f, 3.05f, 2.80f, 2.00f, 0.35f, 1.05f, body, hide)
        rig.box(0f, 2.05f, -1.20f, 2.30f, 0.55f, 4.00f, Theme.shade(body, 1.05f), hide)
        // برجک تیربار
        rig.tube(0f, 2.60f, -0.30f, 0f, 3.05f, -0.30f, 0.62f, 8, Theme.shade(body, 1.08f))
        rig.tube(0f, 2.92f, -0.10f, 0f, 2.98f, 1.55f, 0.07f, 5, DARK)
        if (detail == 0) {
            for (s in -1..1 step 2) {
                for (i in 0..3) {
                    val wz = -2.70f + i * 1.85f
                    rig.tube(s * 1.30f, 0.62f, wz, s * 1.62f, 0.62f, wz, 0.62f, 7, TRACK, false)
                }
            }
            rig.box(0f, 1.90f, 3.30f, 1.70f, 0.42f, 0.12f, GLASS)
        }
    }

    /** کامیون باری نظامی با چادر بار. */
    fun truck(rig: Rig, x: Float, z: Float, yaw: Float, unit: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, unit)
        val body = skin(destroyed, OLIVE)
        val hide = skinTex(destroyed, camo)
        rig.box(0f, 0.75f, 0f, 2.40f, 0.45f, 7.60f, Theme.shade(body, 0.85f), hide)
        rig.box(0f, 1.20f, 2.30f, 2.40f, 1.55f, 2.40f, body, hide)          // کابین
        rig.box(0f, 2.05f, 3.42f, 2.10f, 0.62f, 0.14f, GLASS)               // شیشه
        rig.taper(0f, 1.20f, -1.55f, 1.24f, 3.10f, 1.16f, 3.05f, 1.85f,
            skin(destroyed, 0xFF6B6A4F.toInt()), hide)                       // چادر بار
        if (detail == 0) {
            for (s in -1..1 step 2) {
                rig.tube(s * 0.95f, 0.62f, 2.45f, s * 1.22f, 0.62f, 2.45f, 0.62f, 7, TRACK, false)
                rig.tube(s * 0.95f, 0.62f, -1.35f, s * 1.22f, 0.62f, -1.35f, 0.62f, 7, TRACK, false)
                rig.tube(s * 0.95f, 0.62f, -2.55f, s * 1.22f, 0.62f, -2.55f, 0.62f, 7, TRACK, false)
            }
            rig.box(0f, 2.78f, 3.30f, 1.50f, 0.14f, 0.30f, DARK)
        }
    }

    // ============================================================ پدافند هوایی

    /** پرتابگر متحرک موشک زمین‌به‌هوا با چهار لانچر شیب‌دار. */
    fun samLauncher(
        rig: Rig, x: Float, z: Float, bodyYaw: Float, aimYaw: Float, aimPitch: Float,
        unit: Float, alive: Boolean, detail: Int, y: Float = 0f
    ) {
        val dead = !alive
        val body = skin(dead, OLIVE)
        val hide = skinTex(dead, camo)
        rig.place(x, y, z, bodyYaw, unit)
        // شاسی ۸×۸
        rig.taper(0f, 0.90f, 0f, 1.55f, 5.40f, 1.42f, 5.10f, 0.95f, body, hide)
        rig.box(0f, 1.85f, 3.05f, 2.70f, 1.35f, 2.30f, Theme.shade(body, 1.04f), hide)
        rig.box(0f, 2.70f, 4.18f, 2.40f, 0.62f, 0.14f, GLASS)
        if (detail <= 1) {
            for (s in -1..1 step 2) {
                for (i in 0..3) {
                    val wz = -4.10f + i * 2.55f
                    rig.tube(s * 1.36f, 0.66f, wz, s * 1.70f, 0.66f, wz, 0.66f, 7, TRACK, false)
                }
            }
        }
        // پایه‌های تکیه‌گاه هنگام آتش
        if (detail == 0) {
            for (s in -1..1 step 2) {
                rig.tube(s * 1.60f, 0.85f, -3.4f, s * 2.05f, 0.05f, -3.4f, 0.12f, 5, DARK)
            }
        }

        // --- لانچر: چرخش افقی + زاویه‌ی ارتفاع
        val elev = aimPitch.coerceIn(0.18f, 1.15f)
        rig.place(x, y, z, aimYaw, unit)
        rig.box(0f, 1.85f, -1.30f, 2.30f, 0.70f, 2.60f, Theme.shade(body, 0.95f), hide)
        val baseY = 2.55f
        val len = 6.9f
        val dy = sin(elev) * len
        val dz = -cos(elev) * len
        for (i in 0..3) {
            val sx = (if (i % 2 == 0) -1f else 1f) * 0.62f
            val oy = if (i < 2) 0f else 0.86f
            val ox = sx * (if (i < 2) 1f else 0.92f)
            rig.tube(
                ox, baseY + oy, -1.30f,
                ox + dy * 0.02f, baseY + oy + dy, -1.30f + dz,
                0.42f, 7, if (dead) CHAR else 0xFF6E7358.toInt(), true, hide
            )
        }
        // قاب نگهدارنده
        rig.box(0f, baseY - 0.30f, -1.30f, 2.55f, 0.34f, 1.10f, Theme.shade(body, 0.88f), hide)
        if (detail == 0) {
            rig.tube(0f, baseY + 0.40f, -0.30f, dy * 0.10f, baseY + 0.40f + dy * 0.16f, -0.30f + dz * 0.16f,
                0.09f, 5, DARK)
        }
    }

    /** توپ پدافند خودکشیده با رادار جست‌وجو و دو لوله‌ی ۳۵ میلی‌متری. */
    fun aaGun(
        rig: Rig, x: Float, z: Float, bodyYaw: Float, aimYaw: Float, aimPitch: Float,
        unit: Float, alive: Boolean, flash: Float, detail: Int, y: Float = 0f
    ) {
        val dead = !alive
        val body = skin(dead, OLIVE)
        val hide = skinTex(dead, camo)
        rig.place(x, y, z, bodyYaw, unit)
        for (s in -1..1 step 2) {
            rig.box(s * 1.42f, 0f, 0f, 0.56f, 0.98f, 6.20f, skin(dead, TRACK), skinTex(dead, trackTex))
            if (detail == 0) {
                for (i in 0..4) {
                    val wz = -2.40f + i * 1.20f
                    rig.tube(s * 1.42f - 0.30f, 0.50f, wz, s * 1.42f + 0.26f, 0.50f, wz, 0.42f, 6,
                        Theme.shade(skin(dead, TRACK), 1.25f), false)
                }
            }
        }
        rig.box(0f, 0.55f, 0f, 2.90f, 0.85f, 5.70f, Theme.shade(body, 0.94f), hide)
        rig.wedge(0f, 1.40f, 2.10f, 2.90f, 1.60f, 0.18f, 0.70f, body, hide)

        // --- برجک چرخان
        rig.place(x, y, z, aimYaw, unit)
        rig.taper(0f, 1.40f, -0.40f, 1.45f, 1.70f, 1.20f, 1.45f, 1.25f, body, hide)
        // رادار جست‌وجوی چرخان روی سقف برجک
        rig.disc(0f, 2.72f, -1.30f, 0f, 0.42f, -0.90f, 1.05f, 9, skin(dead, PALE))
        rig.tube(0f, 2.65f, -1.30f, 0f, 3.10f, -1.30f, 0.10f, 5, DARK)
        // رادار پیگیری جلو
        rig.disc(0f, 2.60f, 0.95f, 0f, 0.25f, 0.96f, 0.62f, 9, skin(dead, PALE))
        val elev = aimPitch.coerceIn(-0.05f, 1.30f)
        val len = 3.9f
        val dy = sin(elev) * len
        val dz = cos(elev) * len
        for (s in -1..1 step 2) {
            val bx = s * 0.52f
            rig.tube(bx, 2.05f, 0.55f, bx, 2.05f + dy, 0.55f + dz, 0.10f, 6, Theme.shade(DARK, 1.35f))
            if (flash > 0f && alive) {
                rig.tube(bx, 2.05f + dy, 0.55f + dz, bx, 2.05f + dy * 1.12f, 0.55f + dz * 1.12f,
                    0.34f * (flash / 0.12f).coerceIn(0f, 1f), 6, Theme.AMBER, true)
            }
        }
        if (detail == 0) {
            rig.box(0f, 1.95f, -1.05f, 2.20f, 0.55f, 0.70f, Theme.shade(body, 0.9f), hide)
            rig.box(0f, 2.20f, 1.35f, 1.10f, 0.30f, 0.12f, GLASS)
        }
    }

    /** ایستگاه رادار جست‌وجو با آرایه‌ی چرخان روی برج.
     *  [baseY] ارتفاع سکوی زیر ایستگاه است: روی زمین صفر، روی جزیره/عرشه
     *  ارتفاع همان سکو — تا رادار در مراحل دریایی روی آب شناور نماند. */
    fun radarStation(rig: Rig, x: Float, z: Float, spin: Float, unit: Float, detail: Int, baseY: Float = 0f) {
        rig.place(x, baseY, z, 0f, unit)
        rig.box(0f, 0f, 0f, 11f, 0.40f, 11f, 0xFF7E7C74.toInt(), concrete, concrete)
        // ساختمان عملیات
        rig.box(-3.4f, 0.40f, -0.6f, 5.6f, 3.30f, 8.4f, 0xFF848880.toInt(), metal, roofTex)
        rig.box(-3.4f, 3.70f, -0.6f, 6.0f, 0.35f, 8.8f, 0xFF6A6E68.toInt(), metal, roofTex)
        if (detail <= 1) {
            rig.box(-3.4f, 1.55f, 3.55f, 4.0f, 1.10f, 0.16f, GLASS)
            rig.box(-6.3f, 0.40f, -0.6f, 0.20f, 2.40f, 1.30f, Theme.shade(DARK, 1.2f))
            rig.tube(-1.1f, 4.05f, -3.4f, -1.1f, 5.6f, -3.4f, 0.14f, 5, PALE)
            rig.disc(-1.1f, 5.9f, -3.4f, -0.5f, 0.5f, 0.7f, 1.15f, 9, 0xFFC6C8BE.toInt())
        }
        // برج چهارپایه
        val towerX = 3.4f
        for (sx in -1..1 step 2) {
            for (sz in -1..1 step 2) {
                rig.tube(towerX + sx * 2.6f, 0.40f, sz * 2.6f, towerX + sx * 0.95f, 8.60f, sz * 0.95f,
                    0.22f, 5, STEEL)
            }
        }
        if (detail <= 1) {
            for (level in 1..4) {
                val ly = 0.40f + level * 1.7f
                val sp = 2.6f - level * 0.40f
                rig.tube(towerX - sp, ly, -sp, towerX + sp, ly, sp, 0.09f, 4, Theme.shade(STEEL, 0.85f), false)
                rig.tube(towerX + sp, ly, -sp, towerX - sp, ly, sp, 0.09f, 4, Theme.shade(STEEL, 0.85f), false)
                rig.tube(towerX - sp, ly, -sp, towerX + sp, ly, -sp, 0.08f, 4, Theme.shade(STEEL, 0.8f), false)
            }
        }
        rig.box(towerX, 8.60f, 0f, 4.6f, 0.50f, 4.6f, 0xFF9A9E94.toInt(), metal, metal)

        // آرایه‌ی چرخان: محور چرخش دقیقاً روی مرکز برج است تا صفحه در هوا نچرخد
        rig.place(x + towerX * unit, baseY, z, spin, unit)
        rig.taper(0f, 9.10f, 0.35f, 6.60f, 0.42f, 6.20f, 0.34f, 4.40f,
            PALE, metal, metal, 0f, -0.30f)
        rig.box(0f, 9.10f, 0.75f, 13.4f, 0.22f, 0.28f, 0xFF5E655F.toInt())
        rig.box(0f, 13.30f, 0.30f, 12.8f, 0.24f, 0.30f, 0xFF5E655F.toInt())
        rig.box(0f, 9.90f, -1.30f, 2.20f, 1.20f, 1.60f, Theme.shade(STEEL, 0.9f), metal, metal)
        if (detail <= 1) {
            for (sgn in -1..1 step 2) {
                rig.tube(sgn * 6.2f, 9.20f, 0.35f, sgn * 1.4f, 9.20f, -1.20f,
                    0.10f, 4, Theme.shade(STEEL, 0.85f), false)
            }
            rig.tube(0f, 13.50f, 0.30f, 0f, 15.60f, 0.10f, 0.12f, 5, PALE)
            rig.box(0f, 15.60f, 0.10f, 0.5f, 0.5f, 0.5f, Theme.RED)
        }
}

    /** دکل خرپای مخابراتی / اخلال با نوارهای هوانوردی. */
    fun latticeMast(rig: Rig, x: Float, z: Float, height: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, 0f, 1f)
        val steel = if (destroyed) CHAR else 0xFFA9AAA2.toInt()
        val red = if (destroyed) CHAR else REDBAND
        rig.box(0f, 0f, 0f, 9.0f, 0.6f, 9.0f, 0xFF8E8C82.toInt(), concrete, concrete)
        val segments = if (detail == 0) 8 else 5
        val segH = height / segments
        for (s in 0 until segments) {
            val y0 = 0.6f + s * segH
            val y1 = y0 + segH
            val r0 = 3.6f * (1f - s.toFloat() / segments * 0.62f)
            val r1 = 3.6f * (1f - (s + 1).toFloat() / segments * 0.62f)
            val color = if (s % 2 == 0) steel else red
            for (sx in -1..1 step 2) {
                for (sz in -1..1 step 2) {
                    rig.tube(sx * r0, y0, sz * r0, sx * r1, y1, sz * r1, 0.20f, 4, color, false)
                }
            }
            if (detail <= 1) {
                rig.tube(-r0, y0, -r0, r1, y1, -r1, 0.10f, 3, Theme.shade(color, 0.9f), false)
                rig.tube(r0, y0, -r0, -r1, y1, -r1, 0.10f, 3, Theme.shade(color, 0.9f), false)
                rig.tube(-r0, y0, r0, r1, y1, r1, 0.10f, 3, Theme.shade(color, 0.9f), false)
                rig.tube(-r0, y1, -r1, r0, y1, r1, 0.09f, 3, Theme.shade(color, 0.8f), false)
            }
        }
        // آنتن‌های بشقابی و سکتوری
        if (!destroyed && detail <= 1) {
            rig.disc(2.4f, height * 0.52f, 0f, 0.9f, 0.1f, 0.2f, 1.90f, 9, 0xFFCACCC2.toInt())
            rig.disc(-2.2f, height * 0.72f, 0.4f, -0.9f, 0.05f, 0.3f, 1.40f, 9, 0xFFCACCC2.toInt())
            for (k in 0..2) {
                val a = k * 2.094f
                rig.box(cos(a) * 2.1f, height * 0.86f, sin(a) * 2.1f, 0.34f, 2.20f, 0.24f, 0xFFD2D4CA.toInt())
            }
        }
        rig.tube(0f, 0.6f + height, 0f, 0f, 0.6f + height + 4.2f, 0f, 0.14f, 5, steel)
        rig.box(0f, 0.6f + height + 4.2f, 0f, 0.60f, 0.60f, 0.60f, if (destroyed) CHAR else Theme.RED)
    }

    // ================================================================== دریایی

    /**
     * ناوشکن: بدنه‌ی باریک‌شونده، عرشه، عرشه‌ی فرماندهی چندطبقه، دودکش،
     * دکل رادار، برجک توپ، سلول‌های پرتاب عمودی و سطح فرود بالگرد.
     */
    fun warship(
        rig: Rig, x: Float, z: Float, yaw: Float, beam: Float, height: Float, length: Float,
        destroyed: Boolean, detail: Int
    ) {
        val sink = if (destroyed) -height * 0.28f else 0f
        rig.place(x, sink, z, yaw, 1f)
        val hullC = skin(destroyed, HAZE)
        val hullT = skinTex(destroyed, metal)
        val deckC = skin(destroyed, DECK)
        val deckT = skinTex(destroyed, deckTex)
        val supC = skin(destroyed, Theme.shade(HAZE, 1.08f))

        val half = length * 0.5f
        val b = beam * 0.5f
        val deckY = height * 0.55f
        // بدنه در شش مقطع: باریک‌شدن به سمت دماغه و پاشنه
        val zs = floatArrayOf(-half, -half * 0.70f, -half * 0.25f, half * 0.22f, half * 0.62f, half * 0.88f, half)
        val ws = floatArrayOf(b * 0.80f, b * 0.97f, b, b * 0.96f, b * 0.74f, b * 0.40f, b * 0.05f)
        val ks = floatArrayOf(b * 0.52f, b * 0.66f, b * 0.70f, b * 0.62f, b * 0.42f, b * 0.18f, b * 0.03f)
        val sheer = floatArrayOf(0f, 0f, 0.02f, 0.06f, 0.14f, 0.24f, 0.34f)
        // جعبهٔ کل کشتی — مرجع مشترک لنگر وجه‌ها. بدنه با حاشیهٔ ۴ (یک لایه
        // عقب‌تر از عرشه) لنگر می‌گیرد؛ عرشه با ۲.
        val shipBox = floatArrayOf(0f, height * 1.25f, 0f, b, height * 1.25f, half)
        // در فاصله‌ی زیاد مقاطع دوتایی ادغام می‌شوند تا تعداد وجه‌ها پایین بماند
        val stride = if (detail == 2) 2 else 1
        var i = 0
        rig.beginPart(Rig.PART_HULL)
        while (i < 6) {
            val j = (i + stride).coerceAtMost(6)
            val lift = (sheer[i] + sheer[j]) * 0.5f * height
            rig.hull(zs[i], zs[j], ws[i], ws[j], 0.18f, deckY + lift, ks[i], ks[j], hullC, hullT, deckT,
                farBox = shipBox)
            i = j
        }
        rig.endPart()
        // عرشه‌ی صاف روی بدنه (برچسب تشخیصی روی وجه رویی — فقط برای تست)
        // کلید عمق وجه رویی از دورترین رأس: وگرنه سازه‌های آن‌سوی مرکز عرشه
        // زیر عرشهٔ خودشان می‌روند (رگرسیون ShipDeckOrderTest).
        rig.beginPart(Rig.PART_DECK_PLATE)
        rig.markNextTop(Rig.TAG_SHIP_DECK)
        // بدون maxDepth: کلید وجه رویی باید دقیقاً «دورترین گوشه + ۲» باشد تا
        // بدنه با حاشیهٔ ۴ یک لایهٔ **قطعی** عقب‌تر از آن بنشیند. با
        // maxDepth، کلید عرشه به اندازهٔ (دورترین رأس − میانگین) عمیق‌تر
        // می‌شد و در برخی ژست‌ها از لایهٔ بدنه هم عمیق‌تر می‌شد.
        rig.box(0f, deckY, -half * 0.05f, b * 1.86f, 0.22f, length * 0.72f, deckC, deckT, deckT,
            farBox = shipBox)
        rig.clearTopMark()
        rig.endPart()

        // --- برجک توپ اصلی روی سینه
        val gunZ = half * 0.58f
        rig.beginPart(Rig.PART_ATTACH)
        rig.taper(0f, deckY + 0.22f, gunZ, b * 0.52f, b * 0.62f, b * 0.34f, b * 0.44f, height * 0.30f,
            supC, hullT, hullT, 0f, -b * 0.06f)
        rig.tube(0f, deckY + height * 0.42f, gunZ + b * 0.30f, 0f, deckY + height * 0.46f, gunZ + b * 1.55f,
            beam * 0.030f, 7, Theme.shade(DARK, 1.4f))
        rig.endPart()

        // --- سلول‌های پرتاب عمودی
        val vlsZ = half * 0.34f
        rig.beginPart(Rig.PART_SEATED)
        rig.box(0f, deckY + 0.22f, vlsZ, b * 1.05f, height * 0.10f, length * 0.10f, Theme.shade(supC, 0.92f), hullT)
        if (detail <= 1) {
            // درپوش سلول‌ها حجم نشسته روی بلوک VLS است — با بایاس نشسته تا
            // روی سطح بلوک نلرزد.
            val vlsTopY = deckY + 0.22f + height * 0.10f
            for (cx in 0..3) {
                for (cz in 0..1) {
                    rig.boxSeated(
                        (cx - 1.5f) * b * 0.24f, vlsTopY, vlsZ + (cz - 0.5f) * length * 0.045f,
                        b * 0.17f, height * 0.02f, length * 0.035f, DARK,
                        surfaceX = 0f, surfaceY = vlsTopY, surfaceZ = vlsZ
                    )
                }
            }
        }
        rig.endPart()

        // --- جزیره‌ی فرماندهی: سه طبقه‌ی باریک‌شونده و پل با شیشه‌ی تیره
        var sy = deckY + 0.22f
        var sw = b * 1.42f
        var sl = length * 0.20f
        var sz = half * 0.06f
        for (tier in 0..2) {
            val th = height * (0.34f - tier * 0.05f)
            rig.beginPart(Rig.PART_ISLAND_TIER + tier)
            // طبقه‌های ۱ و ۲ روی مرکزِ بامِ طبقهٔ قبلی می‌نشینند: بامِ هر طبقه
            // از تهِ طبقهٔ بالایی پهن‌تر است و حلقهٔ دفن‌شدهٔ بام با دیوارهای
            // طبقهٔ بالا در کلید مرتب‌سازی تقریباً برابر می‌شوند و در
            // ارتفاع‌های نزدیکِ دوربین می‌لرزند. sz بعد از طبقهٔ قبلی
            // کم شده، پس sz + 0.012·length مرکز طبقهٔ زیرین است.
            if (tier > 0) {
                rig.taper(0f, sy, sz, sw, sl * 0.5f, sw * 0.86f, sl * 0.45f, th, supC, hullT, hullT,
                    seatBias = 1.5f, surfaceX = 0f, surfaceY = sy, surfaceZ = sz + length * 0.012f)
            } else {
                rig.taper(0f, sy, sz, sw, sl * 0.5f, sw * 0.86f, sl * 0.45f, th, supC, hullT, hullT)
            }
            rig.endPart()
            if (tier == 1) {
                // پنجره‌های پل روی وجه‌های طبقه. پنل نازک است و دیوار میزبانش
                // یک چهارضلعی بزرگ: اگر با عمق میانگین خودش مرتب شود، از
                // ارتفاعی پایین‌تر از پنجره (پروفایل پرواز کاربر) دیوار
                // میانگینش نزدیک‌تر می‌شود و روی پنل را می‌پوشاند. با بایاس
                // قفل‌شده روی همان وجه (compress = ۱) کلید پنل همیشه
                // depth(دیوار) − ۱ است، پس هیچ‌وقت پشت دیوار نمی‌رود.
                rig.beginPart(Rig.PART_PANEL)
                val wallY = sy + th * 0.5f
                rig.beginBias(1f, 0f, wallY, sz + sl * 0.48f, compress = 1f)
                rig.box(0f, sy + th * 0.55f, sz + sl * 0.48f, sw * 1.62f, th * 0.30f, 0.16f, GLASS)
                rig.endBias()
                for (s in -1..1 step 2) {
                    rig.beginBias(1f, s * sw * 0.93f, wallY, sz, compress = 1f)
                    rig.box(s * sw * 0.86f, sy + th * 0.55f, sz, 0.16f, th * 0.30f, sl * 0.7f, GLASS)
                    rig.endBias()
                }
                rig.endPart()
            }
            // آرایه‌ی رادار فازی روی بدنه‌ی جزیره
            if (tier == 0 && detail <= 1) {
                rig.beginPart(Rig.PART_PANEL)
                val wallY = sy + th * 0.5f
                rig.beginBias(1f, 0f, wallY, sz + sl * 0.47f, compress = 1f)
                rig.box(0f, sy + th * 0.40f, sz + sl * 0.47f, sw * 1.10f, th * 0.44f, 0.18f, 0xFF3C4A50.toInt())
                rig.endBias()
                for (s in -1..1 step 2) {
                    rig.beginBias(1f, s * sw * 0.93f, wallY, sz - sl * 0.05f, compress = 1f)
                    rig.box(s * sw * 0.84f, sy + th * 0.40f, sz - sl * 0.10f, 0.18f, th * 0.44f, sl * 0.55f,
                        0xFF3C4A50.toInt())
                    rig.endBias()
                }
                rig.endPart()
            }
            sy += th
            sw *= 0.80f
            sl *= 0.80f
            sz -= length * 0.012f
        }

        // --- دکل رادار: چهار پایهٔ همگرا + دو تیر عرضی که دقیقاً بین همان
        // پایه‌ها کشیده می‌شوند (قبلاً تیرها از مرکز خالی شروع می‌شدند و
        // به هیچ‌چیز وصل نبودند: دو میلهٔ شناور کنار پل).
        val mastBase = sy
        val mastTop = mastBase + height * 0.58f
        fun legX(s: Int, t: Float) = s * (sw * 0.8f + (sw * 0.22f - sw * 0.8f) * t)
        fun legZ(g: Int, t: Float) = sz + g * (sl * 0.35f + (sl * 0.10f - sl * 0.35f) * t)
        rig.beginPart(Rig.PART_MAST)
        for (sx in -1..1 step 2) {
            for (szg in -1..1 step 2) {
                rig.tube(legX(sx, 0f), mastBase, legZ(szg, 0f), legX(sx, 1f), mastTop,
                    legZ(szg, 1f), beam * 0.020f, 4, Theme.shade(supC, 0.9f), false)
            }
        }
        for (t in floatArrayOf(0.32f, 0.66f)) {
            for (g in -1..1 step 2) {
                rig.tube(legX(-1, t), mastBase + height * 0.58f * t, legZ(g, t),
                    legX(1, t), mastBase + height * 0.58f * t, legZ(g, t),
                    beam * 0.008f, 3, Theme.shade(supC, 1.05f), false)
            }
        }
        rig.endPart()
        rig.beginPart(Rig.PART_ATTACH)
        rig.box(0f, mastTop, sz, sw * 0.9f, height * 0.05f, sl * 0.6f, supC, hullT)
        rig.endPart()
        if (detail <= 1) {
            // میلهٔ عمودی از روی سکو تا نوک؛ بشقاب دقیقاً روی سرِ همین میله
            // می‌نشیند (مرکز بشقاب = سر میله) — قطعهٔ شناور در کار نیست.
            rig.beginPart(Rig.PART_MAST_ARM)
            rig.tube(0f, mastTop + height * 0.05f, sz, 0f, mastBase + height * 0.96f, sz,
                beam * 0.014f, 5, PALE)
            rig.endPart()
            rig.beginPart(Rig.PART_DISH)
            rig.disc(0f, mastBase + height * 0.96f, sz, 0f, 0.30f, 0.95f, beam * 0.16f, 9, PALE)
            rig.endPart()
        }

        // --- دودکش مایل به عقب
        // دودکش روی عرشه پشت جزیره می‌ایستد، نه داخل آن: نسخهٔ قبل یک‌سوم
        // جلویی‌اش در شیب پشتی جزیره فرو رفته بود و پایه‌اش هم معلق بود.
        // حجم‌های متقاطع زیر مرتب‌ساز نقاشی سوسو می‌زنند. ارتفاع بالا طوری
        // تنظیم شده که نوکش در همان ارتفاع قبلی (deckY + 0.94h) بماند و
        // درپوش موجود دقیقاً رویش بنشیند.
        val fz = -half * 0.26f
        rig.beginPart(Rig.PART_ATTACH)
        rig.taper(0f, deckY + 0.22f, fz, b * 0.58f, length * 0.045f, b * 0.42f, length * 0.032f,
            height * 0.72f, Theme.shade(supC, 0.94f), hullT, hullT, 0f, -length * 0.020f)
        rig.box(0f, deckY + height * 0.94f, fz - length * 0.020f, b * 0.90f, height * 0.04f, length * 0.075f,
            DARK)
        rig.endPart()

        // --- بخش عقب: آشیانه و سطح فرود بالگرد
        rig.beginPart(Rig.PART_ATTACH)
        rig.box(0f, deckY + 0.22f, -half * 0.52f, b * 1.30f, height * 0.32f, length * 0.16f, supC, hullT, hullT)
        rig.endPart()
        // صفحه‌ی پد حجم نشسته روی عرشه است (مرجع = مرکز روی عرشه).
        // اندازه و جای پد طوری تنظیم شده که لبهٔ جلویی‌اش دقیقاً به وجهٔ
        // عقبی آشیانه برسد و دیگر داخل حجم آشیانه فرو نرود (قبلاً ۰٫۰۳ طول
        // کشتی هم‌پوشانی داشت و حجم‌های متقاطع سوسو می‌زدند): بازهٔ پد
        // حالا ‎[-۰٫۵۰، ‎-۰٫۳۴]‎ طول کشتی است.
        val deckRefX = 0f; val deckRefY = deckY + 0.22f; val deckRefZ = -half * 0.05f
        rig.beginPart(Rig.PART_SEATED)
        // وجه رویی صفحهٔ پد برچسب «سطح» می‌گیرد تا تست بتواند حلقه‌ها را
        // دقیقاً با همان وجه بسنجد (نه با درپوش VLS که جای دیگری است).
        rig.markNextTop(Rig.TAG_PAD)
        rig.boxSeated(0f, deckY + 0.24f, -half * 0.84f, b * 1.55f, 0.10f, length * 0.16f,
            Theme.shade(deckC, 0.86f), deckT, deckT, seatBias = 2.2f,
            surfaceX = deckRefX, surfaceY = deckRefY, surfaceZ = deckRefZ)
        rig.clearTopMark()
        rig.endPart()
        if (detail <= 1) {
            // حلقه‌های پد: هم‌خانوادهٔ صفحه‌ی پد (همان مرجع، حاشیهٔ بیشتر)
            // تا با آن چشمک نزنند و همیشه رویش بمانند.
            rig.beginPart(Rig.PART_PAD_RING)
            rig.beginBias(2.8f, deckRefX, deckRefY, deckRefZ)
            rig.disc(0f, deckY + 0.46f, -half * 0.84f, 0f, 1f, 0f, b * 0.62f, 12, 0xFF8C8F82.toInt())
            rig.disc(0f, deckY + 0.52f, -half * 0.84f, 0f, 1f, 0f, b * 0.42f, 12, Theme.shade(deckC, 0.7f))
            rig.endBias()
            rig.endPart()
        }
        // --- سلاح نقطه‌زن و قایق تندرو
        if (detail == 0) {
            rig.beginPart(Rig.PART_ATTACH)
            for (s in -1..1 step 2) {
                val cx = s * b * 0.92f
                rig.tube(cx, deckY + height * 0.36f, -half * 0.34f, cx, deckY + height * 0.50f, -half * 0.34f,
                    beam * 0.055f, 8, PALE)
                rig.disc(cx, deckY + height * 0.51f, -half * 0.34f, 0f, 1f, 0f, beam * 0.055f, 8, PALE)
                rig.tube(cx, deckY + height * 0.50f, -half * 0.32f, cx, deckY + height * 0.56f, -half * 0.24f,
                    beam * 0.018f, 5, DARK)
                rig.box(s * b * 1.05f, deckY + height * 0.24f, half * 0.14f, b * 0.20f, height * 0.10f,
                    length * 0.05f, Theme.shade(supC, 0.88f), hullT)
            }
            rig.endPart()
            // نرده‌های عرشه
            rig.beginPart(Rig.PART_DECK_MARK)
            for (s in -1..1 step 2) {
                rig.tube(s * b * 0.94f, deckY + 0.22f, -half * 0.9f, s * b * 0.94f, deckY + 0.22f, half * 0.72f,
                    beam * 0.006f, 3, PALE, false)
                rig.tube(s * b * 0.94f, deckY + 1.05f, -half * 0.9f, s * b * 0.94f, deckY + 1.05f, half * 0.72f,
                    beam * 0.006f, 3, PALE, false)
            }
            rig.endPart()
        }
        // --- کف موج دماغه روی خط آب
        if (!destroyed && detail <= 1) {
            rig.beginPart(Rig.PART_HULL)
            for (s in -1..1 step 2) {
                rig.face(
                    s * b * 0.20f, 0.16f, half * 0.98f,
                    s * b * 1.35f, 0.16f, half * 0.34f,
                    s * b * 1.20f, 0.16f, -half * 0.40f,
                    s * b * 0.30f, 0.16f, -half * 0.10f,
                    Theme.withAlpha(0xFFE6EFF2.toInt(), 0.42f),
                    farBox = shipBox, farMargin = 4f
                )
            }
            rig.endPart()
        }
    }

    /** ناو هواپیمابر: عرشه‌ی پرواز زاویه‌دار، جزیره در سمت راست و هواپیماهای پارک‌شده. */
    fun carrier(
        rig: Rig, x: Float, z: Float, yaw: Float, beam: Float, height: Float, length: Float,
        destroyed: Boolean, detail: Int
    ) {
        val sink = if (destroyed) -height * 0.22f else 0f
        rig.place(x, sink, z, yaw, 1f)
        val hullC = skin(destroyed, HAZE)
        val hullT = skinTex(destroyed, metal)
        val deckC = skin(destroyed, 0xFF4E5356.toInt())
        val deckT = skinTex(destroyed, deckTex)
        val half = length * 0.5f
        val hb = beam * 0.34f          // نیم‌عرض بدنه
        val db = beam * 0.5f           // نیم‌عرض عرشه‌ی پرواز
        val deckY = height * 0.62f

        val zs = floatArrayOf(-half, -half * 0.6f, 0f, half * 0.55f, half * 0.85f, half)
        val ws = floatArrayOf(hb * 0.86f, hb, hb, hb * 0.9f, hb * 0.52f, hb * 0.08f)
        val ks = floatArrayOf(hb * 0.6f, hb * 0.74f, hb * 0.76f, hb * 0.6f, hb * 0.3f, hb * 0.05f)
        // جعبهٔ کل ناو — باید **همهٔ** سازه‌ها (تا نوک دکل/بشقاب) را دربر بگیرد،
        // وگرنه سازهٔ بیرون‌زده در نمای کم‌ارتفاع می‌تواند از لنگر عمیق‌تر شود
        // و پشت عرشه بیفتد. ارتفاع پوشش: ۰ تا ۲٫۵ برابر ارتفاع ناو.
        val shipBox = floatArrayOf(0f, height * 1.25f, 0f, db, height * 1.25f, half)
        for (i in 0 until 5) {
            rig.beginPart(Rig.PART_HULL)
            // بدنه یک لایه عقب‌تر از عرشه (حاشیهٔ ۴ در برابر ۲): عرشه همیشه
            // روی بدنه می‌افتد و کلید بدنه از محدودهٔ خط‌ها/هواپیماها بیرون
            // است (اندازه‌گیری: پیش از این، وجه رویی بدنه تا ۳۰٬۰۰۰ پیکسل
            // مربع روی عرشه و روی خط فرود می‌افتاد و با آنها می‌لرزید).
            rig.hull(zs[i], zs[i + 1], ws[i], ws[i + 1], 0.2f, deckY, ks[i], ks[i + 1], hullC, hullT, deckT,
                farBox = shipBox)
            rig.endPart()
        }
        // عرشه‌ی پرواز با آویز جانبی. روی عرشه یک چهارضلعی ۲۶۸ متری نیست:
        // در چهار سگمنت طولی کشیده می‌شود تا عمق میانگین هر تکه محلی
        // بماند — با یک وجه‌ی غول‌پیکر، جزیره و هر چیز روی نیمه‌ی دور
        // عرشه با نصف دیگر قاطی می‌شد و لبه‌ها می‌لرزیدند.
        val deckTopY = deckY + height * 0.06f
        rig.beginPart(Rig.PART_DECK_PLATE)
        rig.box(0f, deckY, 0f, db * 2f, height * 0.06f, length * 0.98f, deckC, hullT, deckT,
            topFace = false, allFarBox = shipBox, allFarMargin = 3f)
        // برچسب تشخیصی روی وجه رویی عرشهٔ پرواز (فقط برای تست)
        rig.markBlock(Rig.TAG_SHIP_DECK)
        // عرشهٔ پرواز یک چهارضلعیِ یکپارچه است: سگمنت‌بندیِ قبلی لازم بود
        // چون کلید هر قطعه میانگینِ مرکز خودش بود؛ حالا کلید عرشه به
        // دورترین گوشهٔ کل کشتی لنگر است و یک وجه همان کلید را دارد —
        // و بافت هم دیگر سر هر درز از اول شروع نمی‌شود (حس «تیکه‌تیکه»).
        // کلید عمق عرشه از دورترین رأس + لنگر به دورترین گوشهٔ
        // کل کشتی (رگرسیون ShipDeckOrderTest).
        rig.face(-db, deckTopY, -half * 0.98f, db, deckTopY, -half * 0.98f,
            db, deckTopY, half * 0.98f, -db, deckTopY, half * 0.98f,
            deckC, deckT, farBox = shipBox)
        rig.unmarkBlock()
        rig.endPart()
        rig.markBlock(Rig.TAG_SHIP_PART)
        // ------------------------------------------------------------------
        // یک مرجع مشترک برای *همهٔ* تجهیزات روی عرشه (خط فرود، خط مرکزی،
        // منجنیق، هواپیمای پارک‌شده). قبلاً هر سگمنت مرجع خودش را داشت و
        // همین باعث می‌شد دو قطعهٔ مجاور که به دو سگمنت مختلف افتاده‌اند
        // کلیدهایی با ده‌ها متر اختلاف بگیرند و ترتیبشان با چرخش دوربین
        // برگردد. با مرجع واحد، اختلاف کلید = ۰٫۰۳ × اختلاف عمق واقعی
        // (هم‌علامت و پایدار) و حاشیه‌ها لایه‌بندی را ثابت می‌کنند:
        //   عرشه (لنگر ۲) ← نشانه‌های کف (۱٫۵) ← خط فرود (۱٫۸) ←
        //   تجهیزات ایستاده/هواپیما (۲٫۲) ← حلقه‌های پد (۲٫۸، در ناوشکن)
        // ------------------------------------------------------------------
        val deckRefX = 0f; val deckRefY = deckTopY; val deckRefZ = 0f
        if (detail <= 1) {
            val stripY = deckY + height * 0.062f
            fun stripPortX(z: Float) = -db * 0.86f + (z + half * 0.92f) / (half * 1.54f) * (db * 0.86f)
            fun stripStbdX(z: Float) = -db * 0.20f + (z + half * 0.92f) / (half * 1.54f) * (db * 0.54f)
            val zb = floatArrayOf(-half * 0.92f, -half * 0.49f, 0f, half * 0.49f, half * 0.62f)
            rig.beginPart(Rig.PART_DECK_MARK)
            rig.beginBias(1.8f, deckRefX, deckRefY, deckRefZ)
            for (i in 0..3) {
                val z0 = zb[i]; val z1 = zb[i + 1]
                rig.face(stripPortX(z0), stripY, z0,
                    stripStbdX(z0), stripY, z0,
                    stripStbdX(z1), stripY, z1,
                    stripPortX(z1), stripY, z1,
                    0xFF3E4346.toInt(), deckT)
            }
            rig.endBias()
            // خط‌های مرکزی و منجنیق‌ها
            rig.beginBias(1.5f, deckRefX, deckRefY, deckRefZ)
            for (k in -3..3) {
                val dz = k * length * 0.12f
                rig.box(-db * 0.53f + k * 0.02f, deckTopY + height * 0.0005f, dz,
                    0.55f, 0.05f, length * 0.055f, 0xFFD8D6C4.toInt(), deckT)
            }
            // دو منجنیق جلو
            for (s in -1..0) {
                rig.box(s * db * 0.42f + db * 0.10f, deckTopY + height * 0.0005f, half * 0.52f,
                    0.75f, 0.05f, length * 0.30f, 0xFF35393B.toInt(), deckT)
            }
            rig.endBias()
            rig.endPart()
        }
        // جزیره در سمت راست — همهٔ طبقه‌ها با یک مرکز مشترک (iz) روی هم می‌نشینند.
        var sy = deckY + height * 0.06f
        var sw = beam * 0.11f
        var sl = length * 0.11f
        val ix = db * 0.72f
        val iz = -length * 0.04f
        for (tier in 0..3) {
            val th = height * (0.26f - tier * 0.035f)
            rig.beginPart(Rig.PART_ISLAND_TIER + tier)
            // طبقه‌های ۱ تا ۳ مثل ناوشکن روی بام طبقهٔ زیرین می‌نشینند تا
            // حلقهٔ دفن‌شدهٔ بام با دیوارهای طبقهٔ بالا نجنگد.
            if (tier > 0) {
                rig.taper(ix, sy, iz, sw, sl * 0.5f, sw * 0.88f, sl * 0.46f, th,
                    Theme.shade(hullC, 1.06f), hullT, hullT,
                    seatBias = 1.5f, surfaceX = ix, surfaceY = sy, surfaceZ = iz)
            } else {
                rig.taper(ix, sy, iz, sw, sl * 0.5f, sw * 0.88f, sl * 0.46f, th,
                    Theme.shade(hullC, 1.06f), hullT, hullT)
            }
            rig.endPart()
            if (tier == 1) {
                // پنجره‌های پل: پنل نازکی که وجه جلویی طبقه را سوراخ می‌کند.
                // با بایاس قفل‌شده روی همان وجه (compress = ۱) کلیدش همیشه
                // depth(وجه میزبان) − ۱ است، پس از هیچ ارتفاعی پشت دیوار نمی‌رود.
                rig.beginPart(Rig.PART_PANEL)
                rig.beginBias(1f, ix, sy + th * 0.5f, iz + sl * 0.48f, compress = 1f)
                rig.box(ix, sy + th * 0.55f, iz + sl * 0.48f, sw * 1.7f, th * 0.3f, 0.2f, GLASS)
                rig.endBias()
                rig.endPart()
            }
            sy += th
            sw *= 0.84f
            sl *= 0.84f
        }
        // بالاترین بلوک جزیره دقیقاً روی وجه رویی طبقهٔ سوم می‌نشیند (مرکز
        // یکی، بدون آفست z). نسخهٔ قبل با cz = −0.10L و shiftZ یک صفحهٔ
        // جداافتاده می‌ساخت که کنار برج شناور بود.
        val capH = height * 0.16f
        rig.beginPart(Rig.PART_ISLAND_CAP)
        rig.taper(ix, sy, iz, sw, sl * 0.5f, sw * 0.86f, sl * 0.44f, capH,
            Theme.shade(hullC, 0.96f), hullT, hullT,
            seatBias = 1.5f, surfaceX = ix, surfaceY = sy, surfaceZ = iz)
        rig.endPart()
        val capTop = sy + capH
        if (detail <= 1) {
            // ------------------------------------------------------------
            // مجموعهٔ دکل: چهار پایه از سقف جزیره تا سکوی رادار، خود سکو،
            // و یک دوک عمودی که بشقاب **دقیقاً روی سرش** می‌نشیند. قبلاً
            // دیسک ۰٫۱ ارتفاع بالاتر از سر لوله‌ها شناور بود و هیچ‌چیز
            // وصلش نمی‌کرد؛ در رندر هم همین دیده می‌شد.
            // ------------------------------------------------------------
            val platY = capTop + height * 0.18f
            val platH = height * 0.05f
            val platW = sw * 0.62f
            val platL = sl * 0.30f
            rig.beginPart(Rig.PART_MAST)
            for (sx in -1..1 step 2) {
                for (szg in -1..1 step 2) {
                    rig.tube(ix + sx * sw * 0.78f, capTop, iz + szg * sl * 0.38f,
                        ix + sx * platW, platY, iz + szg * platL,
                        beam * 0.010f, 4, Theme.shade(hullC, 1.1f), false)
                }
            }
            rig.endPart()
            rig.beginPart(Rig.PART_ATTACH)
            rig.box(ix, platY, iz, platW * 2f + beam * 0.02f, platH, platL * 2f + beam * 0.02f,
                Theme.shade(hullC, 1.12f), hullT, hullT)
            rig.endPart()
            val platTop = platY + platH
            val dishY = platTop + height * 0.07f
            rig.beginPart(Rig.PART_MAST_ARM)
            rig.tube(ix, platTop, iz, ix, dishY, iz, beam * 0.012f, 5, PALE)
            rig.endPart()
            rig.beginPart(Rig.PART_DISH)
            rig.disc(ix, dishY, iz, 0f, 0.30f, 0.95f, beam * 0.075f, 10, PALE)
            rig.endPart()
        }
        // هواپیماهای پارک‌شده روی عرشه
        if (detail == 0 && !destroyed) {
            rig.beginPart(Rig.PART_JET)
            rig.beginBias(3.0f, deckRefX, deckRefY, deckRefZ)
            for (k in 0..3) {
                jet(rig, -db * 0.74f, deckY + height * 0.062f, -half * 0.66f + k * length * 0.17f, 1f)
            }
            for (k in 0..1) {
                jet(rig, db * 0.30f, deckY + height * 0.062f, -half * 0.78f + k * length * 0.13f, -1f)
            }
            rig.endBias()
            rig.endPart()
        }
        rig.unmarkBlock()
    }

    /** جنگنده‌ی پارک‌شده روی عرشه: بدنه‌ی باریک، بال دلتا و دو دم عمودی. */
    private fun jet(rig: Rig, cx: Float, cy: Float, cz: Float, nose: Float) {
        val body = 0xFF6E747A.toInt()
        val f = if (nose >= 0f) 1f else -1f
        rig.taper(cx, cy, cz, 0.72f, 6.2f, 0.50f, 5.4f, 1.45f, body, metal, metal)
        // بال دلتا
        rig.face(
            cx - 5.4f, cy + 0.72f, cz - f * 1.6f,
            cx, cy + 0.72f, cz + f * 5.6f,
            cx + 5.4f, cy + 0.72f, cz - f * 1.6f,
            cx, cy + 0.72f, cz - f * 3.4f,
            Theme.shade(body, 1.06f), metal
        )
        // دو دم عمودی
        for (sgn in -1..1 step 2) {
            rig.face(
                cx + sgn * 1.15f, cy + 1.45f, cz - f * 4.8f,
                cx + sgn * 1.45f, cy + 1.45f, cz - f * 6.2f,
                cx + sgn * 1.45f, cy + 3.35f, cz - f * 6.0f,
                cx + sgn * 1.15f, cy + 3.35f, cz - f * 3.9f,
                Theme.shade(body, 0.92f)
            )
        }
        // سرپوش خلبان
        rig.box(cx, cy + 1.45f, cz + f * 2.4f, 1.05f, 0.55f, 2.4f, GLASS)
    }

    /** کشتی باری غیرنظامی برای فضاسازی دریا. */
    fun freighter(rig: Rig, x: Float, z: Float, yaw: Float, beam: Float, height: Float, length: Float) {
        rig.place(x, 0f, z, yaw, 1f)
        val hull = 0xFF6A5A4E.toInt()
        val b = beam * 0.5f
        val half = length * 0.5f
        val deckY = height * 0.6f
        val zs = floatArrayOf(-half, -half * 0.4f, half * 0.5f, half * 0.86f, half)
        val ws = floatArrayOf(b * 0.86f, b, b * 0.94f, b * 0.6f, b * 0.08f)
        val shipBox = floatArrayOf(0f, height * 1.25f, 0f, b, height * 1.25f, half)
        for (i in 0 until 4) {
            rig.beginPart(Rig.PART_HULL)
            rig.hull(zs[i], zs[i + 1], ws[i], ws[i + 1], 0.2f, deckY, ws[i] * 0.7f, ws[i + 1] * 0.7f,
                hull, metal, deckTex, farBox = shipBox)
            rig.endPart()
        }
        rig.beginPart(Rig.PART_DECK_PLATE)
        rig.markNextTop(Rig.TAG_SHIP_DECK)
        rig.box(0f, deckY, half * 0.02f, b * 1.7f, 0.2f, length * 0.8f, 0xFF7A6E5E.toInt(), deckTex, deckTex,
            farBox = shipBox)
        rig.clearTopMark()
        rig.endPart()
        // خانه‌ی سکان در عقب
        rig.beginPart(Rig.PART_ATTACH)
        rig.taper(0f, deckY + 0.2f, -half * 0.66f, b * 0.9f, length * 0.07f, b * 0.78f, length * 0.06f,
            height * 0.9f, PALE, metal, metal)
        rig.endPart()
        // شیشهٔ پل: روی همان صفحهٔ وجه جلویی خانه (قبلاً ~۱ متر جلوتر از
        // دیوار شناور بود). بایاس قفل روی وجه میزبان دارد.
        rig.beginPart(Rig.PART_PANEL)
        rig.beginBias(1f, 0f, deckY + 0.2f + height * 0.45f, -half * 0.66f + length * 0.065f, compress = 1f)
        rig.box(0f, deckY + height * 0.75f, -half * 0.66f + length * 0.0628f, b * 1.5f, height * 0.2f, 0.2f, GLASS)
        rig.endBias()
        rig.endPart()
        rig.beginPart(Rig.PART_ATTACH)
        // دودکش روی بامِ خانهٔ سکان می‌نشیند (مثل طبقه‌های جزیره): از دور
        // حلقهٔ بام با دیوار دودکش هم‌کلید می‌شد و می‌لرزید.
        rig.taper(0f, deckY + height * 1.1f, -half * 0.72f, b * 0.3f, length * 0.025f, b * 0.24f, length * 0.02f,
            height * 0.5f, 0xFF4E4A44.toInt(), metal, metal,
            seatBias = 1.5f, surfaceX = 0f, surfaceY = deckY + 0.2f + height * 0.9f, surfaceZ = -half * 0.66f)
        rig.endPart()
        // کانتینرها روی عرشه — باکس ساده با کلید عمق واقعی خودش.
        // قبلاً boxSeated بودند با مرجعِ مرکز عرشه: کلید هر ۱۲ کانتینر به
        // سمت یک نقطه‌ی مرکزی فشرده می‌شد و ترتیب متقابلشان با چند متر خطا
        // برمی‌گشت (همان خانواده‌ی باگ عرشه). حالا که وجه رویی عرشه به
        // دورترین گوشه‌ی کشتی لنگر شده، کانتینر با کلید واقعی همیشه جلوی
        // عرشه می‌ماند و ترتیب کانتینرها نسبت به هم دقیق است.
        // بافت هم نداشتند: metal تیره‌ی بدنه رنگ‌ها را خفه می‌کرد و
        // کانتینرها مثل تکه‌های سیاه بدنه دیده می‌شدند — رنگ تخت خواناست.
        for (k in 0..3) {
            for (row in -1..1) {
                rig.beginPart(Rig.PART_SEATED)
                rig.box(row * b * 0.55f, deckY + 0.2f, -half * 0.2f + k * length * 0.16f,
                    b * 0.5f, height * 0.34f, length * 0.14f,
                    FREIGHT_COLORS[(k + row + 4) % 4])
                rig.endPart()
            }
        }
    }

    /**
     * جزیره‌ی کوچک صخره‌ای با سکوی صاف در بالا — سکوی طبیعی پدافند و رادار
     * در مراحل دریایی تا هیچ سامانه‌ای «روی آب» ننشیند. ارتفاع کل سازه
     * دقیقاً [h] است و روی سکو در y=h تمام می‌شود؛ میزبان‌ها y خود را
     * برابر h می‌گذارند.
     */
    fun island(rig: Rig, x: Float, z: Float, yaw: Float, w: Float, h: Float, d: Float) {
        rig.place(x, 0f, z, yaw, 1f)
        val rock = 0xFF6E6258.toInt()
        val rockTop = 0xFF7D7268.toInt()
        val sand = 0xFFA08D64.toInt()
        // دامنه‌ی ساحلی شنی از خط آب
        rig.taper(0f, 0f, 0f, w * 0.54f, d * 0.54f, w * 0.46f, d * 0.46f, h * 0.22f, sand, concrete, concrete)
        // بدنه‌ی صخره‌ای دوطبقه
        rig.taper(0f, h * 0.18f, 0f, w * 0.46f, d * 0.46f, w * 0.30f, d * 0.30f, h * 0.62f, rock, concrete, concrete)
        rig.taper(0f, h * 0.80f, 0f, w * 0.30f, d * 0.30f, w * 0.26f, d * 0.26f,
            (h - 0.5f - h * 0.80f).coerceAtLeast(0.05f), rockTop, concrete, concrete)
        // سکوی صاف بالا
        rig.box(0f, h - 0.5f, 0f, w * 0.54f, 0.5f, d * 0.54f, sand, concrete, concrete)
        // دو تیغه‌ی سنگی روی دامنه برای شکستن یکنواختی
        rig.taper(w * 0.30f, h * 0.30f, d * 0.18f, w * 0.10f, d * 0.10f, w * 0.03f, d * 0.03f,
            h * 0.30f, rock, concrete, concrete)
        rig.taper(-w * 0.28f, h * 0.26f, -d * 0.22f, w * 0.08f, d * 0.08f, w * 0.02f, d * 0.02f,
            h * 0.24f, rockTop, concrete, concrete)
    }

    // ============================================================== سازه‌ی هدف

    /** سرپناه بتنی با خاک‌ریز، جان‌پناه و روزنه‌ی دید. */
    fun bunker(rig: Rig, x: Float, z: Float, w: Float, h: Float, d: Float, yaw: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val c = if (destroyed) CHAR else 0xFF9A968A.toInt()
        val ct = if (destroyed) burnt else concrete
        // خاک‌ریز محافظ، پایین‌تر و کم‌عرض‌تر از بلوک بتنی
        rig.taper(0f, 0f, 0f, w * 0.62f, d * 0.62f, w * 0.50f, d * 0.50f, h * 0.30f,
            if (destroyed) CHAR else 0xFF9C8A62.toInt(), ct, ct)
        rig.taper(0f, h * 0.30f, 0f, w * 0.46f, d * 0.46f, w * 0.42f, d * 0.42f, h * 0.62f, c, ct, ct)
        // سقف ضخیم بتنی
        rig.box(0f, h * 0.92f, 0f, w * 0.96f, h * 0.16f, d * 0.96f, Theme.shade(c, 1.08f), ct, ct)
        // روزنه‌ی دید و درِ ضدانفجار
        rig.box(0f, h * 0.58f, -d * 0.45f, w * 0.34f, h * 0.14f, 0.5f, 0xFF202527.toInt())
        rig.box(w * 0.26f, h * 0.30f, -d * 0.44f, w * 0.16f, h * 0.52f, 0.6f, Theme.shade(c, 0.78f), ct)
        if (detail <= 1) {
            // کیسه‌های شن جلوی دهانه و لوله‌ی تهویه
            for (k in -2..2) {
                rig.tube(k * w * 0.13f - w * 0.06f, 0.15f, -d * 0.60f,
                    k * w * 0.13f + w * 0.07f, 0.15f, -d * 0.60f, h * 0.09f, 6, 0xFF8A7C60.toInt(), false)
            }
            rig.tube(-w * 0.24f, h * 1.08f, 0f, -w * 0.24f, h * 1.34f, 0f, h * 0.07f, 6,
                Theme.shade(c, 0.9f), true)
            rig.tube(w * 0.26f, h * 1.08f, d * 0.18f, w * 0.26f, h * 1.62f, d * 0.18f, 0.09f, 4, PALE)
        }
}

    /** دهانه‌ی تونل کوهستانی با دهانه‌ی بتنی، ریل و درِ زرهی. */
    fun tunnelMouth(rig: Rig, x: Float, z: Float, w: Float, h: Float, d: Float, yaw: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val rock = if (destroyed) CHAR else 0xFF8A8272.toInt()
        val c = if (destroyed) CHAR else 0xFF9E9A8E.toInt()
        val ct = if (destroyed) burnt else concrete
        // صخره‌ی پشت دهانه؛ عقب‌تر و کوچک‌تر تا خود دهانه دیده شود
        rig.taper(0f, 0f, d * 1.05f, w * 1.15f, d * 1.05f, w * 0.72f, d * 0.70f, h * 1.75f, rock, ct, ct)
        // قاب بتنی دهانه: دو جرز و سرتاق
        rig.box(-w * 0.62f, 0f, 0f, w * 0.32f, h * 1.32f, d * 0.70f, c, ct, ct)
        rig.box(w * 0.62f, 0f, 0f, w * 0.32f, h * 1.32f, d * 0.70f, c, ct, ct)
        rig.box(0f, h * 1.20f, 0f, w * 1.56f, h * 0.30f, d * 0.70f, c, ct, ct)
        // دهانه‌ی تاریک، عمق‌دار
        rig.box(0f, 0f, d * 0.30f, w * 0.90f, h * 1.18f, d * 0.60f, 0xFF14171A.toInt())
        rig.box(0f, 0f, -d * 0.34f, w * 0.90f, h * 1.18f, 0.4f, 0xFF1A1E21.toInt())
        if (detail <= 1) {
            // جاده‌ی دسترسی و ریل
            rig.box(0f, 0f, -d * 1.05f, w * 1.30f, 0.12f, d * 1.6f, 0xFF6E6656.toInt(), ct, ct)
            for (s in -1..1 step 2) {
                rig.box(s * w * 0.20f, 0.12f, -d * 1.05f, 0.18f, 0.14f, d * 1.6f, 0xFF5A5348.toInt())
            }
            // درِ زرهی کشویی و کیسه‌های شن
            rig.box(-w * 1.02f, 0f, -d * 0.02f, w * 0.14f, h * 1.20f, d * 0.62f,
                Theme.shade(c, 0.74f), ct, ct)
            for (k in -1..1) {
                rig.tube(k * w * 0.30f - w * 0.12f, 0.16f, -d * 0.72f,
                    k * w * 0.30f + w * 0.10f, 0.16f, -d * 0.72f, h * 0.11f, 6, 0xFF8A7C60.toInt(), false)
            }
            rig.tube(w * 0.90f, h * 1.55f, d * 0.1f, w * 0.90f, h * 2.30f, d * 0.1f, 0.11f, 4, PALE)
        }
}

    /** سکوی پرتاب / برج دیده‌بانی با گنتری فلزی. */
    fun gantry(rig: Rig, x: Float, z: Float, w: Float, h: Float, d: Float, yaw: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val c = if (destroyed) CHAR else 0xFFA29C8C.toInt()
        val ct = if (destroyed) burnt else concrete
        val steel = if (destroyed) CHAR else 0xFF9AA0A2.toInt()
        // پی بتنی
        rig.box(0f, 0f, 0f, w * 1.7f, h * 0.045f, d * 1.7f, 0xFF8E8A7E.toInt(), ct, ct)
        // ساقه‌ی باریک برج
        rig.taper(0f, h * 0.045f, 0f, w * 0.30f, d * 0.30f, w * 0.20f, d * 0.20f, h * 0.74f, c, ct, ct)
        // چهار پایه‌ی خرپا دور ساقه
        for (sx in -1..1 step 2) {
            for (sz in -1..1 step 2) {
                rig.tube(sx * w * 0.56f, h * 0.045f, sz * d * 0.56f, sx * w * 0.30f, h * 0.80f,
                    sz * d * 0.30f, w * 0.040f, 4, steel, false)
            }
        }
        if (detail <= 1) {
            for (lvl in 1..4) {
                val ly = h * (0.045f + lvl * 0.155f)
                val sp = w * (0.56f - lvl * 0.052f)
                rig.tube(-sp, ly, -sp, sp, ly, sp, w * 0.018f, 3, Theme.shade(steel, 0.88f), false)
                rig.tube(sp, ly, -sp, -sp, ly, sp, w * 0.018f, 3, Theme.shade(steel, 0.88f), false)
                rig.tube(-sp, ly, -sp, sp, ly, -sp, w * 0.016f, 3, Theme.shade(steel, 0.8f), false)
            }
        }
        // اتاقک کنترل با کنسول شیشه‌ای و آویز
        rig.taper(0f, h * 0.79f, 0f, w * 0.44f, d * 0.44f, w * 0.78f, d * 0.78f, h * 0.11f,
            Theme.shade(c, 1.06f), ct, ct)
        rig.box(0f, h * 0.90f, 0f, w * 1.56f, h * 0.09f, d * 1.56f, Theme.shade(c, 1.12f), ct, ct)
        for (s in -1..1 step 2) {
            rig.box(0f, h * 0.905f, s * d * 0.77f, w * 1.54f, h * 0.075f, 0.4f, GLASS)
            rig.box(s * w * 0.77f, h * 0.905f, 0f, 0.4f, h * 0.075f, d * 1.54f, GLASS)
        }
        rig.box(0f, h * 0.99f, 0f, w * 1.40f, h * 0.035f, d * 1.40f, Theme.shade(c, 0.94f), ct, ct)
        if (detail <= 1) {
            // نرده‌ی بام، آنتن و بشقاب مخابراتی
            for (s in -1..1 step 2) {
                rig.tube(-w * 0.70f, h * 1.025f, s * d * 0.70f, w * 0.70f, h * 1.025f, s * d * 0.70f,
                    w * 0.012f, 3, PALE, false)
                rig.tube(s * w * 0.70f, h * 1.025f, -d * 0.70f, s * w * 0.70f, h * 1.025f, d * 0.70f,
                    w * 0.012f, 3, PALE, false)
            }
            rig.disc(w * 0.34f, h * 1.12f, -d * 0.20f, 0.25f, 0.90f, 0.1f, w * 0.30f, 10, PALE)
            rig.tube(-w * 0.34f, h * 1.025f, d * 0.24f, -w * 0.34f, h * 1.24f, d * 0.24f, w * 0.022f, 4, PALE)
            rig.box(-w * 0.34f, h * 1.24f, d * 0.24f, w * 0.10f, w * 0.10f, w * 0.10f, Theme.RED)
        }
}

    /** پل بتنی با پایه‌ها، خرپا و نرده. */
    fun bridgeSpan(rig: Rig, x: Float, z: Float, w: Float, h: Float, d: Float, yaw: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val c = if (destroyed) CHAR else 0xFFA6A296.toInt()
        val ct = if (destroyed) burnt else concrete
        val steel = if (destroyed) CHAR else 0xFF8E9490.toInt()
        val deckH = h * 0.70f
        for (k in -2..2) {
            rig.taper(k * w * 0.20f, 0f, 0f, d * 0.24f, d * 0.28f, d * 0.16f, d * 0.20f, deckH, c, ct, ct)
        }
        rig.box(0f, deckH, 0f, w, h * 0.14f, d, c, ct, ct)
        rig.box(0f, deckH + h * 0.14f, 0f, w, 0.12f, d * 0.78f, 0xFF5E635F.toInt(), concrete, concrete)
        if (detail <= 1) {
            for (s in -1..1 step 2) {
                rig.box(0f, deckH + h * 0.14f, s * d * 0.44f, w, h * 0.16f, d * 0.06f, Theme.shade(c, 0.9f), ct)
                for (k in -6..6) {
                    rig.tube(k * w * 0.075f, deckH + h * 0.30f, s * d * 0.44f,
                        (k + 1) * w * 0.075f, deckH + h * 0.62f, s * d * 0.44f, w * 0.006f, 3, steel, false)
                }
                rig.tube(-w * 0.5f, deckH + h * 0.62f, s * d * 0.44f, w * 0.5f, deckH + h * 0.62f, s * d * 0.44f,
                    w * 0.008f, 4, steel, false)
            }
            for (k in -4..4) {
                rig.box(k * w * 0.11f, deckH + h * 0.145f, 0f, w * 0.045f, 0.04f, d * 0.06f, 0xFFD6D2BE.toInt())
            }
        }
    }

    /** لکوموتیو دیزلی. */
    fun locomotive(rig: Rig, x: Float, z: Float, yaw: Float, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val body = if (destroyed) CHAR else 0xFF5A6470.toInt()
        val bt = if (destroyed) burnt else metal
        rig.box(0f, 0.55f, 0f, 3.10f, 0.70f, 19.0f, Theme.shade(body, 0.8f), bt)
        rig.box(0f, 1.25f, -1.5f, 3.00f, 2.60f, 13.0f, body, bt)
        rig.box(0f, 1.25f, 7.0f, 3.00f, 3.60f, 4.0f, Theme.shade(body, 1.06f), bt)   // کابین
        rig.box(0f, 3.95f, 8.9f, 2.60f, 0.85f, 0.16f, GLASS)
        rig.box(0f, 3.85f, -1.5f, 2.20f, 0.30f, 11.0f, Theme.shade(body, 0.9f), bt)
        if (detail <= 1) {
            for (s in -1..1 step 2) {
                for (i in 0..2) {
                    val wz = -7.5f + i * 2.2f
                    rig.tube(s * 1.05f, 0.52f, wz, s * 1.35f, 0.52f, wz, 0.52f, 7, TRACK, false)
                    rig.tube(s * 1.05f, 0.52f, wz + 11f, s * 1.35f, 0.52f, wz + 11f, 0.52f, 7, TRACK, false)
                }
            }
            rig.tube(0f, 4.15f, 3.0f, 0f, 4.9f, 3.0f, 0.28f, 6, DARK)
            rig.box(0f, 1.05f, 9.6f, 2.4f, 0.5f, 0.5f, DARK)
        }
    }

    /** واگن مهمات با محفظه‌ی سرپوشیده و بار موشک. */
    fun wagon(rig: Rig, x: Float, z: Float, yaw: Float, kind: Int, destroyed: Boolean, detail: Int) {
        rig.place(x, 0f, z, yaw, 1f)
        val body = if (destroyed) CHAR else 0xFF57503F.toInt()
        val bt = if (destroyed) burnt else metal
        rig.box(0f, 0.55f, 0f, 3.10f, 0.70f, 22.0f, Theme.shade(body, 0.75f), bt)
        when (kind) {
            0 -> {
                rig.box(0f, 1.25f, 0f, 3.05f, 3.40f, 21.0f, body, bt)
                rig.box(0f, 4.65f, 0f, 3.05f, 0.25f, 21.0f, Theme.shade(body, 1.08f), bt)
                if (detail <= 1) for (k in -2..2) rig.box(0f, 1.6f, k * 4.0f, 3.14f, 2.4f, 0.2f, Theme.shade(body, 0.86f))
            }
            1 -> {
                rig.tube(0f, 2.6f, -9.5f, 0f, 2.6f, 9.5f, 1.55f, 9, if (destroyed) CHAR else 0xFF7E8288.toInt(), true, bt)
                rig.box(0f, 1.25f, 0f, 2.2f, 1.0f, 20.0f, Theme.shade(body, 0.85f), bt)
            }
            else -> {
                rig.box(0f, 1.25f, 0f, 3.05f, 0.30f, 21.0f, Theme.shade(body, 0.9f), bt)
                if (detail <= 1) {
                    for (k in -1..1) {
                        rig.tube(-0.75f, 2.0f, k * 6.5f, 0.75f, 2.0f, k * 6.5f, 0.95f, 8,
                            if (destroyed) CHAR else 0xFF6E7A54.toInt(), true, bt)
                    }
                }
            }
        }
        if (detail <= 1) {
            for (s in -1..1 step 2) {
                for (i in 0..1) {
                    val wz = -8.6f + i * 2.2f
                    rig.tube(s * 1.05f, 0.52f, wz, s * 1.35f, 0.52f, wz, 0.52f, 7, TRACK, false)
                    rig.tube(s * 1.05f, 0.52f, wz + 15f, s * 1.35f, 0.52f, wz + 15f, 0.52f, 7, TRACK, false)
                }
            }
        }
    }
}
