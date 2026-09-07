# -*- coding: utf-8 -*-
"""Alpha billboards (rocks, trees, scrub), soft shadow and HUD reticle."""
import sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import distance_transform_edt
import texlib as T

OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
SUN = (-0.55, -0.5, 0.72)  # screen-space: up is -y


def mask_from_draw(size, fn, supersample=3):
    big = (size[0] * supersample, size[1] * supersample)
    im = Image.new('L', big, 0)
    fn(ImageDraw.Draw(im), supersample)
    im = im.resize(size, Image.LANCZOS)
    return np.asarray(im, np.float32) / 255.0


def lighting(height, mask, strength):
    gx = (np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)) * 0.5
    gy = (np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)) * 0.5
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(height)
    inv = 1.0 / np.sqrt(nx * nx + ny * ny + nz * nz)
    lx, ly, lz = SUN
    ln = (lx * lx + ly * ly + lz * lz) ** 0.5
    d = ((nx * lx + ny * ly + nz * lz) * inv / ln).clip(0, 1)
    return (0.52 + 0.80 * d) * mask + (1 - mask)


def dome(hard, power=0.62):
    """Distance transform dome: gives billboards real volume, not a flat blob."""
    d = distance_transform_edt(hard > 0.5).astype(np.float32)
    if d.max() <= 0:
        return d
    return (d / d.max()) ** power


def blurmask(mask, radius):
    im = Image.fromarray((mask * 255).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(im, np.float32) / 255.0


def compose(rgb, alpha, path, margin=2):
    a = alpha.copy()
    a[:margin, :] = 0; a[-margin:, :] = 0
    a[:, :margin] = 0; a[:, -margin:] = 0
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(a * 255.0, 0, 255)])
    T.save_rgba(out, path)


# ------------------------------------------------------------------- rocks

def rock(path, seed, blobs):
    W, H = 256, 192
    r = np.random.default_rng(seed)

    def draw(d, ss):
        for (cx, cy, rw, rh) in blobs:
            d.ellipse([(cx - rw) * ss, (cy - rh) * ss, (cx + rw) * ss, (cy + rh) * ss], fill=255)
        d.rectangle([0, (H - 4) * ss, W * ss, H * ss], fill=0)

    m = mask_from_draw((W, H), draw)
    m = (m > 0.45).astype(np.float32)
    m = blurmask(m, 1.2)
    hard = (m > 0.5).astype(np.float32)
    # real volume from a distance-transform dome, plus faceted rock detail
    dist = dome(hard, 0.55)
    noise = T.fbm(256, 10, 4, r)[:H, :W]
    facet = T.ridged(256, 13, 2, r)[:H, :W]
    height = dist * 44.0 + noise * 3.0 + facet * 2.6
    lit = lighting(height, hard, 2.4)
    # key light from upper-left, so the right/lower flank stays in shadow
    yy, xx = np.meshgrid(np.linspace(0, 1, H), np.linspace(0, 1, W), indexing='ij')
    form = np.clip(0.42 + 0.72 * ((1 - xx) * 0.55 + (1 - yy) * 0.45), 0, 1.35)
    tone = (noise * 0.42 + facet * 0.22 + dist * 0.36).clip(0, 1)
    rgb = T.ramp(tone, [(0.0, (56, 50, 44)), (0.35, (84, 76, 66)),
                        (0.62, (112, 102, 88)), (0.85, (142, 130, 112)), (1.0, (172, 158, 136))])
    crack = np.clip((T.ridged(256, 34, 2, r)[:H, :W] - 0.945) * 26.0, 0, 1)
    crack = np.maximum(crack, np.clip((T.ridged(256, 60, 2, r)[:H, :W] - 0.965) * 30.0, 0, 1) * 0.6)
    rgb = T.tint(rgb, (38, 34, 30), crack * 0.7)
    grain = T.specks(256, 700, r, 0.6, 1.6)[:H, :W]
    rgb = T.tint(rgb, (178, 166, 146), grain * 0.28)
    ground = np.clip((yy - 0.78) / 0.22, 0, 1)
    rgb = T.tint(rgb, (34, 30, 27), ground * 0.55)
    rgb = T.tint(rgb, (198, 174, 134), np.clip((yy - 0.93) / 0.07, 0, 1) * 0.4)
    compose(rgb * (lit * form)[..., None], m, path)


# ------------------------------------------------------------------- trees

