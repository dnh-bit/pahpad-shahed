# -*- coding: utf-8 -*-
"""Software preview of the Kotlin Rig/Scene3D painter, to eyeball the meshes."""
import math, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, '/home/user/preview')
import kt2py

SUN = (-0.44, 0.36, 0.82)


def clamp(v, a, b): return max(a, min(b, v))
def shade(c, f):
    f = clamp(f, 0, 4)
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    return (min(255, int(r * f)) << 16) | (min(255, int(g * f)) << 8) | min(255, int(b * f))
def mix(a, b, t):
    t = clamp(t, 0, 1)
    out = 0
    for sh in (16, 8, 0):
        ca, cb = (a >> sh) & 255, (b >> sh) & 255
        out |= int(ca + (cb - ca) * t) << sh
    return out
def withAlpha(c, a): return c


def light(nx, ny, nz):
    return 0.66 + 0.46 * max(0.0, nx * SUN[0] + ny * SUN[1] + nz * SUN[2])


class Rig:
    def __init__(self):
        self.faces = []
        self.ox = self.oy = self.oz = 0.0
        self.sy, self.cy = 0.0, 1.0
        self.scale = 1.0
        self.refX = self.refY = self.refZ = 0.0

    def place(self, x, y, z, yaw, unitScale=1.0):
        self.ox, self.oy, self.oz = x, y, z
        self.sy, self.cy = math.sin(yaw), math.cos(yaw)
        self.scale = unitScale if unitScale > 1e-4 else 1.0
        self.refX = self.refY = self.refZ = 0.0

    def ref(self, x, y, z):
        self.refX, self.refY, self.refZ = x, y, z

    def w(self, lx, ly, lz):
        lx *= self.scale; ly *= self.scale; lz *= self.scale
        return (self.ox + lx * self.cy - lz * self.sy, self.oy + ly, self.oz + lx * self.sy + lz * self.cy)

    def face(self, ax, ay, az, bx, by, bz, cx, cy2, cz, dx, dy, dz, color, texture=None):
        pa, pb, pc, pd = self.w(ax, ay, az), self.w(bx, by, bz), self.w(cx, cy2, cz), self.w(dx, dy, dz)
        u = [pb[i] - pa[i] for i in range(3)]
        v = [pd[i] - pa[i] for i in range(3)]
        n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
        ln = max(1e-4, math.sqrt(sum(k * k for k in n)))
        n = [k / ln for k in n]
        mx = (ax + bx + cx + dx) * 0.25 - self.refX
        my = (ay + by + cy2 + dy) * 0.25 - self.refY
        mz = (az + bz + cz + dz) * 0.25 - self.refZ
        wx = mx * self.cy - mz * self.sy
        wz = mx * self.sy + mz * self.cy
        if n[0] * wx + n[1] * my + n[2] * wz < 0:
            n = [-k for k in n]
        self.faces.append(([pa, pb, pc, pd], shade(color, light(*n))))

    def tri(self, ax, ay, az, bx, by, bz, cx, cy2, cz, color):
        pa, pb, pc = self.w(ax, ay, az), self.w(bx, by, bz), self.w(cx, cy2, cz)
        u = [pb[i] - pa[i] for i in range(3)]
        v = [pc[i] - pa[i] for i in range(3)]
        n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
        ln = max(1e-4, math.sqrt(sum(k * k for k in n)))
        n = [k / ln for k in n]
        if n[1] < 0: n = [-k for k in n]
        self.faces.append(([pa, pb, pc], shade(color, light(*n))))

    def _hexa(self, p, color, side, top, bottom=True):
        def v(i, k): return p[i * 3 + k]
        if bottom:
            self.face(v(3,0),v(3,1),v(3,2), v(2,0),v(2,1),v(2,2), v(1,0),v(1,1),v(1,2), v(0,0),v(0,1),v(0,2), shade(color,0.72))
        self.face(v(4,0),v(4,1),v(4,2), v(5,0),v(5,1),v(5,2), v(6,0),v(6,1),v(6,2), v(7,0),v(7,1),v(7,2), color)
        for i in range(4):
            j = (i + 1) % 4
            self.face(v(i,0),v(i,1),v(i,2), v(j,0),v(j,1),v(j,2), v(j+4,0),v(j+4,1),v(j+4,2), v(i+4,0),v(i+4,1),v(i+4,2), color)

    def box(self, cx, baseY, cz, sx, sy2, sz, color, side=None, top=None, bottom=True):
        hx, hz, ty = sx * .5, sz * .5, baseY + sy2
        self.ref(cx, baseY + sy2 * .5, cz)
        p = [cx-hx,baseY,cz-hz, cx+hx,baseY,cz-hz, cx+hx,baseY,cz+hz, cx-hx,baseY,cz+hz,
             cx-hx,ty,cz-hz, cx+hx,ty,cz-hz, cx+hx,ty,cz+hz, cx-hx,ty,cz+hz]
        self._hexa(p, color, side, top, bottom)

    def taper(self, cx, baseY, cz, hx0, hz0, hx1, hz1, height, color, side=None, top=None, shiftX=0.0, shiftZ=0.0, bottom=True):
        ty = baseY + height
        self.ref(cx, baseY + height * .5, cz)
        p = [cx-hx0,baseY,cz-hz0, cx+hx0,baseY,cz-hz0, cx+hx0,baseY,cz+hz0, cx-hx0,baseY,cz+hz0,
             cx-hx1+shiftX,ty,cz-hz1+shiftZ, cx+hx1+shiftX,ty,cz-hz1+shiftZ,
             cx+hx1+shiftX,ty,cz+hz1+shiftZ, cx-hx1+shiftX,ty,cz+hz1+shiftZ]
        self._hexa(p, color, side, top, bottom)

    def hull(self, z0, z1, halfW0, halfW1, keelY, deckY, keelHalf0, keelHalf1, color, side, top):
        self.ref(0, (keelY + deckY) * .5, (z0 + z1) * .5)
        p = [-keelHalf0,keelY,z0, keelHalf0,keelY,z0, keelHalf1,keelY,z1, -keelHalf1,keelY,z1,
             -halfW0,deckY,z0, halfW0,deckY,z0, halfW1,deckY,z1, -halfW1,deckY,z1]
        self._hexa(p, color, side, top, True)

    def wedge(self, cx, baseY, cz, sx, sz, frontY, backY, color, side=None, top=None):
        hx, hz = sx * .5, sz * .5
        self.ref(cx, baseY + (frontY + backY) * .25, cz)
        p = [cx-hx,baseY,cz-hz, cx+hx,baseY,cz-hz, cx+hx,baseY,cz+hz, cx-hx,baseY,cz+hz,
             cx-hx,baseY+frontY,cz-hz, cx+hx,baseY+frontY,cz-hz,
             cx+hx,baseY+backY,cz+hz, cx-hx,baseY+backY,cz+hz]
        self._hexa(p, color, side, top, True)

    def tube(self, x0, y0, z0, x1, y1, z1, radius, sides, color, caps=True, texture=None):
        ax, ay, az = x1 - x0, y1 - y0, z1 - z0
        ln = math.sqrt(ax*ax + ay*ay + az*az)
        if ln < 1e-4: return
        ax, ay, az = ax/ln, ay/ln, az/ln
        if abs(ay) < 0.9: ux, uy, uz = -az, 0.0, ax
        else: ux, uy, uz = 1.0, 0.0, 0.0
        ui = 1.0 / max(1e-4, math.sqrt(ux*ux + uy*uy + uz*uz))
        ux, uy, uz = ux*ui, uy*ui, uz*ui
        vx, vy, vz = ay*uz - az*uy, az*ux - ax*uz, ax*uy - ay*ux
        self.ref((x0+x1)*.5, (y0+y1)*.5, (z0+z1)*.5)
        n = max(3, min(16, int(sides)))
        pa = pb = None
        for i in range(n + 1):
            a = i * 2 * math.pi / n
            cc, ss = math.cos(a) * radius, math.sin(a) * radius
            ex, ey, ez = ux*cc + vx*ss, uy*cc + vy*ss, uz*cc + vz*ss
            na = (x0+ex, y0+ey, z0+ez); nb = (x1+ex, y1+ey, z1+ez)
            if i:
                self.face(pa[0],pa[1],pa[2], na[0],na[1],na[2], nb[0],nb[1],nb[2], pb[0],pb[1],pb[2], color)
                if caps:
                    self.tri(x0,y0,z0, pa[0],pa[1],pa[2], na[0],na[1],na[2], shade(color,.88))
                    self.tri(x1,y1,z1, nb[0],nb[1],nb[2], pb[0],pb[1],pb[2], shade(color,1.06))
            pa, pb = na, nb

    def disc(self, cx, cy2, cz, nx, ny, nz, radius, sides, color):
        if abs(ny) < 0.9: ux, uy, uz = -nz, 0.0, nx
        else: ux, uy, uz = 1.0, 0.0, 0.0
        ui = 1.0 / max(1e-4, math.sqrt(ux*ux + uy*uy + uz*uz))
        ux, uy, uz = ux*ui, uy*ui, uz*ui
        vx, vy, vz = ny*uz - nz*uy, nz*ux - nx*uz, nx*uy - ny*ux
        self.ref(cx - nx, cy2 - ny, cz - nz)
        n = max(3, min(16, int(sides)))
        prev = None
        for i in range(n + 1):
            a = i * 2 * math.pi / n
            cc, ss = math.cos(a) * radius, math.sin(a) * radius
            e = (cx + ux*cc + vx*ss, cy2 + uy*cc + vy*ss, cz + uz*cc + vz*ss)
            if i: self.tri(cx, cy2, cz, prev[0], prev[1], prev[2], e[0], e[1], e[2], color)
            prev = e
        return


