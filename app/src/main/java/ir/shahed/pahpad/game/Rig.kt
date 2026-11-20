package ir.shahed.pahpad.game

import android.graphics.Bitmap
import ir.shahed.pahpad.core.Theme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ابزار ساخت احجام در دستگاه محلی هر وسیله.
 *
 * قرارداد چرخش عیناً همان `Scene3D.box` است: x' = x·cos − z·sin و z' = x·sin + z·cos،
 * بنابراین جهت (heading) اجسام تازه با محتوای قبلی بازی هم‌خوان می‌ماند.
 *
 * نورپردازی از همان خورشید مشترک `Scene3D.light` می‌آید. جهت نرمالِ هر وجه با مقایسه
 * با مرکز مرجعِ حجم به بیرون چرخانده می‌شود، پس ترتیب رأس‌ها نور را خراب نمی‌کند.
 */
class Rig(private val scene: Scene3D) {

    private var ox = 0f
    private var oy = 0f
    private var oz = 0f
    private var sy = 0f
    private var cy = 1f

    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pc = FloatArray(3)
    private val pd = FloatArray(3)
    private val corner = FloatArray(24)
    private val ringA = FloatArray(3)
    private val ringB = FloatArray(3)

    /** Back-face culling for closed convex solids (hexa, capped tubes) only. */
    private var cull = false

    /** نرمال جهانی وجه بعدی را برای تست ثبت می‌کند (هیچ اثری در رندر ندارد). */
    private fun markNormal(nx: Float, ny: Float, nz: Float) {
        scene.debugNx = nx; scene.debugNy = ny; scene.debugNz = nz
    }

    /** ضریب بزرگنمایی یکنواخت؛ مدل‌ها با ابعاد واقعی (متر) نوشته می‌شوند. */
    var scale = 1f

    /** مرکز مرجع برای بیرون‌گرداندن نرمال‌ها (در دستگاه محلی) */
    var refX = 0f
    var refY = 0f
    var refZ = 0f

    fun place(x: Float, y: Float, z: Float, yaw: Float, unitScale: Float = 1f) {
        ox = x
        oy = y
        oz = z
        sy = sin(yaw)
        cy = cos(yaw)
        scale = if (unitScale > 0.0001f) unitScale else 1f
        refX = 0f
        refY = 0f
        refZ = 0f
    }

    fun ref(x: Float, y: Float, z: Float) {
        refX = x
        refY = y
        refZ = z
    }

    private val depthTmp = FloatArray(3)
    /** مرجع مشترک برای وجه‌های حجمِ نشسته (بازاستفاده، بدون تخصیص هر فریم). */
    private val seatRef = FloatArray(3)

    // ------------------------------------------------- بایاسِ زمینه (context)
    /**
     * بایاس مشترک برای یک **مجموعهٔ چندفراخوانی** (مثل jet که از taper و face
     * و box ساخته می‌شود): تا وقتی beginBias/endBias فعال است، هر وجهی که
     * بایاس صریح ندارد از همین قرارداد می‌گیرد. بدون این، بایاس‌دادن به همهٔ
     * تکه‌های یک حجمِ مرکب عملاً ممکن نبود و تکه‌ها یک‌جا شناور می‌شدند.
     */
    private var ctxBias = 0f
    private var ctxRefX = 0f
    private var ctxRefY = Float.NaN
    private var ctxRefZ = 0f
    private var ctxCompress = SEATED_BIAS_COMPRESS

    fun beginBias(bias: Float, x: Float, y: Float, z: Float, compress: Float = SEATED_BIAS_COMPRESS) {
        ctxBias = bias; ctxRefX = x; ctxRefY = y; ctxRefZ = z; ctxCompress = compress
    }

    fun endBias() { ctxBias = 0f; ctxRefY = Float.NaN }

    /**
     * عمق دید یک نقطهٔ **محلی** — دقیقاً همان عددی که `Scene3D.submit`
     * به‌عنوان `depth` ذخیره می‌کند (camera-space z پس از چرخش و پیچ).
     */
    private fun camDepth(lx: Float, ly: Float, lz: Float): Float {
        world(lx, ly, lz, depthTmp)
        val cam = scene.cam
        val dx = depthTmp[0] - cam.x
        val dy = depthTmp[1] - cam.y
        val dz = depthTmp[2] - cam.z
        val forward = dx * sin(cam.yaw) + dz * cos(cam.yaw)
        return dy * sin(cam.pitch) + forward * cos(cam.pitch)
    }

    private fun world(lx0: Float, ly0: Float, lz0: Float, out: FloatArray) {
        val lx = lx0 * scale
        val ly = ly0 * scale
        val lz = lz0 * scale
        out[0] = ox + lx * cy - lz * sy
        out[1] = oy + ly
        out[2] = oz + lx * sy + lz * cy
    }

    // ------------------------------------------------------------------ وجه

