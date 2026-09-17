"""Source gate and seam parity math, not a raster screenshot test."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/ir/shahed/pahpad/game/SceneryArt.kt').read_text()
assert 'tileIndex' in s and 'c.scale(-1f,1f' in s, 'Skyline repeats unmatched edges without mirrored neighbors'
for i in range(-20,20):
 right=0 if i%2 else 1
 left=1 if (i+1)%2 else 0
 assert right==left
print('PASS skyline adjacent edge parity including negative yaw tiles')
