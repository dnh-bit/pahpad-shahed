# -*- coding: utf-8 -*-
"""0.0.9 atmosphere assets: horizon silhouettes, cloud deck, macro ground
variation and a directional box shadow. Deterministic (fixed seeds), numpy+Pillow.

    python3 tools/gen_atmosphere.py

Fog colours below MUST match EnvLook.kt, the silhouettes' bases dissolve into them.
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

OUT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/gfx'
W, H = 2048, 192
HORIZON_ROW = int(H * 0.86)   # image row that sits exactly on the game horizon

FOG = {
    'desert': 0xE6D2AE,
    'urban': 0xC4AE9C,
    'naval': 0xCFE0E8,
    'special': 0x9A7C88,
}


def rgb(c):
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], np.float32)


def periodic_1d(r, n, octaves, base_k=3, gain=0.55):
    """Sum of integer-frequency sines: seamless at x=0/x=n."""
    x = np.arange(n, dtype=np.float32) / n * 2 * np.pi
    out = np.zeros(n, np.float32)
    amp, k, norm = 1.0, base_k, 0.0
    for _ in range(octaves):
        for _j in range(3):
            kk = int(k * (0.8 + 0.5 * r.random())) + 1
            out += amp * np.sin(kk * x + r.random() * 2 * np.pi) / 3.0
        norm += amp
        amp *= gain
        k *= 2
    return out / norm  # roughly -1..1


def silhouette(height_px, color, fog, haze):
    """RGBA layer: ridge of per-column heights (px above the horizon row)."""
    y = np.arange(H, dtype=np.float32)[:, None]
    top = HORIZON_ROW - height_px[None, :]
    cov = np.clip(y - top + 0.5, 0.0, 1.0)            # 1px anti-aliased ridge
    cov[HORIZON_ROW:, :] = 1.0
    # atmospheric perspective: the base of distant relief is hazier
    t = np.clip((y - top) / np.maximum(height_px[None, :], 1.0), 0, 1)
    k = np.clip(haze + (1 - haze) * t ** 1.6 * 0.85, 0, 1)
    col = color[None, None, :] * (1 - k[..., None]) + fog[None, None, :] * k[..., None]
    return col, cov


def over(dst_col, dst_a, col, a):
    out_a = a + dst_a * (1 - a)
    out_c = (col * a[..., None] + dst_col * (dst_a * (1 - a))[..., None]) / np.maximum(out_a, 1e-6)[..., None]
    return out_c, out_a


def finish(name, col, a, fog):
    # below the horizon row the strip becomes pure fog and fades out into the ground haze
    y = np.arange(H)[:, None]
    below = y >= HORIZON_ROW
    fade = np.clip(1 - (y - HORIZON_ROW) / (H - HORIZON_ROW), 0, 1)
    col = np.where(below[..., None], fog[None, None, :], col)
    a = np.where(below, fade * np.ones((1, W)), a)
    img = np.dstack([np.clip(col, 0, 255), np.clip(a * 255, 0, 255)]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / f'horizon_{name}.png', optimize=True)


def horizon_desert():
    r = np.random.default_rng(9001)
    fog = rgb(FOG['desert'])
    far = 0.16 + 0.66 * (periodic_1d(r, W, 6, 2) * 0.5 + 0.5) ** 1.3
    # mesas: flatten crowns above a varying plateau
    plateau = 0.60 + 0.12 * periodic_1d(r, W, 2, 3)
    far = np.where(far > plateau, plateau + (far - plateau) * 0.12, far)
    near = 0.10 + 0.18 * (periodic_1d(r, W, 4, 5, 0.45) * 0.5 + 0.5)
    c1, a1 = silhouette(far * HORIZON_ROW * 0.95, rgb(0xA08A86), fog, 0.42)
    c2, a2 = silhouette(near * HORIZON_ROW, rgb(0xC9A57A), fog, 0.22)
    col, a = over(c1, a1, c2, a2)
    finish('desert', col, a, fog)


def horizon_urban():
    r = np.random.default_rng(9002)
    fog = rgb(FOG['urban'])
    def skyline(lo, hi, wmin, wmax, tower_p):
        h = np.zeros(W, np.float32)
        x = 0
        while x < W:
            w = int(r.integers(wmin, wmax))
            v = lo + (hi - lo) * r.random() ** 1.7
            if r.random() < tower_p:
                v = min(0.97, v * 1.6)
            h[x:x + w] = np.maximum(h[x:x + w], v)
            if r.random() < 0.25 and w > 6:        # antenna mast
                m = x + w // 2
                h[m:m + 1] = min(0.99, v + 0.1)
            x += w + int(r.integers(0, 4))
        return h
    far = skyline(0.18, 0.62, 6, 30, 0.08) * HORIZON_ROW
    near = skyline(0.06, 0.34, 10, 44, 0.05) * HORIZON_ROW
    c1, a1 = silhouette(far, rgb(0x7E8494), fog, 0.45)
    c2, a2 = silhouette(near, rgb(0x5B5F6C), fog, 0.24)
    col, a = over(c1, a1, c2, a2)
    # dusk: a sprinkle of warm windows on the nearer skyline
    yy, xx = np.nonzero(a2 > 0.99)
    pick = r.random(len(yy)) < 0.012
    for y0, x0 in zip(yy[pick], xx[pick]):
        if y0 < HORIZON_ROW - 2:
            col[y0, x0] = col[y0, x0] * 0.25 + rgb(0xFFCF8A) * 0.75
    finish('urban', col, a, fog)


def horizon_naval():
    r = np.random.default_rng(9003)
    fog = rgb(FOG['naval'])
    n = periodic_1d(r, W, 5, 2) * 0.5 + 0.5
    islands = np.clip((n - 0.58) / 0.42, 0, 1) ** 0.8 * 0.42
    c1, a1 = silhouette(islands * HORIZON_ROW, rgb(0x8398A6), fog, 0.5)
    finish('naval', c1, a1 * (islands[None, :] > 0.002) + a1 * 0, fog)


def horizon_special():
    r = np.random.default_rng(9004)
    fog = rgb(FOG['special'])
    def ridged(oct_, k):
        return 1 - np.abs(periodic_1d(r, W, oct_, k, 0.5))
    far = (0.35 + 0.6 * ridged(7, 2) ** 1.6) * HORIZON_ROW * 0.98
    near = (0.12 + 0.35 * ridged(6, 4) ** 2.0) * HORIZON_ROW
    c1, a1 = silhouette(far, rgb(0x4F4966), fog, 0.40)
    c2, a2 = silhouette(near, rgb(0x2F2B3B), fog, 0.18)
    col, a = over(c1, a1, c2, a2)
    finish('special', col, a, fog)


# ----------------------------------------------------------------- clouds
def periodic_value_noise(r, w, h, px, py):
    g = r.random((py + 1, px)).astype(np.float32)
    x = np.arange(w, dtype=np.float32) / w * px
    y = np.arange(h, dtype=np.float32) / h * py
    xi = np.floor(x).astype(int); xf = x - xi; xs = xf * xf * (3 - 2 * xf)
    yi = np.floor(y).astype(int); yf = y - yi; ys = yf * yf * (3 - 2 * yf)
    x0 = xi % px; x1 = (xi + 1) % px
    y0 = np.clip(yi, 0, py); y1 = np.clip(yi + 1, 0, py)
    a = g[:, x0] * (1 - xs) + g[:, x1] * xs
    return a[y0, :] * (1 - ys)[:, None] + a[y1, :] * ys[:, None]


def clouds():
    r = np.random.default_rng(4242)
    w, h = 2048, 320
    f = np.zeros((h, w), np.float32); amp = 1.0; norm = 0.0
    px, py = 7, 2
    for _ in range(6):
        f += amp * periodic_value_noise(r, w, h, px, py)
        norm += amp; amp *= 0.52; px *= 2; py *= 2
    f /= norm
    y = np.arange(h, dtype=np.float32)[:, None] / h
    # fewer, flatter clouds toward the horizon (bottom); thinner at the top edge
    cover = 0.55 + 0.08 * (1 - y)
    d = np.clip((f - cover) / 0.16, 0, 1)
    d *= np.clip(y / 0.12, 0, 1) * np.clip((1 - y) / 0.10, 0, 1) ** 0.7
    # self shadow: light from above
    up = np.roll(d, 5, axis=0)
    lit = np.clip(1.0 - 0.55 * np.clip(up - d + 0.35, 0, 1) * d, 0.55, 1.0)
    lit = lit * (0.86 + 0.14 * (1 - y))
    c = np.dstack([lit * 255, lit * 250, lit * 246])
    a = np.clip(d ** 0.9 * 235, 0, 255)
    img = Image.fromarray(np.dstack([np.clip(c, 0, 255), a]).astype(np.uint8), 'RGBA')
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    img.save(OUT / 'sky_clouds.png', optimize=True)


def macro():
    r = np.random.default_rng(777)
    n = 256
    f = np.zeros((n, n), np.float32); amp = 1.0; norm = 0.0; p = 4
    for _ in range(5):
        g = r.random((p, p)).astype(np.float32)
        x = np.arange(n, dtype=np.float32) / n * p
        xi = np.floor(x).astype(int); xf = x - xi; xs = xf * xf * (3 - 2 * xf)
        i0 = xi % p; i1 = (xi + 1) % p
        a = g[:, i0] * (1 - xs) + g[:, i1] * xs
        b = a[i0, :] * (1 - xs)[:, None] + a[i1, :] * xs[:, None]
        f += amp * b; norm += amp; amp *= 0.5; p *= 2
    f /= norm
    f = (f - f.min()) / (f.max() - f.min())
    v = 196 + f * 59   # multiply layer: 0.77 .. 1.0
    img = np.dstack([v, v * 0.995, v * 0.985]).astype(np.uint8)
    Image.fromarray(img, 'RGB').save(OUT / 'terrain_macro.png', optimize=True)


def shadow_box():
    n = 128
    img = Image.new('L', (n, n), 0)
    a = np.zeros((n, n), np.float32)
    a[22:106, 22:106] = 1.0
    im = Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9))
    alpha = np.asarray(im).astype(np.float32) / 255 * 235
    out = np.dstack([np.full((n, n), 8), np.full((n, n), 10), np.full((n, n), 14), alpha]).astype(np.uint8)
    Image.fromarray(out, 'RGBA').save(OUT / 'shadow_box.png', optimize=True)


if __name__ == '__main__':
    horizon_desert(); horizon_urban(); horizon_naval(); horizon_special()
    clouds(); macro(); shadow_box()
    print('ok')
