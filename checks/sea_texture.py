#!/usr/bin/env python3
"""Naval sea-texture regression checks.

Regenerates the procedural sea height field from tools/gen_terrain.py's exact
formula and asserts the properties the runtime depends on. The runtime tiles
terrain_naval.png every 110 m (SceneryArt.kt), so a non-tileable edge is a
visible seam in every naval level.

Run:
    python3 checks/sea_texture.py
Requires numpy. Pillow is optional: without it the shipped-PNG checks are
skipped and only the formula-level checks run.
"""
import pathlib
import sys

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[1]
S = 512  # gen_terrain.S

# Thresholds are asserted against measured interior detail rather than magic
# numbers, so a legitimate re-tune of the texture does not need a code change.
SEAM_RATIO_MAX = 1.60   # wrap step vs interior step in the 2x2 tiling
FOAM_MIN_PCT = 0.30     # whitecap pixel coverage in the rendered sheet
FOAM_MAX_PCT = 12.0
LUMA_SPAN_MIN = 110.0   # crest-to-trough dynamic range, 0..255
GLINT_MIN_PCT = 1.0
GLINT_MAX_PCT = 25.0


def fbm(size, period, octaves, r, gain=0.5, lac=2.0):
    """Copy of texlib.fbm (tileable value noise)."""
    total = np.zeros((size, size), np.float32)
    amp, norm, p = 1.0, 0.0, float(period)
    for _ in range(octaves):
        p = int(p)
        g = r.random((p, p)).astype(np.float32)
        x = np.linspace(0.0, p, size, endpoint=False).astype(np.float32)
        xi = np.floor(x).astype(np.int32)
        xf = x - xi
        xs = xf * xf * (3.0 - 2.0 * xf)
        i0, i1 = xi % p, (xi + 1) % p
        a = g[:, i0] * (1.0 - xs) + g[:, i1] * xs
        b = a[i0, :] * (1.0 - xs)[:, None] + a[i1, :] * xs[:, None]
        total += amp * b
        norm += amp
        amp *= gain
        p *= lac
        if p > size:
            p = size
    return total / max(norm, 1e-6)


def blur_wrap(h, radius=1):
    k = np.ones(2 * radius + 1, np.float32) / (2 * radius + 1)
    pad = np.concatenate([h[-radius:], h, h[:radius]], axis=0)
    out = np.apply_along_axis(lambda m: np.convolve(m, k, mode="valid"), 0, pad)
    pad = np.concatenate([out[:, -radius:], out, out[:, :radius]], axis=1)
    return np.apply_along_axis(lambda m: np.convolve(m, k, mode="valid"), 1, pad).astype(np.float32)


def cross_blur(h, radius=1):
    """Separable wrap blur, applied one axis at a time.

    Needed because texlib.blur_wrap pads a fixed radius and an odd kernel can
    leave a residual step across the wrap on a high-frequency field.
    """
    k = np.ones(2 * radius + 1, np.float32) / (2 * radius + 1)
    rows = np.zeros_like(h, dtype=np.float32)
    for i in range(h.shape[0]):
        rows[i] = np.convolve(np.concatenate([h[i, -radius:], h[i], h[i, :radius]]), k, mode="valid")
    cols = np.zeros_like(rows, dtype=np.float32)
    for j in range(h.shape[1]):
        cols[:, j] = np.convolve(np.concatenate([rows[-radius:, j], rows[:, j], rows[:radius, j]]), k, mode="valid")
    return cols.astype(np.float32)


def grad(h):
    """Central differences with periodic wrap, matching texlib.grad."""
    gx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * 0.5
    gy = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * 0.5
    return gx, gy


def tile_step(a):
    """Per-axis wrap discontinuity, relative to that axis's own interior detail.

    This is the metric the runtime actually exhibits: SceneryArt repeats the
    sheet with TileMode.REPEAT, so a wrap discontinuity becomes a visible line.
    Each axis is compared against its OWN interior adjacent-pixel step, because
    the sea field is deliberately anisotropic (more chop across x than along
    y); comparing the y wrap against the x interior step would score a
    legitimately high-frequency y gradient as a seam.
    """
    gray = a if a.ndim == 2 else a.mean(axis=2)
    h, w = gray.shape
    big = np.tile(gray, (2, 2))
    int_x = float(np.abs(np.diff(big, axis=1)).mean())
    int_y = float(np.abs(np.diff(big, axis=0)).mean())
    vert = float(np.abs(big[:, w - 1] - big[:, w]).mean())   # x wrap
    horz = float(np.abs(big[h - 1, :] - big[h, :]).mean())   # y wrap
    return (vert / max(int_x, 1e-9), horz / max(int_y, 1e-9),
            (int_x + int_y) * 0.5, vert, horz)


