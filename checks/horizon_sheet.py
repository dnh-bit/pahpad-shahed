#!/usr/bin/env python3
"""Horizon-strip crop regression for SceneryArt's env_* sheets.

SceneryArt crops each `env_<name>.png` to a 768x180 sky band that sits above the
renderer's own horizon line. One hardcoded 40% of the sheet height is correct
for at most one of the four photos: the naval sheet's horizon is at 24% of the
image and urban's at 26%, so the old 40% cut showed bare sky over the sea and
dragged the photographed town and mountains over the renderer's own ground.

skyFraction() therefore carries a per-environment constant. This check locks
every constant against its own shipped PNG, so the crop cannot drift back into
the water or the town.

Run:
    python3 checks/horizon_sheet.py
Requires numpy and Pillow.
"""
from pathlib import Path
import re
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
GFX = ROOT / 'app/src/main/assets/gfx'
SCENERY = ROOT / 'app/src/main/java/ir/shahed/pahpad/game/SceneryArt.kt'

# sheet name -> Env enum, in skyFraction()'s order.
SHEETS = {
    'env_naval': 'NAVAL',
    'env_urban': 'URBAN',
    'env_special': 'SPECIAL',
    'env_desert': 'DESERT',
}
# Shipped sky fractions, mirroring SceneryArt.skyFraction(). DESERT is the
# `else` branch, so it has no explicit case to parse.
EXPECTED = {
    'NAVAL': 0.24,
    'URBAN': 0.25,
    'SPECIAL': 0.67,
    'DESERT': 0.55,
}
# Tolerance between the shipping constant and the measured horizon, in rows.
ROW_TOLERANCE = 10
# The coherent detector must find a real sustained break, not cloud texture.
MIN_AGREEING_COLUMNS = 0.30


def read_sheet(name):
    from PIL import Image
    return np.asarray(Image.open(GFX / f'{name}.png').convert('RGB'))


def coherent_step_row(px, threshold=6.0):
    """Row where the largest share of columns step in the same direction.

    Sky-to-ground is a broad, sustained luminance break, unlike a cloud edge,
    which reverses direction across the image. The strongest coherent step in the
    middle of the sheet is the horizon.
    """
    h, w, _ = px.shape
    lum = px.astype(np.float32).mean(axis=2)
    delta = lum[1:] - lum[:-1]
    score = np.maximum((delta > threshold).sum(axis=1),
                       (delta < -threshold).sum(axis=1)).astype(np.float32) / w
    lo, hi = int(h * 0.12), int(h * 0.75)
    return lo + int(score[lo:hi].argmax()), float(score[lo:hi].max())


def production_fractions():
    """Parse skyFraction() out of SceneryArt.kt so the test follows the source."""
    text = SCENERY.read_text()
    m = re.search(
        r'fun skyFraction\(env:Env\):Float\s*=\s*when\(env\)\{(.+?)\n\s*\}',
        text, re.S)
    if not m:
        raise AssertionError(
            'SceneryArt.kt no longer exposes skyFraction(env):Float = when(env){...}')
    body = m.group(1)
    found = {env: float(val)
             for env, val in re.findall(r'Env\.([A-Z]+)\s*->\s*([0-9.]+)f', body)}
    # The final `else ->` branch is DESERT, the only env left.
    else_branch = re.search(r'else\s*->\s*([0-9.]+)f', body)
    if else_branch and 'DESERT' not in found:
        found['DESERT'] = float(else_branch.group(1))
    return found


def main() -> int:
    try:
        import PIL  # noqa: F401
    except ImportError:
        print('SKIP: Pillow unavailable; cannot read the shipped sheets')
        return 0

    failures = 0
    fractions = production_fractions()
    print(f'SceneryArt.skyFraction -> {fractions}\n')

    for sheet, env in SHEETS.items():
        path = GFX / f'{sheet}.png'
        if not path.is_file():
            print(f'FAIL {sheet}: sheet missing at {path}')
            failures += 1
            continue
        px = read_sheet(sheet)
        h = px.shape[0]
        row, share = coherent_step_row(px)

        fraction = fractions.get(env)
        if fraction is None:
            print(f'FAIL {sheet}: skyFraction has no {env} case; it would fall back '
                  f'to an unmeasured crop')
            failures += 1
            continue
        if abs(fraction - EXPECTED[env]) > 0.005:
            print(f'FAIL {sheet}: skyFraction {fraction:.2f} drifted from the reviewed '
                  f'value {EXPECTED[env]:.2f}')
            failures += 1
        if share < MIN_AGREEING_COLUMNS:
            print(f'FAIL {sheet}: no coherent horizon found ({share:.0%} of columns '
                  f'agree, need {MIN_AGREEING_COLUMNS:.0%}); re-measure before trusting '
                  f'the constant')
            failures += 1

        crop_row = int(round(h * fraction))
        drift = abs(crop_row - row)
        # Cropping even slightly past the horizon would show photographed water
        # or town above the renderer's own ground plane, so require sky-only.
        if crop_row > row:
            print(f'FAIL {sheet}: crop row {crop_row} is BELOW the measured horizon '
                  f'{row}; the strip would pull ground/water into the sky band')
            failures += 1
        elif drift > ROW_TOLERANCE:
            print(f'FAIL {sheet}: crop row {crop_row} is {drift} rows from the '
                  f'horizon {row} (max {ROW_TOLERANCE}); re-measure skyFraction')
            failures += 1
        else:
            print(f'PASS {sheet}: sky={fraction:.2f} crop_row={crop_row} '
                  f'horizon={row} ({row / h:.0%}) drift={drift} '
                  f'agreeing={share:.0%}')

    if failures:
        print(f'\n{failures} horizon-strip check(s) failed')
        return 1
    print('\nALL HORIZON-STRIP CHECKS PASS')
    return 0


if __name__ == '__main__':
    sys.exit(main())