def canopy_tree(path, seed, kind):
    S = 256
    r = np.random.default_rng(seed)
    trunk_top = {'acacia': 0.52, 'poplar': 0.78, 'palm': 0.70}[kind]

    def draw_trunk(d, ss):
        bx = S * 0.5
        base_w = S * (0.030 if kind != 'palm' else 0.022)
        top_w = base_w * 0.45
        ty = S * (1.0 - trunk_top)
        lean = S * (0.05 if kind == 'palm' else 0.0)
        d.polygon([((bx - base_w) * ss, S * ss), ((bx + base_w) * ss, S * ss),
                   ((bx + top_w + lean) * ss, ty * ss), ((bx - top_w + lean) * ss, ty * ss)], fill=255)
        if kind == 'acacia':
            for sgn in (-1, 1):
                d.line([(bx * ss, (S * 0.62) * ss), ((bx + sgn * S * 0.16) * ss, (S * 0.47) * ss)],
                       fill=255, width=int(4 * ss))

    def draw_leaves(d, ss):
        if kind == 'acacia':
            cy = S * 0.36
            for _ in range(90):
                a = r.uniform(0, 2 * np.pi)
                rad = r.uniform(0, 1) ** 0.55
                cx = S * 0.5 + np.cos(a) * rad * S * 0.42
                y = cy + np.sin(a) * rad * S * 0.15
                rr = r.uniform(S * 0.045, S * 0.085)
                d.ellipse([(cx - rr) * ss, (y - rr * 0.72) * ss, (cx + rr) * ss, (y + rr * 0.72) * ss], fill=255)
        elif kind == 'poplar':
            d.ellipse([(S * 0.5 - S * 0.155) * ss, S * 0.08 * ss,
                       (S * 0.5 + S * 0.155) * ss, S * 0.66 * ss], fill=255)
            for _ in range(150):
                t = r.uniform(0, 1)
                y = S * (0.08 + t * 0.58)
                spread = S * 0.175 * np.sin(np.pi * (0.18 + t * 0.80))
                cx = S * 0.5 + r.uniform(-1, 1) * spread
                rr = r.uniform(S * 0.030, S * 0.058)
                d.ellipse([(cx - rr) * ss, (y - rr) * ss, (cx + rr) * ss, (y + rr) * ss], fill=255)
        else:  # palm fronds
            cx, cy = S * 0.53, S * 0.30
            for k in range(11):
                a = -np.pi * 0.5 + (k - 5) * 0.30 + r.uniform(-0.06, 0.06)
                ln = S * r.uniform(0.30, 0.42)
                pts = []
                for i in range(9):
                    t = i / 8.0
                    droop = t * t * S * 0.20
                    pts.append(((cx + np.cos(a) * ln * t) * ss, (cy + np.sin(a) * ln * t + droop) * ss))
                d.line(pts, fill=255, width=int(max(2, S * 0.020) * ss))
                for i in range(1, 9):
                    t = i / 8.0
                    droop = t * t * S * 0.20
                    px = cx + np.cos(a) * ln * t
                    py = cy + np.sin(a) * ln * t + droop
                    fl = S * 0.055 * (1 - t * 0.5)
                    d.line([(px * ss, py * ss), ((px + np.cos(a + 1.5) * fl) * ss, (py + np.sin(a + 1.5) * fl) * ss)],
                           fill=255, width=int(max(1, S * 0.008) * ss))

    trunk = mask_from_draw((S, S), draw_trunk)
    leaves = mask_from_draw((S, S), draw_leaves)
    leaves = (leaves > 0.35).astype(np.float32)
    leaves = blurmask(leaves, 1.0)
    trunk = (trunk > 0.4).astype(np.float32)
    m = np.clip(trunk + leaves, 0, 1)

    lclump = T.fbm(S, 14, 4, r)
    lfine = T.fbm(S, 46, 3, r)
    height = dome((leaves > 0.5).astype(np.float32), 0.7) * 20.0 + lclump * 3.4 + lfine * 1.2
    lit = lighting(height, np.clip(leaves, 0, 1), 1.5)

    leafcol = {
        'acacia': [(0.0, (38, 52, 30)), (0.45, (62, 82, 44)), (0.75, (92, 112, 58)), (1.0, (128, 146, 78))],
        'poplar': [(0.0, (30, 50, 34)), (0.45, (48, 76, 46)), (0.78, (74, 104, 58)), (1.0, (110, 138, 78))],
        'palm':   [(0.0, (36, 54, 32)), (0.45, (58, 84, 42)), (0.78, (92, 118, 56)), (1.0, (134, 154, 84))],
    }[kind]
    tone = (lclump * 0.6 + lfine * 0.4)
    # sunlit side brighter
    yy, xx = np.meshgrid(np.linspace(0, 1, S), np.linspace(0, 1, S), indexing='ij')
    tone = np.clip(tone * 0.72 + (1 - xx) * 0.16 + (1 - yy) * 0.16, 0, 1)
    rgbleaf = T.ramp(tone, leafcol)

    tg = T.fbm(S, 30, 3, r)
    trunkcol = T.ramp(tg, [(0.0, (58, 46, 34)), (0.5, (86, 68, 48)), (1.0, (116, 96, 70))])
    bark = np.clip((T.ridged(S, 60, 2, r) - 0.72) * 4.0, 0, 1)
    trunkcol = T.tint(trunkcol, (44, 34, 26), bark * 0.55)
    tl = lighting(blurmask(trunk, 3) * 8.0, trunk, 2.0)
    trunkcol = trunkcol * tl[..., None]

    rgb = rgbleaf * lit[..., None]
    lm = np.clip(leaves, 0, 1)[..., None]
    rgb = trunkcol * (1 - lm) + rgb * lm
    compose(rgb, m, path)