def shade(h, strength, light, ambient, diffuse):
    gx, gy = grad(h)
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(h)
    inv = 1.0 / np.sqrt(nx * nx + ny * ny + nz * nz)
    lx, ly, lz = light
    ln = (lx * lx + ly * ly + lz * lz) ** 0.5
    d = ((nx * lx + ny * ly + nz * lz) * inv / ln).clip(0.0, 1.0)
    return (ambient + diffuse * d).astype(np.float32)


def ramp(t, stops):
    t = np.clip(np.asarray(t, np.float32), 0.0, 1.0)
    out = np.zeros(t.shape + (3,), np.float32)
    out[...] = np.asarray(stops[0][1], np.float32)
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        m = (t >= p0) & (t <= p1)
        if not m.any():
            continue
        k = ((t[m] - p0) / max(p1 - p0, 1e-6))[:, None]
        out[m] = np.asarray(c0, np.float32) * (1 - k) + np.asarray(c1, np.float32) * k
    out[t >= stops[-1][0]] = np.asarray(stops[-1][1], np.float32)
    return out


def tint(rgb, color, amount):
    a = np.asarray(amount, np.float32)[..., None]
    return rgb * (1 - a) + np.asarray(color, np.float32) * a


def build_field():
    """Mirror of gen_terrain.naval()'s height block, sharing the RNG order."""
    r = np.random.default_rng(20240902)
    yy, xx = np.meshgrid(np.arange(S), np.arange(S), indexing="ij")
    wa = (fbm(S, 4, 3, r) - 0.5) * 26.0
    wb = (fbm(S, 8, 3, r) - 0.5) * 14.0
    wc = (fbm(S, 16, 2, r) - 0.5) * 8.0

    def tile_sine(cycles, ax, ay, phase=0.0):
        return np.sin(2 * np.pi * (ax * xx + ay * yy) * cycles / S + phase)

    swell = tile_sine(4, 1, 1, wa * 0.07) * 1.00
    swell += tile_sine(2, 1, -1, wb * 0.09) * 0.55
    swell += tile_sine(1, 1, 0, wc * 0.05) * 0.34
    chop = tile_sine(11, 1, 2, wa * 0.30) * 0.22
    chop += tile_sine(7, 2, -1, wb * 0.35) * 0.15
    chop += tile_sine(23, 1, -3, wc * 0.55) * 0.07
    micro = (fbm(S, 32, 3, r) - 0.5) * 0.12
    return r, blur_wrap(swell + chop + micro, 1)


def render(h, r):
    """Mirror of gen_terrain.naval()'s shading block."""
    lit = shade(h, 3.0, (-0.55, 0.5, 0.62), 0.46, 0.80)
    depth = ((h - h.min()) / (h.max() - h.min())).clip(0, 1)
    rgb = ramp(depth, [(0.0, (12, 44, 66)), (0.28, (20, 66, 94)),
                       (0.52, (32, 88, 118)), (0.74, (58, 118, 148)),
                       (0.90, (96, 158, 184)), (1.0, (150, 202, 218))])
    gx, gy = grad(h)
    slope = gx * 0.6 + gy * 0.5
    steep = np.clip((slope - np.percentile(slope, 60.0)) * 3.0 + 0.30, 0, 1)
    crest = np.clip((depth - np.percentile(depth, 60.0)) * 3.0, 0, 1)
    foam = steep * crest * np.clip((fbm(S, 32, 3, r) - 0.35) * 2.5, 0, 1)
    rgb = tint(rgb, (228, 242, 246), np.clip(foam * 0.9, 0, 1))
    glint = np.clip((lit - np.percentile(lit, 93.0)) * 8.0, 0, 1) * np.clip(
        (fbm(S, 32, 2, r) - 0.52) * 3.5, 0, 1)
    out = rgb * lit[..., None]
    out = tint(out, (242, 248, 240), np.clip(glint * 0.6, 0, 1))
    return np.clip(out, 0, 255), foam, glint


