# -*- coding: utf-8 -*-
"""Tileable procedural texture helpers for Pahpad Shahed. No network assets."""
import numpy as np
from PIL import Image


def value_noise(size, period, r):
    period = max(2, int(period))
    g = r.random((period, period)).astype(np.float32)
    x = np.linspace(0.0, period, size, endpoint=False).astype(np.float32)
    xi = np.floor(x).astype(np.int32)
    xf = x - xi
    xs = xf * xf * (3.0 - 2.0 * xf)
    i0 = xi % period
    i1 = (xi + 1) % period
    a = g[:, i0] * (1.0 - xs) + g[:, i1] * xs
    b = a[i0, :] * (1.0 - xs)[:, None] + a[i1, :] * xs[:, None]
    return b.astype(np.float32)


def fbm(size, period, octaves, r, gain=0.5, lac=2.0):
    total = np.zeros((size, size), np.float32)
    amp, norm, p = 1.0, 0.0, float(period)
    for _ in range(octaves):
        total += amp * value_noise(size, p, r)
        norm += amp
        amp *= gain
        p *= lac
        if p > size:
            p = size
    return total / max(norm, 1e-6)


def ridged(size, period, octaves, r):
    n = fbm(size, period, octaves, r)
    return 1.0 - np.abs(n * 2.0 - 1.0)


def warp(field, dx, dy):
    size = field.shape[0]
    yy, xx = np.meshgrid(np.arange(size), np.arange(size), indexing='ij')
    sx = np.mod(np.round(xx + dx), size).astype(np.int32)
    sy = np.mod(np.round(yy + dy), size).astype(np.int32)
    return field[sy, sx]


def grad(h):
    gx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * 0.5
    gy = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * 0.5
    return gx, gy


def shade(h, strength=1.0, light=(-0.55, 0.5, 0.72), ambient=0.55, diffuse=0.78):
    gx, gy = grad(h)
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(h)
    inv = 1.0 / np.sqrt(nx * nx + ny * ny + nz * nz)
    lx, ly, lz = light
    ln = (lx * lx + ly * ly + lz * lz) ** 0.5
    d = ((nx * lx + ny * ly + nz * lz) * inv / ln).clip(0.0, 1.0)
    return (ambient + diffuse * d).astype(np.float32)


def blur_wrap(h, radius=4):
    k = np.ones(2 * radius + 1, np.float32) / (2 * radius + 1)
    out = h
    pad = np.concatenate([out[-radius:], out, out[:radius]], axis=0)
    out = np.apply_along_axis(lambda m: np.convolve(m, k, mode='valid'), 0, pad)
    pad = np.concatenate([out[:, -radius:], out, out[:, :radius]], axis=1)
    out = np.apply_along_axis(lambda m: np.convolve(m, k, mode='valid'), 1, pad)
    return out.astype(np.float32)


def ao(h, radius=6, strength=0.6):
    return (0.72 + strength * (h - blur_wrap(h, radius))).clip(0.4, 1.25).astype(np.float32)


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


def specks(size, count, r, rmin=0.6, rmax=1.8):
    f = np.zeros((size, size), np.float32)
    yy, xx = np.meshgrid(np.arange(size), np.arange(size), indexing='ij')
    cx = r.integers(0, size, count)
    cy = r.integers(0, size, count)
    rad = r.uniform(rmin, rmax, count)
    for i in range(count):
        dx = np.minimum(np.abs(xx - cx[i]), size - np.abs(xx - cx[i]))
        dy = np.minimum(np.abs(yy - cy[i]), size - np.abs(yy - cy[i]))
        d = np.sqrt(dx * dx + dy * dy)
        f = np.maximum(f, np.clip(1.0 - d / rad[i], 0.0, 1.0))
    return f


def streaks(size, count, r, length=40, width=1.6):
    f = np.zeros((size, size), np.float32)
    yy, xx = np.meshgrid(np.arange(size), np.arange(size), indexing='ij')
    for _ in range(count):
        cx = r.integers(0, size)
        cy = r.integers(0, size)
        ln = max(4.0, r.uniform(0.4, 1.0) * length)
        w = max(0.7, r.uniform(0.6, 1.0) * width)
        dx = np.minimum(np.abs(xx - cx), size - np.abs(xx - cx))
        dy = (yy - cy) % size
        m = np.clip(1.0 - dx / w, 0.0, 1.0) * np.clip(1.0 - dy / ln, 0.0, 1.0)
        f = np.maximum(f, m)
    return f


def hband(f, y, thick, value):
    size = f.shape[0]
    for k in range(int(max(1, thick))):
        yy = (int(y) + k) % size
        f[yy, :] = np.maximum(f[yy, :], value)


def vband(f, x, thick, value):
    size = f.shape[0]
    for k in range(int(max(1, thick))):
        xx = (int(x) + k) % size
        f[:, xx] = np.maximum(f[:, xx], value)


def rect(f, x0, y0, w, h, value):
    size = f.shape[0]
    ys = [(int(y0) + j) % size for j in range(int(h))]
    xs = [(int(x0) + i) % size for i in range(int(w))]
    for y in ys:
        f[y, xs] = np.maximum(f[y, xs], value)


def save_rgb(rgb, path):
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB').save(path, optimize=True)


def save_rgba(rgba, path):
    Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), 'RGBA').save(path, optimize=True)