    fun face(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy2: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        color: Int, texture: Bitmap? = null, emissive: Boolean = false, cullBack: Boolean = false,
        tiled: Boolean = false,
        /** بایاس عمق: پنجره‌های روی دیوار باید همیشه جلوتر از دیوار مرتب شوند */
        depthBias: Float = 0f,
        /**
         * بایاس «روی-دیوار»: اگر بزرگ‌تر از صفر باشد، بایاس دقیق محاسبه می‌شود:
         * اختلاف عمق دیدِ مرکز این وجه با نقطهٔ مرجعِ دیوار ([biasRefLocal])
         * به‌علاوهٔ همین مقدار به‌عنوان حاشیه.
         *
         * چرا لازم است: مرتب‌سازی نقاش عمق **میانگین** را مقایسه می‌کند.
         * روی دیوار مایل، عمق میانگین دیوار با عمق پنجرهٔ لبهٔ دور ده‌ها متر
         * فرق دارد؛ هر بایاس ثابتی کافی نیست و پنجره پشت دیوار می‌رود.
         */
        surfaceBias: Float = 0f,
        /** نقطهٔ مرجع دیوار در دستگاه محلی (مرکز همان دیوار) */
        biasRefLocal: FloatArray? = null,
        /**
         * فشردگی تصحیح عمق: ۱ یعنی کلید مرتب‌سازی دقیقاً روی عمقِ سطحِ
         * مرجع قفل می‌شود (رفتار درست برای پنجره‌ها که هم‌سطح دیوارند).
         * برای حجم‌های نشسته روی یک سطح (پاراپت و مکعب روی بام، کانتینر
         * روی عرشه) قفل کامل یعنی همهٔ وجوه یک کلید می‌گیرند و ترتیبشان
         * نسبت به هم به ترتیب رسم واگذار می‌شود: از یک سمت دوربین درست
         * و از سمت دیگر غلط، و نزدیک تساوی، با نویز گردِ عمق میانگین،
         * فریم‌به‌فریم برعکس می‌شود — همان «بی‌ثباتی مکعب بالای خانه».
         * ۰.۹۷ بیشتر تصحیح را نگه می‌دارد ولی ۳٪ اختلاف عمق واقعی وجوه
         * را باقی می‌گذارد تا ترتیب نسبی‌شان هندسی بماند؛ حاشیهٔ
         * seatBias باید از (۱−۰.۹۷)×بیشترین اختلاف عمق روی سطح بزرگ‌تر
         * باشد تا هیچ وجه نشسته‌ای زیر سطح میزبان نرود.
         */
        biasCompress: Float = 1f,
        /**
         * کلید عمق از دورترین رأس، نه میانگین. فقط برای وجه رویی یک سطحِ
         * بزرگِ تخت که چیزهایی رویش نشسته‌اند (عرشهٔ کشتی): میانگین در
         * مرکز سطح می‌نشیند و سازه‌های آن‌سوی مرکز زیر سطحِ خودشان
         * می‌روند. فقط همان وجه رویی — دیوارها و وجه‌های دیگر با میانگین
         * می‌مانند تا ترتیبشان با بقیهٔ دنیا عوض نشود.
         */
        maxDepth: Boolean = false,
        /**
         * لنگر دورِ کشتی: جعبهٔ محدودکنندهٔ کل کشتی در دستگاه محلی، به‌صورت
         * [مرکزx، مرکزy، مرکزz، نیم‌عرضx، نیم‌عرضy، نیم‌عرضz]. اگر داده شود،
         * کلید این وجه روی دورترین گوشهٔ جعبه (در نمای فعلی) + ۲ متر قفل
         * می‌شود — یعنی عرشه همیشه پشتِ **همهٔ** سازه‌های کشتیِ خودش مرتب
         * می‌شود، نه فقط سازه‌های روی همان سگمنت.
         *
         * چرا maxDepth به‌تنهایی کافی نیست: دورترین رأسِ یک سگمنتِ عرشه
         * نزدیکِ کشتی است؛ سازه‌ای که روی سگمنتِ دور نشسته، عمقش از آن
         * بیشتر است و در نمایِ هم‌محور با عرشه، سگمنتِ نزدیک روی آن را
         * می‌پوشاند (۱۴۴ جفتِ باقی‌مانده در ShipDeckOrderTest همین‌ها بودند).
         * دورترین گوشهٔ جعبه، دورترین نقطهٔ ممکنِ کل کشتی است (دوربین بالای
         * کشتی است و هیچ سازه‌ای از جعبه بیرون نمی‌زند)، پس این قفل برای
         * هر سازهٔ همان کشتی کافی است.
         */
        farBox: FloatArray? = null,
        /**
         * حاشیهٔ لنگرِ [farBox] بر حسب متر. کلید = دورترین گوشهٔ جعبه + این حاشیه.
         *
         * چرا لازم شد: وجه‌های **زیر** یک سطح (بدنهٔ زیر عرشه) باید قبل از خود
         * سطح رسم شوند، ولی عرشه با حاشیهٔ ۲ روی دورترین گوشه لنگر شده. بدنه
         * با حاشیهٔ بزرگ‌تر (۴) یک لایه عقب‌تر می‌نشیند: همیشه قبل از عرشه
         * رسم می‌شود (عرشه رویش را می‌پوشاند) و کلیدش از محدودهٔ کلیدِ
         * تجهیزات روی عرشه بیرون می‌رود — همان چیزی که باعث می‌شد وجه‌های
         * بدنه با خط‌های فرود و هواپیماها فریم‌به‌فریم ترتیبشان برگردد.
         */
        farMargin: Float = 2f
    ) {
        world(ax, ay, az, pa)
        world(bx, by, bz, pb)
        world(cx, cy2, cz, pc)
        world(dx, dy, dz, pd)
        val ux = pb[0] - pa[0]; val uy = pb[1] - pa[1]; val uz = pb[2] - pa[2]
        val vx = pd[0] - pa[0]; val vy = pd[1] - pa[1]; val vz = pd[2] - pa[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val inv = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx *= inv; ny *= inv; nz *= inv
        // بیرون‌گرداندن نرمال نسبت به مرکز مرجع
        val mx = (ax + bx + cx + dx) * 0.25f - refX
        val my = (ay + by + cy2 + dy) * 0.25f - refY
        val mz = (az + bz + cz + dz) * 0.25f - refZ
        val wx = mx * cy - mz * sy
        val wz = mx * sy + mz * cy
        if (nx * wx + ny * my + nz * wz < 0f) {
            nx = -nx; ny = -ny; nz = -nz
        }
        if (cull || cullBack) {
            val cam = scene.cam
            val fx = (pa[0] + pb[0] + pc[0] + pd[0]) * 0.25f - cam.x
            val fy = (pa[1] + pb[1] + pc[1] + pd[1]) * 0.25f - cam.y
            val fz = (pa[2] + pb[2] + pc[2] + pd[2]) * 0.25f - cam.z
            if (nx * fx + ny * fy + nz * fz >= 0f) return
        }
        // بایاس سطح — محاسبهٔ **دقیق**، نه تقریبی.
        //
        // مرتب‌سازی نقاش در Scene3D عدد `depth` (عمق میانگین در فضای دوربین)
        // را منهای بایاس مقایسه می‌کند. برای اینکه پنجره همیشه درست روی
        // دیوارِ خودش بیفتد، باید بایاس برابر باشد با:
        //     depth(پنجره) − depth(دیوار) + حاشیه
        // چون عمق دیوار میانگینِ کل دیوار است و عمق پنجره محلی است، این
        // اختلاف روی دیوار مایل به ده‌ها متر می‌رسد. هیچ عدد ثابتی جواب
        // نمی‌دهد — باید برای هر پنجره جدا حساب شود.
        val biasOut = if (farBox != null) {
            val cxL = (ax + bx + cx + dx) * 0.25f
            val cyL = (ay + by + cy2 + dy) * 0.25f
            val czL = (az + bz + cz + dz) * 0.25f
            val faceD = camDepth(cxL, cyL, czL)
            var far = Float.NEGATIVE_INFINITY
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) for (sz in -1..1 step 2) {
                val d = camDepth(farBox[0] + sx * farBox[3], farBox[1] + sy * farBox[4], farBox[2] + sz * farBox[5])
                if (d > far) far = d
            }
            // کلید = عمق میانگین − بایاس ≈ دورترین گوشه + حاشیه.
            faceD - far - farMargin
        } else if (surfaceBias > 0f && biasRefLocal != null) {
            val cxL = (ax + bx + cx + dx) * 0.25f
            val cyL = (ay + by + cy2 + dy) * 0.25f
            val czL = (az + bz + cz + dz) * 0.25f
            val winD = camDepth(cxL, cyL, czL)
            val wallD = camDepth(biasRefLocal[0], biasRefLocal[1], biasRefLocal[2])
            // اگر پنجره از مرکز دیوار دورتر است، بایاس مثبت می‌شود و آن را
            // جلو می‌آورد؛ حاشیه تضمین می‌کند حتی با خطای رُند هم ببرد.
            (winD - wallD) * biasCompress + surfaceBias
        } else if (ctxBias > 0f && ctxRefY.isFinite()) {
            // بایاسِ زمینه: برای حجم‌های چندتکه‌ای (هواپیمای پارک‌شده، پنل
            // روی دیوار، حلقهٔ پد) که از چند فراخوانی ساخته می‌شوند و نمی‌توان
            // به هر کدام بایاس جدا داد. همان قرارداد «نشسته» است: کلید = 
            // depth − [(depth − depth(مرجع))·compress + حاشیه].
            val cxL = (ax + bx + cx + dx) * 0.25f
            val cyL = (ay + by + cy2 + dy) * 0.25f
            val czL = (az + bz + cz + dz) * 0.25f
            (camDepth(cxL, cyL, czL) - camDepth(ctxRefX, ctxRefY, ctxRefZ)) * ctxCompress + ctxBias
        } else depthBias
        markNormal(nx, ny, nz)
        val shaded = if (emissive) color else scene.lit(color, nx, ny, nz)
        if (tiled && texture != null) {
            scene.quadTiled(
                pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
                shaded, texture, maxDepth = maxDepth
            )
        } else if (biasOut != 0f) {
            scene.quadBiased(
                pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
                shaded, texture, biasOut, maxDepth = maxDepth
            )
        } else {
            scene.quad(
                pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
                shaded, texture = texture, maxDepth = maxDepth
            )
        }
    }

    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy2: Float, cz: Float,
        color: Int
    ) {
        world(ax, ay, az, pa)
        world(bx, by, bz, pb)
        world(cx, cy2, cz, pc)
        val ux = pb[0] - pa[0]; val uy = pb[1] - pa[1]; val uz = pb[2] - pa[2]
        val vx = pc[0] - pa[0]; val vy = pc[1] - pa[1]; val vz = pc[2] - pa[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val inv = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx *= inv; ny *= inv; nz *= inv
        val cxL = (ax + bx + cx) / 3f
        val cyL = (ay + by + cy2) / 3f
        val czL = (az + bz + cz) / 3f
        val oxL = cxL - refX; val oyL = cyL - refY; val ozL = czL - refZ
        val oxW = oxL * cy - ozL * sy
        val ozW = oxL * sy + ozL * cy
        if (nx * oxW + ny * oyL + nz * ozW < 0f) { nx = -nx; ny = -ny; nz = -nz }
        markNormal(nx, ny, nz)
        val lit = scene.lit(color, nx, ny, nz)
        if (ctxBias > 0f && ctxRefY.isFinite()) {
            val bias = (camDepth(cxL, cyL, czL) - camDepth(ctxRefX, ctxRefY, ctxRefZ)) * ctxCompress + ctxBias
            scene.triangleBiased(
                pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], pc[0], pc[1], pc[2], lit, bias
            )
            return
        }
        scene.triangle(
            pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], pc[0], pc[1], pc[2],
            lit
        )
    }

    // --------------------------------------------------------------- شش‌وجهی

    /** هشت رأس: ۰..۳ پایین (خلاف ساعت)، ۴..۷ بالا با همان ترتیب. */
    /**
     * مکعب شش‌وجهی.
     *
     * [tiledFaces] = true فقط برای دیوار ساختمان‌ها استفاده می‌شود: بافت با
     * مقیاس متری تکرار می‌شود. بقیهٔ اشیا (تانک، پهپاد، کشتی) تکسچرشان
     * همان‌طور که هست کشیده می‌شود — وگرنه بدنهٔ کوچک با مقیاس ۷ متر داغان
     * می‌شود.
     */
    private fun hexa(p: FloatArray, color: Int, side: Bitmap?, top: Bitmap?, bottom: Boolean = true,
                     tiledFaces: Boolean = false, topFace: Boolean = true, seatBias: Float = 0f,
                     surfaceX: Float = 0f, surfaceY: Float = 0f, surfaceZ: Float = 0f,
                     topMaxDepth: Boolean = false, farBox: FloatArray? = null, farMargin: Float = 2f,
                     allFarBox: FloatArray? = null, allFarMargin: Float = 4f) {
        // برای حجمِ نشسته، هر وجه باید بداند نسبت به **سطح زیرین خود** کجاست.
        // `face` با surfaceBias همین را حساب می‌کند؛ فقط باید مرجع را عوض کنیم
        // و بعد برگردانیم — وگرنه وجوه بعدیِ همین حجم (مثل دیوارها) بایاس غلط
        // می‌گیرند.
        //
        // نکتهٔ کلیدی: مرجع = مرکز سطحِ میزبان (بام/عرشه)، نه مرکز خودِ حجم.
        // اگر مرکز خود حجم باشد، پاراپتِ لبهٔ دور کلیدش کوچک‌تر از بام در
        // می‌آید و زیر بام می‌رود (توضیح کامل در boxSeated).
        val seatX = surfaceX
        val seatY = surfaceY
        val seatZ = surfaceZ
        cull = true
        try {
            hexaFaces(p, color, side, top, bottom, tiledFaces, topFace, seatBias, seatX, seatY, seatZ,
                topMaxDepth = topMaxDepth, farBox = farBox, farMargin = farMargin,
                allFarBox = allFarBox, allFarMargin = allFarMargin)
        } finally {
            cull = false
        }
    }

    private fun hexaFaces(
        p: FloatArray, color: Int, side: Bitmap?, top: Bitmap?, bottom: Boolean,
        tiledFaces: Boolean = false, topFace: Boolean = true,
        seatBias: Float = 0f, seatX: Float = 0f, seatY: Float = 0f, seatZ: Float = 0f,
        topMaxDepth: Boolean = false, farBox: FloatArray? = null, farMargin: Float = 2f,
        allFarBox: FloatArray? = null, allFarMargin: Float = 4f
    ) {
        fun v(i: Int, k: Int) = p[i * 3 + k]
        // بایاس/مرجع فقط وقتی معنا دارد که حجم روی چیز دیگری نشسته باشد.
        val surfaceBias = seatBias
        val biasRef = if (seatBias > 0f) seatRef else null
        if (seatBias > 0f) { seatRef[0] = seatX; seatRef[1] = seatY; seatRef[2] = seatZ }
        if (bottom) {
            face(v(3, 0), v(3, 1), v(3, 2), v(2, 0), v(2, 1), v(2, 2),
                v(1, 0), v(1, 1), v(1, 2), v(0, 0), v(0, 1), v(0, 2),
                Theme.shade(color, 0.72f), null,
                surfaceBias = surfaceBias, biasRefLocal = biasRef,
                biasCompress = if (seatBias > 0f) SEATED_BIAS_COMPRESS else 1f,
                farBox = allFarBox, farMargin = allFarMargin)
        }
        // وجه بالا: اگر حجمِ رویش دقیقاً همان اندازه را بپوشاند، این وجه
        // دیده نمی‌شود و فقط یک وجه هم‌سطح اضافی برای مرتب‌ساز است.
        if (topFace) {
            val prevTag = scene.debugTag
            val prevGroup = scene.debugGroup
            if (topOnlyTag != 0) { scene.debugTag = topOnlyTag; scene.debugGroup = diagGroup }
            face(v(4, 0), v(4, 1), v(4, 2), v(5, 0), v(5, 1), v(5, 2),
                v(6, 0), v(6, 1), v(6, 2), v(7, 0), v(7, 1), v(7, 2), color, top,
                tiled = tiledFaces, surfaceBias = surfaceBias, biasRefLocal = biasRef,
                biasCompress = if (seatBias > 0f) SEATED_BIAS_COMPRESS else 1f,
                maxDepth = topMaxDepth, farBox = farBox ?: allFarBox,
                farMargin = if (farBox != null) farMargin else allFarMargin)
            scene.debugTag = prevTag
            scene.debugGroup = prevGroup
        }
        for (i in 0..3) {
            val j = (i + 1) % 4
            face(v(i, 0), v(i, 1), v(i, 2), v(j, 0), v(j, 1), v(j, 2),
                v(j + 4, 0), v(j + 4, 1), v(j + 4, 2), v(i + 4, 0), v(i + 4, 1), v(i + 4, 2),
                color, side, tiled = tiledFaces, surfaceBias = surfaceBias, biasRefLocal = biasRef,
                biasCompress = if (seatBias > 0f) SEATED_BIAS_COMPRESS else 1f,
                farBox = allFarBox, farMargin = allFarMargin)
        }
    }

    private fun setCorner(i: Int, x: Float, y: Float, z: Float) {
        corner[i * 3] = x
        corner[i * 3 + 1] = y
        corner[i * 3 + 2] = z
    }

    /** جعبه‌ی محلی؛ cy پایه‌ی جعبه است نه مرکز آن. */
    fun box(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sy2: Float, sz: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side, bottom: Boolean = true,
        tiledFaces: Boolean = false, topFace: Boolean = true, seatBias: Float = 0f,
        surfaceX: Float = 0f, surfaceY: Float = 0f, surfaceZ: Float = 0f,
        topMaxDepth: Boolean = false, farBox: FloatArray? = null, farMargin: Float = 2f,
        /** لنگرِ همهٔ وجه‌ها (نه فقط رویی) — برای حجم‌هایی که کلشان زیر یک سطح‌اند. */
        allFarBox: FloatArray? = null, allFarMargin: Float = 4f
    ) {
        val hx = sx * 0.5f
        val hz = sz * 0.5f
        val ty = baseY + sy2
        ref(cx, baseY + sy2 * 0.5f, cz)
        setCorner(0, cx - hx, baseY, cz - hz)
        setCorner(1, cx + hx, baseY, cz - hz)
        setCorner(2, cx + hx, baseY, cz + hz)
        setCorner(3, cx - hx, baseY, cz + hz)
        setCorner(4, cx - hx, ty, cz - hz)
        setCorner(5, cx + hx, ty, cz - hz)
        setCorner(6, cx + hx, ty, cz + hz)
        setCorner(7, cx - hx, ty, cz + hz)
        // seatBias > 0 یعنی این حجم روی سطحی نشسته: مرجع بایاس = مرکز سطحِ
        // میزبان (بام/عرشه) که از بیرون پاس داده می‌شود.
        hexa(corner, color, side, top, bottom, tiledFaces, topFace, seatBias,
            surfaceX, surfaceY, surfaceZ, topMaxDepth = topMaxDepth, farBox = farBox,
            farMargin = farMargin, allFarBox = allFarBox, allFarMargin = allFarMargin)
    }

    /**
     * حجمی که **روی** حجم دیگر می‌نشیند (پاراپت روی بام، کانتینر روی عرشه).
     *
     * علت باگ: وجه رویی حجم زیرین یک چهارضلعی بزرگ است و عمقش در مرتب‌ساز
     * نقاش **میانگین کل وجه** — یعنی در مرکز بام. پاراپت باریک روی لبه، عمقش
     * نزدیک لبه است. با نزدیک شدن دوربین این دو عدد به هم می‌رسند و با خطای
     * گِرد هر فریم ترتیبشان برعکس می‌شود → «تکهٔ بالای ساختمان ظاهر و غیب
     * می‌شود». همان چیز برای کانتینر روی عرشه.
     *
     * راه‌حل: همان بایاس دقیقی که پنجره‌های روی دیوار را پایدار کرد، ولی
     * مرجع بایاس **مرکز سطحی که حجم رویش نشسته** است (بام/عرشه) — همان
     * نقطه‌ای که عمق میانگین وجه رویی سطح در آن قرار دارد. با این مرجع،
     * کلید مرتب‌سازی هر وجه نشسته برابر می‌شود با
     * `depth(سطح) − حاشیه` و دیگر به موقعیت لبه و جهت دوربین وابسته نیست.
     *
     * دام مهم (یک دور ما را گمراه کرد): اگر مرجع، مرکز **خودِ حجم** باشد،
     * پاراپتِ لبهٔ دور کلید کوچک‌تری از بام می‌گیرد، اول کشیده می‌شود و بام
     * رویش را می‌پوشاند — یعنی همان باگ، فقط با محاسبهٔ «دقیق». پس مرجع
     * درست، سطح است نه حجم.
     */
    fun boxSeated(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sy2: Float, sz: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side,
        tiledFaces: Boolean = false, seatBias: Float = 1.5f,
        /**
         * مرکز سطحی که این حجم رویش نشسته (در دستگاه محلی حجمِ میزبان).
         *
         * چرا **این** نقطه و نه مرکز خودِ حجم: مرتب‌ساز نقاش عمق میانگین
         * وجه را مقایسه می‌کند. وجه رویی بام یک چهارضلعی بزرگ است و عمق
         * میانگینش در **مرکز بام** است؛ پاراپت باریک روی لبه عمقش نزدیک
         * لبه است. اگر بایاس نسبت به مرکز خودِ پاراپت حساب شود، کلیدش
         * depth(پاراپت) − ۱.۵ می‌شود که برای پاراپتِ لبهٔ دور **کوچک‌تر** از
         * کلید بام (مرکز نزدیک‌تر) است → پاراپت اول کشیده می‌شود و بام
         * رویش را می‌پوشاند. دقیقاً همان «تکهٔ بالای ساختمان غیب می‌شود».
         *
         * باید مثل پنجره‌ها باشد: depth(پاراپت) − depth(مرکز بام) + حاشیه.
         */
        surfaceX: Float = 0f, surfaceY: Float = 0f, surfaceZ: Float = 0f
    ) {
        seatedBuilt++
        // برچسب تشخیصی: تست باید بتواند این حجم را روی سطح میزبانش پیدا کند.
        val prevTag = scene.debugTag
        val prevGroup = scene.debugGroup
        scene.debugTag = TAG_SEAT
        scene.debugGroup = diagGroup
        try {
            box(cx, baseY, cz, sx, sy2, sz, color, side, top,
                bottom = false, tiledFaces = tiledFaces, topFace = true,
                seatBias = seatBias, surfaceX = surfaceX, surfaceY = surfaceY, surfaceZ = surfaceZ)
        } finally {
            scene.debugTag = prevTag
            scene.debugGroup = prevGroup
        }
    }

    companion object {
        /** برچسب تشخیصی: حجمی که روی سطحی نشسته (پاراپت/کانتینر). */
        const val TAG_SEAT = 1
        /** برچسب تشخیصی: وجه رویی عرشهٔ کشتی (ناوشکن/باری). */
        const val TAG_SHIP_DECK = 3
        /** برچسب تشخیصی: سازهٔ روی عرشهٔ کشتی (برجک، جزیره، دودکش، آشیانه، ...). */
        const val TAG_SHIP_PART = 4
        /** وجه روییِ صفحهٔ پد (نه بام ساختمان؛ برای سنجش حلقه‌های پد). */
        const val TAG_PAD = 5
        /** برچسب تشخیصی: وجه رویی سطحی که حجم رویش نشسته (بام/عرشه). */
        const val TAG_SURFACE = 2

        // -------------------------------------------------------------
        // شناسهٔ تشخیصیِ حجم/خانواده (Face.debugPart) — فقط برای تست.
        // تست‌های پایداری با این شناسه جفت‌های درون یک سازه را می‌شناسند
        // (طبقه‌های جزیره، پنل چسبیده به دیوار، پایهٔ دکل و دیسکش) و
        // «همیشه باید جلوتر/عقب‌تر باشد» را مستقیم روی کلید مرتب‌سازی
        // می‌سنجند. هیچ اثری در رندر ندارد.
        // -------------------------------------------------------------
        const val PART_HULL = 20
        const val PART_HULL_SEAM_END = 21
        const val PART_DECK_PLATE = 22
        /** طبقه‌های جزیره: PART_ISLAND_TIER + شمارهٔ طبقه. */
        const val PART_ISLAND_TIER = 30
        /** کلاهک/بالاترین بلوک جزیره. */
        const val PART_ISLAND_CAP = 35
        /** پنل/پنجره/آرایه‌ای که روی وجه یک حجم چسبیده است. */
        const val PART_ATTACH = 40
        /** پنلِ نازکِ چسبیده به دیوار (پنجرهٔ پل، آرایهٔ رادار) — هم‌سطح دیوار. */
        const val PART_PANEL = 47
        /** پایه‌های دکل. */
        const val PART_MAST = 41
        /** دیسک/بشقاب رادار. */
        const val PART_DISH = 42
        /** میلهٔ عمودی/بازوی دکل. */
        const val PART_MAST_ARM = 43
        /** هواپیمای پارک‌شده روی عرشه. */
        const val PART_JET = 44
        /** خط/منجنیق/علامت‌گذاری نشسته روی عرشه. */
        const val PART_DECK_MARK = 45
        const val PART_SEATED = 46
        /** حلقه‌های نازک روی صفحهٔ پد (نه ریل‌ها و بقیهٔ نشانه‌های عرشه). */
        const val PART_PAD_RING = 48

        /** فشردگی بایاس حجم‌های نشسته؛ به face() پاس داده می‌شود. */
        const val SEATED_BIAS_COMPRESS = 0.97f
    }

    /** برچسب تشخیصی بعدی را ست می‌کند — فقط برای تست. */
    fun markNext(tag: Int) { scene.debugTag = tag }
    fun clearMark() { scene.debugTag = 0 }

    /** برچسب تشخیصی روی یک **بلوک** از ساخت — مقدار قبلی ذخیره و بازگردانده
     *  می‌شود تا برچسب بیرونی (کشتی) از بین نرود. فقط برای تست. */
    fun markBlock(tag: Int) { blockTagStack.add(scene.debugTag); scene.debugTag = tag }
    fun unmarkBlock() { scene.debugTag = blockTagStack.removeAt(blockTagStack.size - 1) }
    private val blockTagStack = ArrayList<Int>(2)

    /** شناسهٔ حجم تشخیصی؛ مثل markBlock ولی روی [Scene3D.debugPart]. */
    fun beginPart(id: Int) { partStack.add(scene.debugPart); scene.debugPart = id }
    fun endPart() { scene.debugPart = partStack.removeAt(partStack.size - 1) }
    private val partStack = ArrayList<Int>(4)

    /**
     * برچسب فقط روی **وجه رویی** حجم بعدی (نه دیوارهایش).
     *
     * چرا: برای مقایسهٔ پاراپت با بام، باید دقیقاً همان چهارضلعی رویی پیدا
     * شود. اگر کل جعبهٔ لبه برچسب بخورد، دیوارهای ۰٫۵ متری هم کاندید
     * می‌شوند و تست ممکن است دیوار را با بام اشتباه بگیرد.
     */
    private var topOnlyTag = 0
    fun markNextTop(tag: Int) { topOnlyTag = tag }
    fun clearTopMark() { topOnlyTag = 0 }

    /** شناسهٔ گروه تشخیصی — پاراپت و بامِ خودش را به هم وصل می‌کند. */
    private var diagGroup = 0
    fun beginGroup(g: Int) { diagGroup = g }
    fun endGroup() { diagGroup = 0 }

    /** چند حجمِ نشسته در این فریم ساخته شد (تشخیصی). */
    var seatedBuilt = 0
        private set

    fun resetDiagnostics() { seatedBuilt = 0 }

    /** هرم ناقص: نیم‌عرض پایین و بالا جدا؛ برای بدنه‌ی شیب‌دار، برجک و دودکش. */
    fun taper(
        cx: Float, baseY: Float, cz: Float,
        hx0: Float, hz0: Float, hx1: Float, hz1: Float, height: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side,
        shiftX: Float = 0f, shiftZ: Float = 0f, bottom: Boolean = true
    ) {
        val ty = baseY + height
        ref(cx, baseY + height * 0.5f, cz)
        setCorner(0, cx - hx0, baseY, cz - hz0)
        setCorner(1, cx + hx0, baseY, cz - hz0)
        setCorner(2, cx + hx0, baseY, cz + hz0)
        setCorner(3, cx - hx0, baseY, cz + hz0)
        setCorner(4, cx - hx1 + shiftX, ty, cz - hz1 + shiftZ)
        setCorner(5, cx + hx1 + shiftX, ty, cz - hz1 + shiftZ)
        setCorner(6, cx + hx1 + shiftX, ty, cz + hz1 + shiftZ)
        setCorner(7, cx - hx1 + shiftX, ty, cz + hz1 + shiftZ)
        hexa(corner, color, side, top, bottom)
    }

    /**
     * بدنه با مقطع متغیر در طول محور Z: برای دماغه‌ی کشتی و سینه‌ی زرهی تانک.
     * halfW0/halfW1 نیم‌عرض دو سر، deckY ارتفاع عرشه، keelY ارتفاع کف.
     */
    fun hull(
        z0: Float, z1: Float, halfW0: Float, halfW1: Float,
        keelY: Float, deckY: Float, keelHalf0: Float, keelHalf1: Float,
        color: Int, side: Bitmap?, top: Bitmap?,
        /**
         * لنگرِ کلِ بدنه به جعبهٔ کشتی. بدنه **زیر** عرشه است، پس باید یک
         * لایه عقب‌تر از خود عرشه بنشیند (حاشیهٔ ۴ در برابر ۲): همیشه قبل
         * از عرشه رسم می‌شود تا عرشه رویش را بپوشاند، و کلیدش از محدودهٔ
         * کلیدِ خط‌ها/هواپیماهای روی عرشه بیرون می‌رود. بدون این، وجه‌های
         * روییِ بدنه روی عرشه کشیده می‌شدند و با نشانه‌های عرشه جابه‌جا
         * می‌شدند (اندازه‌گیری: تا ۳۰٬۰۰۰ پیکسل مربع هم‌پوشانی وارون).
         */
        farBox: FloatArray? = null,
        farMargin: Float = 4f
    ) {
        ref(0f, (keelY + deckY) * 0.5f, (z0 + z1) * 0.5f)
        setCorner(0, -keelHalf0, keelY, z0)
        setCorner(1, keelHalf0, keelY, z0)
        setCorner(2, keelHalf1, keelY, z1)
        setCorner(3, -keelHalf1, keelY, z1)
        setCorner(4, -halfW0, deckY, z0)
        setCorner(5, halfW0, deckY, z0)
        setCorner(6, halfW1, deckY, z1)
        setCorner(7, -halfW1, deckY, z1)
        hexa(corner, color, side, top, true, farBox = farBox, farMargin = farMargin,
            allFarBox = farBox, allFarMargin = farMargin)
    }

    /** گُوِه: جلوی جسم پایین‌تر از عقب آن است (سینه‌ی تانک، دهانه‌ی سرپناه). */
    fun wedge(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sz: Float, frontY: Float, backY: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side
    ) {
        val hx = sx * 0.5f
        val hz = sz * 0.5f
        ref(cx, baseY + (frontY + backY) * 0.25f, cz)
        setCorner(0, cx - hx, baseY, cz - hz)
        setCorner(1, cx + hx, baseY, cz - hz)
        setCorner(2, cx + hx, baseY, cz + hz)
        setCorner(3, cx - hx, baseY, cz + hz)
        setCorner(4, cx - hx, baseY + frontY, cz - hz)
        setCorner(5, cx + hx, baseY + frontY, cz - hz)
        setCorner(6, cx + hx, baseY + backY, cz + hz)
        setCorner(7, cx - hx, baseY + backY, cz + hz)
        hexa(corner, color, side, top, true)
    }

    // ------------------------------------------------------------- استوانه

    /** استوانه بین دو نقطه‌ی محلی؛ برای لوله‌ی توپ، دکل، دودکش و چرخ. */
    fun tube(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        radius: Float, sides: Int, color: Int, caps: Boolean = true, texture: Bitmap? = null
    ) {
        var ax = x1 - x0
        var ay = y1 - y0
        var az = z1 - z0
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 0.0001f) return
        ax /= len; ay /= len; az /= len
        // دو محور عمود بر محور استوانه
        var ux: Float; var uy: Float; var uz: Float
        if (kotlin.math.abs(ay) < 0.9f) { ux = -az; uy = 0f; uz = ax } else { ux = 0f; uy = -az; uz = ay }
        val ui = 1f / sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0001f)
        ux *= ui; uy *= ui; uz *= ui
        val vx = ay * uz - az * uy
        val vy = az * ux - ax * uz
        val vz = ax * uy - ay * ux
        ref((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f)
        val n = sides.coerceIn(3, 16)
        var pax = 0f; var pay = 0f; var paz = 0f
        var pbx = 0f; var pby = 0f; var pbz = 0f
        for (i in 0..n) {
            val ang = i * 2f * Math.PI.toFloat() / n
            val cc = cos(ang) * radius
            val ss = sin(ang) * radius
            val ex = ux * cc + vx * ss
            val ey = uy * cc + vy * ss
            val ez = uz * cc + vz * ss
            val nax = x0 + ex; val nay = y0 + ey; val naz = z0 + ez
            val nbx = x1 + ex; val nby = y1 + ey; val nbz = z1 + ez
            if (i > 0) {
                cull = caps
                face(pax, pay, paz, nax, nay, naz, nbx, nby, nbz, pbx, pby, pbz, color, texture)
                cull = false
                if (caps) {
                    tri(x0, y0, z0, pax, pay, paz, nax, nay, naz, Theme.shade(color, 0.88f))
                    tri(x1, y1, z1, nbx, nby, nbz, pbx, pby, pbz, Theme.shade(color, 1.06f))
                }
            }
            pax = nax; pay = nay; paz = naz
            pbx = nbx; pby = nby; pbz = nbz
        }
    }

    /** صفحه‌ی دیسکی (آنتن سهموی، صفحه‌ی رادار، چرخ). */
    fun disc(
        cx: Float, cy2: Float, cz: Float,
        nx: Float, ny: Float, nz: Float,
        radius: Float, sides: Int, color: Int
    ) {
        val nInv = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        val nnx = nx * nInv; val nny = ny * nInv; val nnz = nz * nInv
        var ux: Float; var uy: Float; var uz: Float
        if (kotlin.math.abs(nny) < 0.9f) { ux = -nnz; uy = 0f; uz = nnx } else { ux = 0f; uy = -nnz; uz = nny }
        val ui = 1f / sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0001f)
        ux *= ui; uy *= ui; uz *= ui
        val vx = nny * uz - nnz * uy
        val vy = nnz * ux - nnx * uz
        val vz = nnx * uy - nny * ux
        ref(cx - nnx, cy2 - nny, cz - nnz)
        val n = sides.coerceIn(3, 16)
        var px = 0f; var py = 0f; var pz = 0f
        for (i in 0..n) {
            val ang = i * 2f * Math.PI.toFloat() / n
            val cc = cos(ang) * radius
            val ss = sin(ang) * radius
            val ex = cx + ux * cc + vx * ss
            val ey = cy2 + uy * cc + vy * ss
            val ez = cz + uz * cc + vz * ss
            if (i > 0) tri(cx, cy2, cz, px, py, pz, ex, ey, ez, color)
            px = ex; py = ey; pz = ez
        }
    }
}