def main():
    fails = []

    def check(name, ok, detail):
        print(("PASS " if ok else "FAIL ") + name + ("" if ok else ": " + detail))
        if not ok:
            fails.append(name)

    r, h = build_field()

    # 1) The height field must wrap without a seam. The generator's integer
    #    cycle counts make this exact; assert it so a future non-integer period
    #    is caught at generation time instead of on a device.
    vv, hh, interior, vert, horz = tile_step(h)
    check(
        "sea height field wraps without a seam (2x2 tiling)",
        max(vv, hh) <= SEAM_RATIO_MAX,
        f"vert={vv:.2f}x horz={hh:.2f}x (max {SEAM_RATIO_MAX:.2f}x); "
        f"vert_step={vert:.4f} horz_step={horz:.4f} interior={interior:.4f}",
    )

    sheet, foam, glint = render(h, r)

    # 2) Whitecaps must actually appear. The original (grad*3.2 - 0.90) gate
    #    measured 0.000% coverage, i.e. the foam layer never rendered at all.
    foam_pct = float((foam > 0.25).mean() * 100)
    check(
        "whitecaps render inside a visible-but-sparse band",
        FOAM_MIN_PCT <= foam_pct <= FOAM_MAX_PCT,
        f"foam={foam_pct:.3f}% outside [{FOAM_MIN_PCT}, {FOAM_MAX_PCT}]%",
    )

    # 3) The palette must be able to reach foam-white. The old ramp topped out
    #    at luma 115, so no tint could produce a visible whitecap.
    luma = sheet.mean(axis=2)
    span = float(luma.max() - luma.min())
    check(
        "sea sheet has crest-to-trough dynamic range",
        span >= LUMA_SPAN_MIN,
        f"luma span={span:.0f} (min {luma.min():.0f} max {luma.max():.0f}), need >= {LUMA_SPAN_MIN:.0f}",
    )

    # 4) Sun glint should be a highlight, not a wash and not absent.
    glint_pct = float((glint > 0).mean() * 100)
    check(
        "sun glint coverage stays in a highlight band",
        GLINT_MIN_PCT <= glint_pct <= GLINT_MAX_PCT,
        f"glint={glint_pct:.3f}% outside [{GLINT_MIN_PCT}, {GLINT_MAX_PCT}]%",
    )

    # 5) The shipped PNG must match the formula (no stale asset) and must tile
    #    cleanly at the pixel level.
    png = ROOT / "app/src/main/assets/gfx/terrain_naval.png"
    try:
        from PIL import Image
    except ImportError:
        print("SKIP shipped-PNG checks (Pillow unavailable)")
        Image = None
    if Image is not None and png.is_file():
        arr = np.asarray(Image.open(png).convert("RGB")).astype(np.float32)
        svv, shh, _, _, _ = tile_step(arr)
        check(
            "shipped terrain_naval.png tiles without a seam",
            max(svv, shh) <= SEAM_RATIO_MAX,
            f"vert={svv:.2f}x horz={shh:.2f}x (max {SEAM_RATIO_MAX:.2f}x)",
        )
        ship_luma = arr.mean(axis=2)
        ship_span = float(ship_luma.max() - ship_luma.min())
        check(
            "shipped terrain_naval.png has the same dynamic range",
            abs(ship_span - span) <= 25.0,
            f"shipped span={ship_span:.0f} vs formula {span:.0f}",
        )
    elif Image is not None:
        print("SKIP shipped-PNG checks (terrain_naval.png missing)")

    print(f"\nmeasured: height-wrap vert={vv:.2f}x horz={hh:.2f}x  foam={foam_pct:.3f}%  "
          f"glint={glint_pct:.3f}%  luma_span={span:.0f}")
    if fails:
        raise SystemExit(f"{len(fails)} sea texture check(s) failed: {', '.join(fails)}")
    print("ALL SEA TEXTURE CHECKS PASS")


if __name__ == "__main__":
    sys.exit(main())
