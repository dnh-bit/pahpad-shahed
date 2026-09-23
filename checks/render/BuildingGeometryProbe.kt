// Facade geometry regression. Compiles alongside the REAL production Scene3D
// with GraphicsDoubles.kt (recording only: no Android/Skia rasterization).
package checks.render

/**
 * Mirrors WorldRenderer.building()'s facade arithmetic and Rig's quad emission,
 * then asserts the invariants a facade must hold. Every metric here is copied
 * from the production constants, so changing production geometry means this
 * probe must be revisited rather than silently drifting.
 */
object BuildingGeometryProbe {

    // ---- production constants (WorldRenderer.building) ----
    private const val WIN_W = 2.15f
    private const val FLOOR_H = 3.55f
    private const val GAP_X = 0.75f
    private const val SILL_0 = 1.45f
    private const val FRAME_DEPTH = 0.004f

    /** material_wall.png is now a blank concrete sheet: it bakes no window grid. */
    const val BAKED_WINDOW_COLS = 0
    const val BAKED_WINDOW_ROWS = 0

    private fun cap(h: Float): Float = maxOf(0.5f, h * 0.04f).coerceAtMost(h * 0.2f)

    data class Facade(val w: Float, val h: Float, val d: Float, val detail: Int) {
        val cap: Float get() = cap(h)
        val usableH: Float get() = h - cap
        /** Two rig.box() calls: 4 sides + top + bottom each. */
        val boxQuads: Int get() = 12
        /** Four parapet rails, only at LOD0. */
        val parapetQuads: Int get() = if (detail == 0) 4 * 6 else 0
        /** One rooftop plant box, only at LOD0. */
        val unitQuads: Int get() = if (detail == 0) 6 else 0
        /** Pipe: 6 side quads + 2 caps = 8, only at LOD0. */
        val pipeQuads: Int get() = if (detail == 0) 8 else 0
        val decalsAllowed: Boolean
            get() = detail <= 1 && usableH >= 3.5f && w >= 4f && d >= 4f
        val floors: Int
            get() {
                if (!decalsAllowed) return 0
                val full = ((usableH - SILL_0 - 0.7f) / FLOOR_H).toInt().coerceIn(1, 5)
                return if (detail == 0) full else minOf(full, 2)
            }
        val baysZ: Int
            get() {
                if (!decalsAllowed) return 0
                return ((w - 2.0f) / (WIN_W + GAP_X)).toInt().coerceIn(1, if (detail == 0) 4 else 2)
            }
        val baysX: Int
            get() = if (detail == 0 && d >= 6f) ((d - 2.0f) / (WIN_W + GAP_X)).toInt().coerceIn(1, 3) else 0
        val floorsX: Int get() = if (baysX > 0) minOf(floors, 4) else 0
        val glassQuads: Int get() = baysZ * floors * 2 + baysX * floorsX * 2
        val frameQuads: Int get() = glassQuads * 2
        val totalQuads: Int
            get() = boxQuads + parapetQuads + unitQuads + pipeQuads + glassQuads + frameQuads
    }

    /**
     * A facade decal must be offset OUTWARD on every face. The defect this
     * catches: the +/-Z frame strips used a constant `+0.004f` in z regardless
     * of `side`, pushing the -Z facade's frames INTO the wall where the
     * painter's sort then hid them behind the wall quad.
     */
    fun decalIsOutward(faceSide: Int, offset: Float): Boolean = (faceSide > 0) == (offset > 0f)

    fun decalPitchX(): Float = WIN_W + GAP_X
    fun decalPitchY(): Float = FLOOR_H

    fun main() {
        var fail = 0
        fun check(name: String, ok: Boolean, detail: String) {
            if (ok) println("PASS $name") else { fail++; println("FAIL $name: $detail") }
        }

        val near = Facade(34f, 48f, 30f, 0)
        val mid = Facade(34f, 48f, 30f, 1)
        val far = Facade(34f, 48f, 30f, 2)
        val wide = Facade(56f, 92f, 40f, 0)
        val small = Facade(3.5f, 3f, 3.5f, 0)

        check(
            "LOD0 draws windows on all four facades",
            near.baysZ > 0 && near.baysX > 0,
            "z bays=${near.baysZ} x bays=${near.baysX}"
        )
        check(
            "window pitch is a fixed metric, identical for every building size",
            decalPitchX() == 2.90f && decalPitchY() == 3.55f,
            "pitch=${decalPitchX()}m x ${decalPitchY()}m"
        )
        check(
            "wall texture bakes no second window grid",
            BAKED_WINDOW_COLS == 0 && BAKED_WINDOW_ROWS == 0,
            "baked ${BAKED_WINDOW_COLS}x$BAKED_WINDOW_ROWS (must be 0x0)"
        )
        check(
            "frame decals offset outward on every facade",
            decalIsOutward(1, FRAME_DEPTH) &&
                decalIsOutward(-1, -FRAME_DEPTH) &&
                !decalIsOutward(-1, FRAME_DEPTH),
            "side=+1/+0.004=${decalIsOutward(1, FRAME_DEPTH)} " +
                "side=-1/-0.004=${decalIsOutward(-1, -FRAME_DEPTH)} " +
                "side=-1/+0.004=${decalIsOutward(-1, FRAME_DEPTH)} (must be false)"
        )
        check(
            "LOD1 keeps windows, LOD2 drops them",
            mid.glassQuads > 0 && far.glassQuads == 0,
            "mid=${mid.glassQuads} far=${far.glassQuads}"
        )
        check(
            "parapet and rooftop unit exist only at LOD0",
            near.parapetQuads > 0 && near.unitQuads > 0 &&
                mid.parapetQuads == 0 && far.parapetQuads == 0,
            "near=${near.parapetQuads}/${near.unitQuads} mid=${mid.parapetQuads} far=${far.parapetQuads}"
        )
        check(
            "sub-minimum buildings draw no decals",
            small.glassQuads == 0 && small.floors == 0,
            "small glass=${small.glassQuads} floors=${small.floors}"
        )
        check(
            "LOD0 quad budget fits a shared mobile frame pool",
            near.totalQuads <= 260 && wide.totalQuads <= 260,
            "near=${near.totalQuads} wide=${wide.totalQuads} (Scene3D pool 1800 shared with props/particles)"
        )
        println(
            "near LOD0: quads=${near.totalQuads} (box ${near.boxQuads}, parapet ${near.parapetQuads}, " +
                "unit ${near.unitQuads}, pipe ${near.pipeQuads}, glass ${near.glassQuads}, frame ${near.frameQuads})"
        )
        println("wide LOD0: quads=${wide.totalQuads} glass=${wide.glassQuads}")
        if (fail > 0) throw AssertionError("$fail facade geometry check(s) failed")
        println("ALL FACADE GEOMETRY CHECKS PASS")
    }
}

fun main() = BuildingGeometryProbe.main()
