package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.*
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.Env
import kotlin.math.*

/**
 * Sky dome, sun, clouds, horizon relief, world-locked ground and cast shadows.
 *
 * 0.0.9 rewrite. Everything that sits "at infinity" (sky, sun, stars, clouds,
 * mountains) is placed by elevation angle and scrolled by `yaw * focal`, so it
 * turns exactly with the 3D world instead of sliding against it (the old strip
 * used a fixed 4 tiles per turn, independent of the field of view). The ground
 * haze is derived from the same fog curve as the geometry, so distant solids,
 * ground and horizon dissolve into one colour. No per-frame allocations.
 */
class SceneryArt(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blend = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val local = Matrix()
    private val src = FloatArray(8); private val dst = FloatArray(8); private val ground = FloatArray(3)
    private val groundCorners = Array(4) { FloatArray(3) }
    private val rect = RectF()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

    private val terrainShaders = HashMap<Env, BitmapShader>()
    private var macroShader: BitmapShader? = null
    private var macroFineShader: BitmapShader? = null
    private var cloudFilter: ColorFilter? = null

    private var lastLook: EnvLook? = null
    private var sky: LinearGradient? = null
    private var haze: LinearGradient? = null
    private var halo: RadialGradient? = null
    private var bloom: RadialGradient? = null
    private var glint: RadialGradient? = null

    /** World-space animation phase (seconds): sea drift, cloud drift, twinkle. */
    private var phase = 0f
    fun advance(dt: Float) { phase += dt }

    // Fixed star field (elevation-biased), only used by looks with stars.
    private val starCount = 110
    private val starDir = FloatArray(starCount * 3)
    private val starMag = FloatArray(starCount)
    init {
        val r = java.util.Random(0x51A7L)
        for (i in 0 until starCount) {
            val az = r.nextFloat() * 2f * PI.toFloat()
            val el = (0.14f + 1.3f * r.nextFloat().pow(0.7f)).coerceAtMost(1.5f)
            starDir[i * 3] = cos(el) * sin(az)
            starDir[i * 3 + 1] = sin(el)
            starDir[i * 3 + 2] = cos(el) * cos(az)
            starMag[i] = 0.35f + r.nextFloat() * 0.65f
        }
    }

    fun clear() {
        terrainShaders.clear(); macroShader = null; macroFineShader = null
        sky = null; haze = null; halo = null; bloom = null; glint = null; cloudFilter = null; lastLook = null
    }

    fun name(env: Env) = env.name.lowercase(java.util.Locale.ROOT)

    private fun rebuild(look: EnvLook, scene: Scene3D) {
        // Sky in unit space: y = -tan(elevation). Scaled by focal each frame.
        val t70 = tan(Math.toRadians(70.0)).toFloat()
        fun pos(deg: Double) = 1f - tan(Math.toRadians(deg)).toFloat() / t70
        val mid = Theme.mix(look.glow, look.upper, .55f)
        sky = LinearGradient(0f, -t70, 0f, 0f,
            intArrayOf(look.zenith, look.upper, mid, look.glow, look.fog),
            floatArrayOf(0f, pos(26.0), pos(10.0), pos(2.4), 1f), Shader.TileMode.CLAMP)
        // Ground haze in unit space t = fogStart / distance (t=0 at the horizon).
        val ds = floatArrayOf(1e7f, 1f, .86f, .72f, .58f, .44f, .30f, .16f, 0f)
        val colors = IntArray(ds.size); val stops = FloatArray(ds.size)
        for (i in ds.indices) {
            val d = if (i == 0) 1e7f else look.fogStart + (look.fogEnd - look.fogStart) * ds[i]
            stops[i] = (look.fogStart / d).coerceIn(0f, 1f)
            colors[i] = Theme.withAlpha(look.fog, scene.fogFactor(d))
        }
        stops[0] = 0f
        haze = LinearGradient(0f, 0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
        halo = RadialGradient(0f, 0f, 1f,
            intArrayOf(Theme.withAlpha(look.sunHalo, .55f), Theme.withAlpha(look.sunHalo, .16f), Theme.withAlpha(look.sunHalo, 0f)),
            floatArrayOf(0f, .22f, 1f), Shader.TileMode.CLAMP)
        bloom = RadialGradient(0f, 0f, 1f,
            intArrayOf(Theme.withAlpha(look.sunHalo, .30f), Theme.withAlpha(look.glow, .10f), Theme.withAlpha(look.glow, 0f)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        glint = RadialGradient(0f, 0f, 1f,
            intArrayOf(Theme.withAlpha(look.sunDisc, .55f), Theme.withAlpha(look.sunHalo, .16f), Theme.withAlpha(look.sunHalo, 0f)),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        cloudFilter = LightingColorFilter(look.cloudMul and 0xFFFFFF, look.cloudAdd and 0xFFFFFF)
        lastLook = look
    }

    fun draw(c: Canvas, scene: Scene3D, world: World, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val env = world.level.env
        val look = scene.look
        if (lastLook !== look || sky == null) rebuild(look, scene)
        val cam = scene.cam
        val f = cam.focal
        val horizon = cam.horizonY()

        // ---------------------------------------------------------------- sky
        local.setScale(1f, f); local.postTranslate(0f, horizon)
        sky?.setLocalMatrix(local)
        paint.shader = sky; paint.alpha = 255
        c.drawRect(-w, -h, w * 2f, h * 2f, paint); paint.shader = null

        if (look.stars) drawStars(c, scene, h)

        // --------------------------------------------------------------- sun
        val sunVisible = cam.project(cam.x + scene.sunX * 1000f, cam.y + scene.sunY * 1000f, cam.z + scene.sunZ * 1000f, ground)
        val sunSx = ground[0]; val sunSy = ground[1]
        if (sunVisible) {
            blend.xfermode = screen
            val big = h * 1.25f
            local.setScale(big, big); local.postTranslate(sunSx, sunSy); bloom?.setLocalMatrix(local)
            blend.shader = bloom; c.drawCircle(sunSx, sunSy, big, blend)
            val r = h * .2f
            local.setScale(r, r); local.postTranslate(sunSx, sunSy); halo?.setLocalMatrix(local)
            blend.shader = halo; c.drawCircle(sunSx, sunSy, r, blend)
            blend.shader = null; blend.xfermode = null
            paint.color = look.sunDisc; c.drawCircle(sunSx, sunSy, h * .018f, paint)
        }

        // ------------------------------------------------------------ clouds
        Gfx.get(context, "sky_clouds")?.let { clouds ->
            val yTop = horizon - f * tan(Math.toRadians(21.0)).toFloat()
            val yBot = horizon - f * tan(Math.toRadians(1.1)).toFloat()
            paint.colorFilter = cloudFilter; paint.alpha = look.cloudAlpha
            strip(c, clouds, yTop, yBot, cam.yaw + phase * .0035f, f, w, 1.7f)
            paint.colorFilter = null; paint.alpha = 255
        }

        // ------------------------------------------------ distant relief strip
        Gfx.get(context, "horizon_${name(env)}")?.let { relief ->
            val s = f * tan(Math.toRadians(4.8)).toFloat()
            val full = s / .86f
            strip(c, relief, horizon - s, horizon - s + full, cam.yaw, f, w, 1f)
        }

        // ------------------------------------------------------------ ground
        val top = max(-h, horizon + 2f); val bottom = h * 2f
        if (top >= bottom) return
        paint.color = look.groundFallback
        c.drawRect(-w, top, w * 2f, bottom, paint)
        dst[0] = -w; dst[1] = top; dst[2] = w * 2; dst[3] = top; dst[4] = w * 2; dst[5] = bottom; dst[6] = -w; dst[7] = bottom
        var valid = true
        for (i in 0..3) if (!cam.groundAt(dst[i * 2], dst[i * 2 + 1], groundCorners[i])) { valid = false; break }
        val bitmap = Gfx.get(context, "terrain_${name(env)}")
        val drift = if (env == Env.NAVAL) phase * 3.4f else 0f
        if (valid && bitmap != null) {
            val shader = terrainShaders.getOrPut(env) { BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
            val tile = if (env == Env.NAVAL) 110f else 180f
            if (groundLayer(c, shader, bitmap.width.toFloat(), bitmap.height.toFloat(), tile, drift, 255, null, w, top, bottom)) {
                Gfx.get(context, "terrain_macro")?.let { macro ->
                    val m1 = macroShader ?: BitmapShader(macro, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { macroShader = it }
                    val m2 = macroFineShader ?: BitmapShader(macro, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { macroFineShader = it }
                    // Two incommensurate multiply layers break up the 180 m tile grid.
                    val strength = if (env == Env.NAVAL) 150 else 255
                    groundLayer(c, m1, macro.width.toFloat(), macro.height.toFloat(), 1613f, drift, strength, multiply, w, top, bottom)
                    groundLayer(c, m2, macro.width.toFloat(), macro.height.toFloat(), 389f, drift, strength / 2, multiply, w, top, bottom)
                }
            }
        }
        // Sun glitter on water, at the sun's mirror depression angle.
        if (env == Env.NAVAL && sunVisible) {
            val el = asin(scene.sunY.coerceIn(-1f, 1f))
            val gy = horizon + f * tan(el) * .55f
            local.setScale(f * .34f, max(f * tan(el) * .75f, 1f)); local.postTranslate(sunSx, gy); glint?.setLocalMatrix(local)
            blend.xfermode = screen; blend.shader = glint
            c.drawRect(sunSx - f * .34f, top, sunSx + f * .34f, gy + f * tan(el) * .75f, blend)
            blend.shader = null; blend.xfermode = null
        }
        // Aerial perspective over the ground, same curve as the geometry fog.
        val height = max(cam.y, .5f)
        val sMax = height * f / scene.fogStart
        local.setScale(1f, max(sMax, .001f)); local.postTranslate(0f, horizon); haze?.setLocalMatrix(local)
        paint.shader = haze; paint.alpha = 255
        c.drawRect(-w, top, w * 2f, bottom, paint); paint.shader = null
        // Soft bright seam where relief meets ground; hides the texture's far aliasing.
        rect.set(-w, horizon - h * .01f, w * 2f, horizon + h * .02f)
        paint.color = Theme.withAlpha(look.fog, .55f)
        c.drawRect(rect, paint)
    }

    /** Sprite strip at infinity: whole number of mirrored tiles per turn. */
    private fun strip(c: Canvas, bmp: Bitmap, yTop: Float, yBot: Float, yaw: Float, f: Float, w: Float, stretch: Float) {
        val hPx = yBot - yTop
        if (hPx <= 1f || !hPx.isFinite()) return
        val circumference = 2f * PI.toFloat() * f
        val want = hPx * bmp.width / bmp.height * stretch
        var n = (circumference / want).roundToInt().coerceAtLeast(2)
        if (n % 2 != 0) n++
        val tileW = circumference / n
        var offset = (yaw * f) % circumference
        if (offset < 0f) offset += circumference
        var i = floor((-w + offset) / tileW).toInt()
        var x = i * tileW - offset
        var guard = 0
        while (x < w * 2f && guard++ < 64) {
            rect.set(x, yTop, x + tileW + 1f, yBot)
            if (((i % n) + n) % 2 != 0) {
                c.save(); c.scale(-1f, 1f, rect.centerX(), 0f)
                c.drawBitmap(bmp, null, rect, paint)
                c.restore()
            } else c.drawBitmap(bmp, null, rect, paint)
            x += tileW; i++
        }
    }

    private fun groundLayer(c: Canvas, shader: BitmapShader, bw: Float, bh: Float, period: Float, drift: Float,
                            alpha: Int, mode: Xfermode?, w: Float, top: Float, bottom: Float): Boolean {
        // Anchor on the near (bottom) corner: best float precision where detail shows.
        val ox = floor((groundCorners[3][0] + drift) / period) * period
        val oz = floor(groundCorners[3][2] / period) * period
        for (i in 0..3) {
            src[i * 2] = (groundCorners[i][0] + drift - ox) * bw / period
            src[i * 2 + 1] = (groundCorners[i][2] - oz) * bh / period
        }
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return false
        shader.setLocalMatrix(matrix)
        paint.shader = shader; paint.alpha = alpha; paint.xfermode = mode
        c.drawRect(-w, top, w * 2, bottom, paint)
        paint.shader = null; paint.alpha = 255; paint.xfermode = null
        return true
    }

    private fun drawStars(c: Canvas, scene: Scene3D, h: Float) {
        val cam = scene.cam
        val base = max(1f, h / 520f)
        for (i in 0 until starCount) {
            if (!cam.project(cam.x + starDir[i * 3] * 1000f, cam.y + starDir[i * 3 + 1] * 1000f, cam.z + starDir[i * 3 + 2] * 1000f, ground)) continue
            val el = starDir[i * 3 + 1]
            val twinkle = .75f + .25f * sin(phase * (1.7f + i % 5) + i)
            val a = (starMag[i] * twinkle * ((el - .12f) / .35f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
            if (a < .05f) continue
            paint.color = Theme.withAlpha(0xFFF2F4FF.toInt(), a)
            c.drawCircle(ground[0], ground[1], base * (.5f + starMag[i]), paint)
        }
    }

    /** Screen-space lens ghosts; drawn after geometry, before the HUD. */
    fun lensFlare(c: Canvas, scene: Scene3D, w: Float, h: Float) {
        val cam = scene.cam
        if (!cam.project(cam.x + scene.sunX * 1000f, cam.y + scene.sunY * 1000f, cam.z + scene.sunZ * 1000f, ground)) return
        val sx = ground[0]; val sy = ground[1]
        if (sx < -w * .1f || sx > w * 1.1f || sy < -h * .1f || sy > h * 1.1f) return
        val edge = 1f - (max(abs(sx - w / 2f) / (w * .6f), abs(sy - h / 2f) / (h * .6f))).coerceIn(0f, 1f)
        if (edge <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val look = scene.look
        blend.shader = null; blend.xfermode = screen
        val ghosts = floatArrayOf(.35f, .62f, 1.18f, 1.55f)
        val sizes = floatArrayOf(.035f, .018f, .06f, .026f)
        for (k in ghosts.indices) {
            val gx = sx + (cx - sx) * ghosts[k] * 2f
            val gy = sy + (cy - sy) * ghosts[k] * 2f
            blend.color = Theme.withAlpha(if (k % 2 == 0) look.sunHalo else 0xFF9FD3FF.toInt(), .10f * edge)
            c.drawCircle(gx, gy, h * sizes[k], blend)
        }
        blend.xfermode = null
    }

    fun roads(scene: Scene3D, world: World) {
        if (world.level.env != Env.URBAN && world.level.env != Env.SPECIAL) return
        val cam = scene.cam
        val asphalt = 0xFF4B5052.toInt()
        val curb = 0xFF8E8C84.toInt()
        val paintLine = 0xFFD9D4B8.toInt()
        val row0 = floor((cam.z - 120f - CityLayout.START_Z) / CityLayout.ROW_SPACING).toInt() - 1
        val row1 = row0 + 19
        for (row in row0..row1) {
            val cross = CityLayout.crossZ(row)
            val hr = CityLayout.HALF_ROAD
            scene.quad(-400f, .04f, cross - hr, 400f, .04f, cross - hr, 400f, .04f, cross + hr, -400f, .04f, cross + hr, asphalt)
            // kerbs
            scene.quad(-400f, .06f, cross - hr - .9f, 400f, .06f, cross - hr - .9f, 400f, .06f, cross - hr, -400f, .06f, cross - hr, curb)
            scene.quad(-400f, .06f, cross + hr, 400f, .06f, cross + hr, 400f, .06f, cross + hr + .9f, -400f, .06f, cross + hr + .9f, curb)
            for (col in -4..4) {
                val x = col * CityLayout.COLUMN_SPACING + 39f
                scene.quad(x - hr, .05f, cross, x + hr, .05f, cross, x + hr, .05f, cross + CityLayout.ROW_SPACING, x - hr, .05f, cross + CityLayout.ROW_SPACING, asphalt)
                for (i in 0..2) {
                    val zz = cross + 14f + i * 26f
                    scene.quad(x - .3f, .08f, zz, x + .3f, .08f, zz, x + .3f, .08f, zz + 10f, x - .3f, .08f, zz + 10f, paintLine)
                }
            }
        }
    }

    /** Round soft blob (drone, AA sites). */
    fun shadow(c: Canvas, scene: Scene3D, x: Float, z: Float, w: Float, d: Float, alpha: Float = .22f) {
        val bmp = Gfx.get(context, "shadow_soft") ?: return
        for (i in 0..3) {
            val px = x + if (i == 0 || i == 3) -w else w
            val pz = z + if (i < 2) -d else d
            if (!scene.cam.project(px, .12f, pz, ground)) return
            dst[i * 2] = ground[0]; dst[i * 2 + 1] = ground[1]
        }
        mapBitmap(c, bmp, alpha)
    }

    /**
     * Directional shadow of a (rotated) box of height `h`: a soft parallelogram
     * from the footprint towards the anti-sun, instead of an axis-aligned blob.
     */
    fun castShadow(c: Canvas, scene: Scene3D, x: Float, z: Float, w: Float, d: Float, h: Float, rot: Float, alpha: Float) {
        val bmp = Gfx.get(context, "shadow_box") ?: return
        val dx = -scene.shadowX * h; val dz = -scene.shadowZ * h
        val len = sqrt(dx * dx + dz * dz)
        val ux: Float; val uz: Float
        if (len > .01f) { ux = dx / len; uz = dz / len } else { ux = 0f; uz = 1f }
        val px = -uz; val pz = ux
        val cr = cos(rot); val sr = sin(rot)
        val a1x = cr * w * .5f; val a1z = sr * w * .5f
        val a2x = -sr * d * .5f; val a2z = cr * d * .5f
        // shadow_box has soft margins (~17%), so widen the footprint to compensate
        val hp = (abs(a1x * px + a1z * pz) + abs(a2x * px + a2z * pz)) * 1.28f
        val hu = (abs(a1x * ux + a1z * uz) + abs(a2x * ux + a2z * uz)) * 1.28f
        val bx = x - ux * hu; val bz = z - uz * hu
        val fx = x + dx + ux * hu * .8f; val fz = z + dz + uz * hu * .8f
        val cam = scene.cam
        if (!cam.project(bx + px * hp, .1f, bz + pz * hp, ground)) return
        dst[0] = ground[0]; dst[1] = ground[1]
        if (!cam.project(bx - px * hp, .1f, bz - pz * hp, ground)) return
        dst[2] = ground[0]; dst[3] = ground[1]
        if (!cam.project(fx - px * hp, .1f, fz - pz * hp, ground)) return
        dst[4] = ground[0]; dst[5] = ground[1]
        if (!cam.project(fx + px * hp, .1f, fz + pz * hp, ground)) return
        dst[6] = ground[0]; dst[7] = ground[1]
        mapBitmap(c, bmp, alpha)
    }

    private fun mapBitmap(c: Canvas, bmp: Bitmap, alpha: Float) {
        src[0] = 0f; src[1] = 0f; src[2] = bmp.width.toFloat(); src[3] = 0f
        src[4] = src[2]; src[5] = bmp.height.toFloat(); src[6] = 0f; src[7] = src[5]
        if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            paint.shader = null; paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            c.drawBitmap(bmp, matrix, paint); paint.alpha = 255
        }
    }
}
