# -*- coding: utf-8 -*-
"""Ground + material textures. Run: python3 gen_terrain.py <outdir>"""
import sys, os
import numpy as np
import texlib as T

OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
S = 512
M = 256


def desert():
    r = np.random.default_rng(20240901)
    dune = T.fbm(S, 3, 4, r)
    mid = T.fbm(S, 11, 4, r)
    fine = T.fbm(S, 40, 3, r)
    wx = (T.fbm(S, 5, 3, r) - 0.5) * 34.0
    wy = (T.fbm(S, 5, 3, r) - 0.5) * 34.0
    yy, xx = np.meshgrid(np.arange(S), np.arange(S), indexing='ij')
    # wind ripples: two wavelengths, domain warped, so they curve like real sand
    ph = (xx * 0.62 + yy * 0.38) * (2 * np.pi / 13.0)
    ripple = np.sin(ph + (wx * 0.55)) * 0.5 + 0.5
    ripple2 = np.sin((xx * 0.30 - yy * 0.52) * (2 * np.pi / 31.0) + wy * 0.3) * 0.5 + 0.5
    ripple = ripple * 0.72 + ripple2 * 0.28
    ripple *= (0.35 + 0.65 * mid)
    peb = T.specks(S, 850, r, 0.7, 2.3)
    h = dune * 3.4 + mid * 0.9 + ripple * 0.55 + fine * 0.22 + peb * 0.9
    lit = T.shade(h, 2.2, ambient=0.60, diffuse=0.74)
    occ = T.ao(h, 7, 0.5)
    tone = (dune * 0.6 + mid * 0.4).clip(0, 1)
    rgb = T.ramp(tone, [(0.0, (152, 118, 76)), (0.35, (183, 148, 100)),
                        (0.62, (206, 174, 124)), (0.85, (221, 194, 148)),
                        (1.0, (231, 209, 168))])
    # pebbles read as cooler grey stone
    rgb = T.tint(rgb, (128, 118, 104), peb * 0.75)
    # sparse dry scrub in the hollows
    scrub = np.clip((T.fbm(S, 17, 4, r) - 0.63) * 4.2, 0, 1) * (1.0 - dune * 0.6)
    rgb = T.tint(rgb, (104, 105, 72), scrub * 0.55)
    # salt / dust bloom on the crests
    rgb = T.tint(rgb, (238, 226, 200), np.clip((dune - 0.72) * 2.4, 0, 1) * 0.35)
    out = rgb * (lit * occ)[..., None]
    T.save_rgb(out, os.path.join(OUT, 'terrain_desert.png'))


def naval():
    r = np.random.default_rng(20240902)
    yy, xx = np.meshgrid(np.arange(S), np.arange(S), indexing='ij')
    wa = (T.fbm(S, 4, 3, r) - 0.5) * 26.0
    wb = (T.fbm(S, 9, 3, r) - 0.5) * 14.0
    swell = np.sin((xx * 0.86 + yy * 0.51) * (2 * np.pi / 170.0) + wa * 0.07) * 1.00
    swell += np.sin((xx * -0.42 + yy * 0.90) * (2 * np.pi / 96.0) + wb * 0.09) * 0.55
    chop = np.sin((xx * 0.70 - yy * 0.71) * (2 * np.pi / 34.0) + wa * 0.30) * 0.15
    chop += np.sin((xx * 0.95 + yy * 0.30) * (2 * np.pi / 25.0) + wb * 0.35) * 0.10
    micro = (T.fbm(S, 52, 3, r) - 0.5) * 0.10
    h = T.blur_wrap(swell + chop + micro, 2)
    lit = T.shade(h, 5.5, light=(-0.55, 0.5, 0.62), ambient=0.70, diffuse=0.46)
    depth = ((h - h.min()) / (h.max() - h.min())).clip(0, 1)
    rgb = T.ramp(depth, [(0.0, (16, 52, 74)), (0.30, (24, 70, 96)),
                         (0.58, (34, 88, 114)), (0.80, (46, 106, 132)),
                         (1.0, (68, 130, 152))])
    # whitecaps only on the steep windward faces
    gx, gy = T.grad(h)
    steep = np.clip((gx * 0.6 + gy * 0.5) * 3.2 - 0.90, 0, 1)
    crest = np.clip((depth - 0.88) * 7.0, 0, 1)
    foam = np.clip(steep * crest * 1.4, 0, 1) * np.clip((T.fbm(S, 120, 3, r) - 0.45) * 3.0, 0, 1)
    rgb = T.tint(rgb, (224, 238, 242), foam * 0.75)
    glint = np.clip((lit - 1.05) * 7.0, 0, 1) * np.clip((T.fbm(S, 150, 2, r) - 0.55) * 3.5, 0, 1)
    out = rgb * lit[..., None]
    out = T.tint(out, (238, 244, 236), np.clip(glint * 0.55, 0, 1))
    T.save_rgb(out, os.path.join(OUT, 'terrain_naval.png'))


