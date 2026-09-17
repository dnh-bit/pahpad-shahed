# Canvas render regressions

Run from repository root:

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -Xmx512m' python3 checks/render/run.py
```

This compiles the actual `Scene3D.kt`, `Fx.kt`, and `Theme.kt` with Kotlin
1.9.22 and runs them on JDK 17. `GraphicsDoubles.kt` records draw calls and
shader matrices; it does **not** rasterize pixels or emulate Android/Skia.
Compiler jars live outside the repository in `/tmp/pahpad-render-kotlin`.
See `run.py` for environment overrides and required jar names.

Observed test-first failures before their respective fixes:

- `FAIL near-clipped opaque quad keeps texture and perspective UV: clipped quad lost its bitmap`
- `FAIL particles queue between far and near solids instead of overlaying: Fx draws immediately outside shared queue`

Final standalone run: exit 0, all six tests PASS:

- near-clipped opaque quad keeps texture and perspective UV
- transparent quad survives near-plane sweep without opaque fallback
- one-corner clipping keeps a five-vertex textured polygon
- fully hidden and degenerate transparent quads are culled
- unclipped trapezoid matches four-corner homography at interior UVs
- particles queue between far and near solids instead of overlaying

The latter four cases extend coverage of the initial red/green fixes; they were
not independently observed red before implementation. Several foreground JVM
runs timed out on the shared host; the final tracked run completed successfully.

Additional Android/Robolectric tests are in
`app/src/test/java/ir/shahed/pahpad/game/Scene3DRegressionTest.kt`.
They exercise Android Matrix mapping and recorded Canvas dispatch. The parent
agent owns the Gradle run; the standalone result is not a claim that those tests
or Android on-device rendering have passed.

## Scope and limitations

Texture homography is composed from a planar convex quad in camera space and
camera projection, before clipping. Original corners can have z=0 or negative z.
The clipped polygon limits coverage; it does not stretch the full bitmap across
the clipped remainder. Degenerate transparent quads never fall back to an opaque
rectangle. Planar convex input remains the existing quad API assumption.

Particles and blast flashes now join the same stable far-to-near painter queue
as solids and image planes. `Fx.draw` keeps its public signature but enqueues;
call it **before** `scene.flush`. Ground shockwave lines retain their existing
under-solids pass. A single mean face depth/particle center depth handles fully
separated occluders, not intersecting geometry, long slanted faces, cyclic
occlusion or per-pixel transparency. This is not a z-buffer replacement.