def bush(path, seed):
    S = 128
    r = np.random.default_rng(seed)

    def draw(d, ss):
        # low dense mound: width shrinks with height, anchored on the ground line
        for _ in range(150):
            t = r.uniform(0, 1) ** 0.8
            cy = S * (0.98 - t * 0.52)
            spread = S * 0.40 * (1.0 - t * 0.72)
            cx = S * 0.5 + r.uniform(-1, 1) * spread
            rr = r.uniform(S * 0.055, S * 0.105)
            d.ellipse([(cx - rr) * ss, (cy - rr * 0.85) * ss,
                       (cx + rr) * ss, (cy + rr * 0.85) * ss], fill=255)
        d.rectangle([0, (S - 2) * ss, S * ss, S * ss], fill=0)

    m = mask_from_draw((S, S), draw)
    m = blurmask((m > 0.4).astype(np.float32), 0.9)
    hard = (m > 0.5).astype(np.float32)
    n = T.fbm(S, 12, 4, r)
    height = dome(hard, 0.7) * 14.0 + n * 2.8
    lit = lighting(height, hard, 1.6)
    yy = np.linspace(0, 1, S)[:, None] * np.ones((1, S), np.float32)
    tone = np.clip(n * 0.7 + (1 - yy) * 0.3, 0, 1)
    rgb = T.ramp(tone, [(0.0, (52, 52, 34)), (0.45, (82, 82, 50)), (0.8, (116, 112, 68)), (1.0, (146, 140, 92))])
    rgb = T.tint(rgb, (58, 48, 34), np.clip((yy - 0.82) / 0.18, 0, 1) * 0.4)
    compose(rgb * lit[..., None], m, path)


# ------------------------------------------------------------------- utility

def soft_shadow(path):
    S = 128
    yy, xx = np.meshgrid(np.linspace(-1, 1, S), np.linspace(-1, 1, S), indexing='ij')
    d = np.sqrt(xx * xx + yy * yy)
    a = np.clip(1.0 - d, 0, 1) ** 1.9
    a = a * 0.92
    rgb = np.zeros((S, S, 3), np.float32) + np.asarray((6, 8, 10), np.float32)
    out = np.dstack([rgb, np.clip(a * 255, 0, 255)])
    T.save_rgba(out, path)


def reticle(path):
    S = 64
    im = Image.new('RGBA', (S * 4, S * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = S * 2
    R = int(S * 1.55)
    amber = (255, 197, 61, 255)
    dim = (255, 197, 61, 140)
    d.ellipse([c - R, c - R, c + R, c + R], outline=dim, width=int(S * 0.14))
    for k in range(4):
        a0 = 45 + k * 90 - 20
        d.arc([c - R, c - R, c + R, c + R], a0, a0 + 40, fill=amber, width=int(S * 0.26))
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        d.line([c + dx * int(R * 0.30), c + dy * int(R * 0.30),
                c + dx * int(R * 0.72), c + dy * int(R * 0.72)], fill=amber, width=int(S * 0.16))
    d.ellipse([c - int(S * 0.16), c - int(S * 0.16), c + int(S * 0.16), c + int(S * 0.16)], fill=amber)
    im = im.resize((S, S), Image.LANCZOS)
    im.save(path, optimize=True)


if __name__ == '__main__':
    rock(os.path.join(OUT, 'prop_rock1.png'), 5101,
         [(96, 138, 62, 44), (150, 132, 52, 50), (124, 112, 46, 38), (66, 152, 34, 26), (186, 154, 30, 24)])
    rock(os.path.join(OUT, 'prop_rock2.png'), 5102,
         [(128, 128, 80, 56), (86, 146, 40, 34), (176, 148, 44, 36), (132, 96, 40, 30)])
    print('rocks')
    canopy_tree(os.path.join(OUT, 'prop_tree1.png'), 5201, 'acacia')
    canopy_tree(os.path.join(OUT, 'prop_tree2.png'), 5202, 'poplar')
    canopy_tree(os.path.join(OUT, 'prop_palm.png'), 5203, 'palm')
    bush(os.path.join(OUT, 'prop_bush.png'), 5204)
    print('trees')
    soft_shadow(os.path.join(OUT, 'shadow_soft.png'))
    reticle(os.path.join(OUT, 'hud_target.png'))
    print('utility')