def urban():
    r = np.random.default_rng(20240903)
    agg = T.fbm(S, 150, 3, r)
    grime = T.fbm(S, 7, 4, r)
    slab = np.zeros((S, S), np.float32)
    joint = np.zeros((S, S), np.float32)
    step = 128
    tone = np.zeros((S, S), np.float32)
    for j in range(S // step):
        for i in range(S // step):
            tone[j * step:(j + 1) * step, i * step:(i + 1) * step] = r.uniform(0.34, 0.66)
    for k in range(S // step + 1):
        T.hband(joint, k * step - 1, 3, 1.0)
        T.vband(joint, k * step - 1, 3, 1.0)
    joint = T.blur_wrap(joint, 1)
    cracks = np.clip((T.ridged(S, 30, 2, r) - 0.955) * 26.0, 0, 1)
    cracks = np.maximum(cracks, np.clip((T.ridged(S, 55, 2, r) - 0.965) * 30.0, 0, 1) * 0.6)
    cracks *= np.clip((T.fbm(S, 5, 3, r) - 0.40) * 2.6, 0, 1)
    patch = np.clip((T.fbm(S, 6, 3, r) - 0.64) * 3.2, 0, 1)
    grav = T.specks(S, 1400, r, 0.6, 1.5)
    h = agg * 0.30 + grime * 0.5 + grav * 0.55 - joint * 1.5 - cracks * 1.2
    lit = T.shade(h, 2.6, ambient=0.62, diffuse=0.70)
    base = T.ramp(tone * 0.55 + grime * 0.45, [(0.0, (74, 76, 76)), (0.4, (104, 105, 101)),
                                               (0.7, (131, 131, 124)), (1.0, (156, 155, 146))])
    base = T.tint(base, (62, 62, 62), patch * 0.45)              # asphalt repairs
    base = T.tint(base, (145, 142, 130), grav * 0.5)             # loose gravel
    base = T.tint(base, (30, 31, 31), np.clip(joint, 0, 1) * 0.75)
    base = T.tint(base, (44, 44, 42), cracks * 0.7)
    # tyre scuffs and oil
    oil = np.clip((T.fbm(S, 20, 3, r) - 0.70) * 5.0, 0, 1)
    base = T.tint(base, (28, 28, 30), oil * 0.45)
    dust = np.clip((T.fbm(S, 4, 3, r) - 0.5) * 2.0, 0, 1)
    base = T.tint(base, (150, 136, 112), dust * 0.22)
    T.save_rgb(base * lit[..., None], os.path.join(OUT, 'terrain_urban.png'))


def special():
    r = np.random.default_rng(20240904)
    plate = T.fbm(S, 7, 3, r)
    rough = T.fbm(S, 30, 4, r)
    fis = np.clip((T.ridged(S, 40, 2, r) - 0.962) * 30.0, 0, 1)
    fis = np.maximum(fis, np.clip((T.ridged(S, 70, 2, r) - 0.972) * 34.0, 0, 1) * 0.6)
    fis *= np.clip((T.fbm(S, 6, 3, r) - 0.38) * 2.4, 0, 1)
    rub = T.specks(S, 5200, r, 0.8, 3.2)
    boulder = T.specks(S, 90, r, 5.0, 13.0)
    h = plate * 1.3 + rough * 0.6 + rub * 1.4 + boulder * 3.0 - fis * 1.2
    lit = T.shade(h, 2.2, ambient=0.64, diffuse=0.62)
    occ = T.ao(h, 6, 0.45)
    tone = (plate * 0.45 + rough * 0.55)
    rgb = T.ramp(tone, [(0.0, (74, 70, 66)), (0.3, (98, 92, 85)),
                        (0.58, (122, 113, 101)), (0.8, (144, 132, 116)), (1.0, (164, 151, 132))])
    # iron-oxide dust settled in the low ground
    ironox = np.clip((0.55 - plate) * 2.0, 0, 1) * (0.4 + 0.6 * rough)
    rgb = T.tint(rgb, (140, 92, 62), ironox * 0.30)
    rgb = T.tint(rgb, (172, 164, 150), rub * 0.38)
    rgb = T.tint(rgb, (62, 57, 54), fis * 0.55)
    rgb = T.tint(rgb, (150, 140, 126), boulder * 0.45)
    T.save_rgb(rgb * (lit * occ)[..., None], os.path.join(OUT, 'terrain_special.png'))


# ------------------------------------------------------------------ materials

def wall():
    r = np.random.default_rng(20240911)
    h = np.zeros((M, M), np.float32)
    glassmask = np.zeros((M, M), np.float32)
    frame = np.zeros((M, M), np.float32)
    sill = np.zeros((M, M), np.float32)
    mullion = np.zeros((M, M), np.float32)
    cols, rows = 5, 7
    cw, ch = M / cols, M / rows
    lit_pane = np.zeros((M, M), np.float32)
    for j in range(rows):
        for i in range(cols):
            gx = int(i * cw + cw * 0.16)
            gy = int(j * ch + ch * 0.16)
            gw = int(cw * 0.68)
            gh = int(ch * 0.58)
            T.rect(frame, gx - 1, gy - 1, gw + 2, gh + 2, 1.0)
            T.rect(glassmask, gx, gy, gw, gh, 1.0)
            T.rect(sill, gx - 2, gy + gh + 1, gw + 4, 2, 1.0)
            T.rect(mullion, gx + gw // 2, gy, 1, gh, 1.0)
            if r.random() < 0.22:
                T.rect(lit_pane, gx, gy, gw, gh, 1.0)
    # ground floor band: shutters instead of windows
    T.rect(glassmask, 0, M - int(ch * 0.55), M, int(ch * 0.42), 0.0)
    conc = T.fbm(M, 9, 4, r)
    fine = T.fbm(M, 60, 3, r)
    seam = np.zeros((M, M), np.float32)
    for k in range(cols + 1):
        T.vband(seam, int(k * cw) - 1, 2, 1.0)
    for k in range(rows + 1):
        T.hband(seam, int(k * ch) - 1, 2, 0.8)
    h += conc * 0.4 + fine * 0.15 + sill * 1.4 + frame * 0.5 - glassmask * 1.0 - seam * 0.7
    lit = T.shade(h, 2.4, ambient=0.62, diffuse=0.72)
    occ = T.ao(h, 5, 0.8)
    rgb = T.ramp(conc * 0.7 + fine * 0.3, [(0.0, (140, 136, 122)), (0.5, (168, 163, 148)),
                                           (1.0, (196, 190, 174))])
    rgb = T.tint(rgb, (110, 106, 96), seam * 0.6)
    # window glass: dark, with a sky reflection gradient top-down inside each pane
    yy, xx = np.meshgrid(np.arange(M), np.arange(M), indexing='ij')
    localy = (yy % max(1, int(ch))) / max(1.0, ch)
    glass = T.ramp(np.clip(1.0 - localy * 1.5, 0, 1), [(0.0, (26, 34, 42)), (0.5, (42, 58, 70)),
                                                       (1.0, (104, 132, 148))])
    rgb = rgb * (1 - glassmask[..., None]) + glass * glassmask[..., None]
    rgb = T.tint(rgb, (188, 168, 118), lit_pane * glassmask * 0.55)
    rgb = T.tint(rgb, (86, 82, 74), mullion * 0.6)
    rgb = T.tint(rgb, (128, 124, 114), frame * (1 - glassmask) * 0.35)
    stain = T.streaks(M, 90, r, 46, 2.4) * (1 - glassmask)
    rgb = T.tint(rgb, (96, 92, 80), stain * 0.42)
    dirt = np.clip((T.fbm(M, 5, 3, r) - 0.52) * 3.0, 0, 1)
    rgb = T.tint(rgb, (112, 104, 88), dirt * 0.3 * (1 - glassmask))
    out = rgb * (lit * occ)[..., None]
    # glass keeps a hard specular streak so it does not muddy
    out = T.tint(out, (196, 214, 226), glassmask * (1 - lit_pane) * np.clip((T.fbm(M, 24, 2, r) - 0.66) * 4, 0, 1) * 0.45)
    T.save_rgb(out, os.path.join(OUT, 'material_wall.png'))


def roof():
    r = np.random.default_rng(20240912)
    h = np.zeros((M, M), np.float32)
    grav = T.specks(M, 5200, r, 0.6, 1.5)
    felt = T.fbm(M, 40, 3, r)
    big = T.fbm(M, 6, 3, r)
    unit = np.zeros((M, M), np.float32)
    top = np.zeros((M, M), np.float32)
    # parapet
    par = np.zeros((M, M), np.float32)
    for k in range(6):
        T.hband(par, k, 1, 1.0); T.hband(par, M - 1 - k, 1, 1.0)
        T.vband(par, k, 1, 1.0); T.vband(par, M - 1 - k, 1, 1.0)
    # HVAC boxes / stairwell / vents
    boxes = [(34, 40, 62, 44), (150, 30, 52, 36), (44, 150, 74, 58), (168, 140, 46, 46),
             (110, 96, 30, 26), (206, 200, 30, 30)]
    shadow = np.zeros((M, M), np.float32)
    louvre = np.zeros((M, M), np.float32)
    for (bx, by, bw, bh) in boxes:
        T.rect(shadow, bx + 5, by + 6, bw, bh, 1.0)
        T.rect(unit, bx, by, bw, bh, 1.0)
        T.rect(top, bx + 2, by + 2, bw - 4, bh - 4, 1.0)
        for lv in range(by + 6, by + bh - 4, 5):
            T.rect(louvre, bx + 5, lv, bw - 10, 2, 1.0)
    shadow = np.clip(shadow - unit, 0, 1)
    seam = np.zeros((M, M), np.float32)
    for k in range(1, 4):
        T.hband(seam, int(k * M / 4), 2, 1.0)
    h += grav * 0.6 + felt * 0.3 + big * 0.5 + unit * 5.0 + top * 1.2 + par * 4.0 - louvre * 0.9 - seam * 0.8
    lit = T.shade(h, 2.0, ambient=0.60, diffuse=0.76)
    occ = T.ao(h, 6, 0.9)
    rgb = T.ramp(felt * 0.5 + big * 0.5, [(0.0, (78, 78, 74)), (0.45, (104, 103, 96)),
                                          (0.8, (128, 126, 116)), (1.0, (148, 145, 133))])
    rgb = T.tint(rgb, (150, 146, 134), grav * 0.55)
    pond = np.clip((0.42 - big) * 3.2, 0, 1)
    rgb = T.tint(rgb, (62, 66, 66), pond * 0.4)
    rgb = T.tint(rgb, (128, 132, 130), unit * 0.9)
    rgb = T.tint(rgb, (172, 176, 172), top * 0.85)
    rgb = T.tint(rgb, (96, 100, 100), louvre * 0.6)
    rgb = T.tint(rgb, (34, 34, 32), shadow * 0.55)
    rgb = T.tint(rgb, (176, 172, 160), par * 0.7)
    rust = T.streaks(M, 40, r, 26, 2.0) * unit
    rgb = T.tint(rgb, (126, 78, 48), rust * 0.5)
    T.save_rgb(rgb * (lit * occ)[..., None], os.path.join(OUT, 'material_roof.png'))


def concrete():
    r = np.random.default_rng(20240913)
    conc = T.fbm(M, 8, 5, r)
    fine = T.fbm(M, 70, 3, r)
    board = np.zeros((M, M), np.float32)
    for k in range(0, M, 32):
        T.hband(board, k, 2, 1.0)
    ties = np.zeros((M, M), np.float32)
    for j in range(16, M, 64):
        for i in range(24, M, 56):
            T.rect(ties, i, j, 4, 4, 1.0)
    chips = np.clip((T.fbm(M, 34, 3, r) - 0.76) * 7.0, 0, 1)
    agg = T.specks(M, 900, r, 0.6, 1.7)
    h = conc * 0.6 + fine * 0.25 + agg * 0.4 - board * 1.1 - ties * 1.6 - chips * 1.8
    lit = T.shade(h, 2.6, ambient=0.64, diffuse=0.70)
    occ = T.ao(h, 5, 0.8)
    rgb = T.ramp(conc * 0.65 + fine * 0.35, [(0.0, (118, 118, 112)), (0.45, (146, 145, 137)),
                                             (0.78, (170, 168, 158)), (1.0, (188, 185, 174))])
    rgb = T.tint(rgb, (98, 98, 92), board * 0.5)
    rgb = T.tint(rgb, (78, 76, 70), ties * 0.7)
    rgb = T.tint(rgb, (128, 124, 112), agg * 0.35)
    rgb = T.tint(rgb, (108, 104, 94), chips * 0.6)
    rust = T.streaks(M, 60, r, 44, 2.2)
    rgb = T.tint(rgb, (132, 96, 62), rust * 0.34)
    moss = np.clip((T.fbm(M, 5, 3, r) - 0.62) * 4.0, 0, 1)
    rgb = T.tint(rgb, (92, 100, 78), moss * 0.3)
    T.save_rgb(rgb * (lit * occ)[..., None], os.path.join(OUT, 'material_concrete.png'))


def metal():
    """Warship / hardware grey steel: plate seams, weld beads, rivets, rust."""
    r = np.random.default_rng(20240914)
    plate = np.zeros((M, M), np.float32)
    weld = np.zeros((M, M), np.float32)
    rivet = np.zeros((M, M), np.float32)
    pw, ph = 64, 42
    tone = np.zeros((M, M), np.float32)
    for j in range(0, M, ph):
        for i in range(0, M, pw):
            off = (pw // 2) if (j // ph) % 2 else 0
            x0 = (i + off) % M
            tone[j:j + ph, :] = tone[j:j + ph, :]
            T.rect(plate, x0, j, pw - 2, ph - 2, r.uniform(0.35, 0.65))
    for j in range(0, M, ph):
        T.hband(weld, j, 2, 1.0)
    for i in range(0, M, pw):
        T.vband(weld, i, 2, 1.0)
    for j in range(6, M, ph):
        for i in range(6, M, 9):
            T.rect(rivet, i, j, 2, 2, 1.0)
        for i in range(6, M, 9):
            T.rect(rivet, i, (j + ph - 10) % M, 2, 2, 1.0)
    grain = T.fbm(M, 100, 3, r)
    dent = T.fbm(M, 14, 3, r)
    h = plate * 0.5 + grain * 0.2 + dent * 0.45 + weld * 1.3 + rivet * 1.8
    lit = T.shade(h, 2.2, ambient=0.62, diffuse=0.74)
    occ = T.ao(h, 4, 0.7)
    base = T.ramp(plate * 0.55 + dent * 0.45, [(0.0, (86, 94, 98)), (0.45, (108, 117, 120)),
                                               (0.8, (128, 137, 139)), (1.0, (146, 154, 155))])
    base = T.tint(base, (150, 158, 158), weld * 0.35)
    base = T.tint(base, (156, 162, 162), rivet * 0.5)
    rust = T.streaks(M, 120, r, 40, 2.0)
    rust = np.maximum(rust, np.clip((T.fbm(M, 18, 4, r) - 0.72) * 5.0, 0, 1))
    base = T.tint(base, (128, 72, 42), rust * 0.5)
    scuff = np.clip((T.fbm(M, 46, 3, r) - 0.66) * 4.0, 0, 1)
    base = T.tint(base, (170, 176, 176), scuff * 0.3)
    T.save_rgb(base * (lit * occ)[..., None], os.path.join(OUT, 'material_metal.png'))


def deck():
    """Non-skid ship deck with safety markings and tie-downs."""
    r = np.random.default_rng(20240915)
    grit = T.specks(M, 9000, r, 0.5, 1.2)
    grain = T.fbm(M, 80, 3, r)
    wear = T.fbm(M, 7, 3, r)
    lines = np.zeros((M, M), np.float32)
    T.hband(lines, 18, 4, 1.0)
    T.hband(lines, M - 22, 4, 1.0)
    T.vband(lines, 20, 4, 1.0)
    T.vband(lines, M - 24, 4, 1.0)
    hatch = np.zeros((M, M), np.float32)
    hrim = np.zeros((M, M), np.float32)
    for (hx, hy) in [(66, 58), (160, 150)]:
        T.rect(hrim, hx - 3, hy - 3, 44, 34, 1.0)
        T.rect(hatch, hx, hy, 38, 28, 1.0)
    rings = np.zeros((M, M), np.float32)
    for j in range(40, M, 56):
        for i in range(48, M, 60):
            T.rect(rings, i, j, 5, 5, 1.0)
    seam = np.zeros((M, M), np.float32)
    for k in range(0, M, 86):
        T.hband(seam, k, 2, 1.0)
    h = grit * 0.35 + grain * 0.2 + hrim * 1.4 + rings * 1.5 - hatch * 0.6 - seam * 0.7
    lit = T.shade(h, 2.0, ambient=0.66, diffuse=0.66)
    occ = T.ao(h, 4, 0.7)
    base = T.ramp(grain * 0.5 + wear * 0.5, [(0.0, (58, 63, 66)), (0.5, (76, 82, 84)),
                                             (1.0, (96, 102, 103))])
    base = T.tint(base, (110, 116, 116), grit * 0.35)
    base = T.tint(base, (196, 168, 52), lines * 0.85)         # yellow safety lines
    base = T.tint(base, (88, 95, 97), hatch * 0.6)
    base = T.tint(base, (134, 140, 140), rings * 0.6)
    rust = T.streaks(M, 70, r, 30, 1.8)
    base = T.tint(base, (124, 74, 44), rust * 0.4)
    base = T.tint(base, (120, 126, 126), np.clip((wear - 0.66) * 4, 0, 1) * 0.35)
    T.save_rgb(base * (lit * occ)[..., None], os.path.join(OUT, 'material_deck.png'))


def camo():
    """Three-tone desert camouflage with dust, wear and fasteners."""
    r = np.random.default_rng(20240916)
    a = T.fbm(M, 5, 3, r)
    b = T.fbm(M, 9, 3, r)
    m1 = (a > 0.52).astype(np.float32)
    m2 = ((b > 0.58) & (a <= 0.52)).astype(np.float32)
    m1 = T.blur_wrap(m1, 1)
    m2 = T.blur_wrap(m2, 1)
    panel = np.zeros((M, M), np.float32)
    for k in range(0, M, 84):
        T.hband(panel, k, 2, 1.0)
    for k in range(0, M, 96):
        T.vband(panel, k, 2, 1.0)
    bolts = np.zeros((M, M), np.float32)
    for j in range(12, M, 84):
        for i in range(14, M, 24):
            T.rect(bolts, i, j, 3, 3, 1.0)
    grain = T.fbm(M, 90, 3, r)
    h = grain * 0.22 + bolts * 1.6 - panel * 1.0
    lit = T.shade(h, 2.2, ambient=0.68, diffuse=0.62)
    base = np.zeros((M, M, 3), np.float32)
    base[...] = np.asarray((150, 134, 96), np.float32)          # sand
    base = base * (1 - m1[..., None]) + np.asarray((108, 104, 74), np.float32) * m1[..., None]
    base = base * (1 - m2[..., None]) + np.asarray((92, 78, 58), np.float32) * m2[..., None]
    base = T.tint(base, (66, 62, 54), panel * 0.55)
    base = T.tint(base, (168, 158, 132), bolts * 0.5)
    dust = np.clip((T.fbm(M, 6, 4, r) - 0.42) * 2.2, 0, 1)
    base = T.tint(base, (186, 168, 132), dust * 0.35)
    scratch = np.clip((T.ridged(M, 40, 3, r) - 0.90) * 12.0, 0, 1)
    base = T.tint(base, (140, 136, 128), scratch * 0.45)
    soot = np.clip((T.fbm(M, 12, 3, r) - 0.74) * 5.0, 0, 1)
    base = T.tint(base, (52, 48, 44), soot * 0.35)
    T.save_rgb(base * lit[..., None], os.path.join(OUT, 'material_camo.png'))


def track():
    """Tank tread: repeating links + grousers + caked mud."""
    r = np.random.default_rng(20240917)
    h = np.zeros((M, M), np.float32)
    link = np.zeros((M, M), np.float32)
    pin = np.zeros((M, M), np.float32)
    pitch = 32
    for i in range(0, M, pitch):
        T.rect(link, i + 3, 0, pitch - 8, M, 1.0)
        T.rect(pin, i + pitch - 5, 0, 4, M, 1.0)
    grouser = np.zeros((M, M), np.float32)
    for i in range(0, M, pitch):
        T.rect(grouser, i + int(pitch * 0.42), 0, 5, M, 1.0)
    # transverse pad ribs so the belt does not read as corrugated sheet
    rib = np.zeros((M, M), np.float32)
    for j in range(0, M, 26):
        T.hband(rib, j, 3, 1.0)
    edge = np.zeros((M, M), np.float32)
    T.hband(edge, 0, 10, 1.0)
    T.hband(edge, M - 10, 10, 1.0)
    grain = T.fbm(M, 70, 3, r)
    h = link * 1.6 + grouser * 1.4 + rib * 0.9 + edge * 0.7 - pin * 1.2 + grain * 0.25
    lit = T.shade(h, 2.6, ambient=0.54, diffuse=0.82)
    occ = T.ao(h, 4, 0.9)
    base = T.ramp(grain, [(0.0, (52, 52, 52)), (0.5, (70, 70, 70)), (1.0, (92, 92, 90))])
    base = T.tint(base, (108, 108, 106), grouser * 0.4)
    base = T.tint(base, (36, 36, 36), pin * 0.6)
    base = T.tint(base, (96, 96, 94), rib * 0.35)
    base = T.tint(base, (58, 58, 58), edge * 0.4)
    yy, xx = np.meshgrid(np.arange(M), np.arange(M), indexing='ij')
    wheel = np.clip(1.0 - np.abs(yy - M * 0.5) / (M * 0.42), 0, 1) ** 2
    mud = np.clip((T.fbm(M, 10, 4, r) - 0.40) * 2.6, 0, 1) * (0.45 + 0.55 * (1.0 - wheel))
    base = T.tint(base, (122, 100, 66), mud * 0.68)
    shine = np.clip((T.fbm(M, 55, 2, r) - 0.68) * 5.0, 0, 1) * link
    base = T.tint(base, (150, 150, 148), shine * 0.4)
    T.save_rgb(base * (lit * occ)[..., None], os.path.join(OUT, 'material_track.png'))


def burnt():
    """Charred / rusted skin for wrecks."""
    r = np.random.default_rng(20240918)
    soot = T.fbm(M, 6, 5, r)
    peel = np.clip((T.fbm(M, 14, 4, r) - 0.55) * 3.2, 0, 1)
    blister = T.specks(M, 2200, r, 0.8, 2.6)
    plate = np.zeros((M, M), np.float32)
    for k in range(0, M, 58):
        T.hband(plate, k, 2, 1.0)
    for k in range(0, M, 72):
        T.vband(plate, k, 2, 1.0)
    h = soot * 0.5 + blister * 0.8 + peel * 0.5 - plate * 1.0
    lit = T.shade(h, 2.4, ambient=0.50, diffuse=0.72)
    occ = T.ao(h, 5, 0.9)
    base = T.ramp(soot, [(0.0, (22, 20, 19)), (0.4, (40, 36, 33)),
                         (0.7, (62, 54, 48)), (1.0, (86, 74, 64))])
    base = T.tint(base, (122, 62, 32), peel * 0.55)
    base = T.tint(base, (150, 88, 44), blister * 0.35)
    base = T.tint(base, (16, 15, 14), plate * 0.6)
    ash = np.clip((T.fbm(M, 30, 3, r) - 0.70) * 5.0, 0, 1)
    base = T.tint(base, (128, 124, 118), ash * 0.3)
    T.save_rgb(base * (lit * occ)[..., None], os.path.join(OUT, 'material_rust.png'))


if __name__ == '__main__':
    desert(); print('terrain_desert')
    naval(); print('terrain_naval')
    urban(); print('terrain_urban')
    special(); print('terrain_special')
    wall(); print('material_wall')
    roof(); print('material_roof')
    concrete(); print('material_concrete')
    metal(); print('material_metal')
    deck(); print('material_deck')
    camo(); print('material_camo')
    track(); print('material_track')
    burnt(); print('material_rust')