def draw(faces, W=900, H=560, eye=(0, 12, -40), look=(0, 3, 0), fov=52, bg=(150, 162, 176)):
    ex, ey, ez = eye
    lx, ly, lz = look
    fx, fy, fz = lx - ex, ly - ey, lz - ez
    yaw = math.atan2(fx, fz)
    pitch = math.atan2(fy, math.sqrt(fx*fx + fz*fz))
    sy, cy = math.sin(yaw), math.cos(yaw)
    sp, cp = math.sin(pitch), math.cos(pitch)
    focal = H / 2 / math.tan(math.radians(fov / 2))
    im = Image.new('RGB', (W, H), bg)
    d = ImageDraw.Draw(im)
    out = []
    for pts, col in faces:
        cam = []
        for (px, py, pz) in pts:
            dx, dy, dz = px - ex, py - ey, pz - ez
            fwd = dx*sy + dz*cy
            cam.append((dx*cy - dz*sy, dy*cp - fwd*sp, dy*sp + fwd*cp))
        if any(p[2] <= 0.6 for p in cam): continue
        scr = [(W/2 + focal*p[0]/p[2], H/2 - focal*p[1]/p[2]) for p in cam]
        depth = sum(p[2] for p in cam) / len(cam)
        out.append((depth, scr, col))
    out.sort(key=lambda t: -t[0])
    for depth, scr, col in out:
        d.polygon(scr, fill=((col >> 16) & 255, (col >> 8) & 255, col & 255))
    return im
