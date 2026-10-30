#!/usr/bin/env python3
"""Renders vitalyn-body.bin to a PNG to check the shape and clothing without a Mac.

Usage: python3 tools/avatar/preview.py out.png [gender age muscle weight] ...  (values 0-1, MakeHuman scale)
"""
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw

COLORS = {"skin": (226, 186, 156), "shirt": (246, 246, 244), "leggings": (34, 36, 40), "shoes": (40, 44, 52), "sole": (236, 236, 232),
          "hair": (74, 52, 38), "longhair": (74, 52, 38), "brows": (70, 50, 36), "eyes": (240, 240, 236), "iris": (70, 90, 120)}


def load(path):
    b = open(path, "rb").read()
    assert b[:4] == b"VBDY"
    o = 8
    (n,) = struct.unpack_from("<I", b, o); o += 4
    base = np.frombuffer(b, "<f4", n * 3, o).reshape(n, 3).astype(float); o += n * 12
    (pc,) = struct.unpack_from("<I", b, o); o += 4
    parts = {}
    for _ in range(pc):
        ln = b[o]; o += 1
        name = b[o:o + ln].decode(); o += ln
        (c,) = struct.unpack_from("<I", b, o); o += 4
        parts[name] = np.frombuffer(b, "<u2", c, o).reshape(-1, 3).astype(int); o += c * 2
    (tc,) = struct.unpack_from("<I", b, o); o += 4
    targets = {}
    for _ in range(tc):
        ln = b[o]; o += 1
        name = b[o:o + ln].decode(); o += ln
        scale, c = struct.unpack_from("<fI", b, o); o += 8
        rec = np.frombuffer(b, [("v", "<u2"), ("d", "<i2", 3)], c, o); o += c * 8
        targets[name] = (rec["v"].astype(int), rec["d"].astype(float) * scale)
    return base, parts, targets


def weights(gender, age, muscle, weight):
    g = {"male": gender, "female": 1 - gender}
    a = {"old": max(0, age * 2 - 1)}; a["young"] = 1 - a["old"]
    m = {"maxmuscle": max(0, muscle * 2 - 1), "minmuscle": max(0, 1 - muscle * 2)}; m["averagemuscle"] = 1 - m["maxmuscle"] - m["minmuscle"]
    w = {"maxweight": max(0, weight * 2 - 1), "minweight": max(0, 1 - weight * 2)}; w["averageweight"] = 1 - w["maxweight"] - w["minweight"]
    out = {}
    for gk, gv in g.items():
        for ak, av in a.items():
            out[f"race-{gk}-{ak}"] = gv * av
            for mk, mv in m.items():
                for wk, wv in w.items():
                    out[f"universal-{gk}-{ak}-{mk}-{wk}"] = gv * av * mv * wv
    return out


def shape(base, targets, params):
    v = base.copy()
    for name, wt in weights(*params).items():
        if wt > 0 and name in targets:
            idx, d = targets[name]
            np.add.at(v, idx, d * wt)
    return v


def volume(v, parts):
    tris = np.concatenate([parts[p] for p in ("skin", "shirt", "leggings", "shoes", "sole", "hair", "brows")])
    a, b, c = v[tris[:, 0]], v[tris[:, 1]], v[tris[:, 2]]
    center = v.mean(0)
    return abs(np.einsum("ij,ij->i", a - center, np.cross(b - center, c - center)).sum() / 6)


def render(v, parts, size=(360, 720), yaw=0.0):
    cy, sy = np.cos(yaw), np.sin(yaw)
    r = v @ np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]).T
    lo, hi = r.min(0), r.max(0)
    s = (size[1] - 40) / (hi[1] - lo[1])
    img = Image.new("RGB", size, (243, 244, 242))
    d = ImageDraw.Draw(img)
    light = np.array([0.3, 0.5, 0.8]); light /= np.linalg.norm(light)
    polys = []
    for name, tris in parts.items():
        p = r[tris]
        n = np.cross(p[:, 1] - p[:, 0], p[:, 2] - p[:, 0])
        n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-9
        for t, nn, z in zip(p, n, p[:, :, 2].mean(1)):
            if nn[2] <= 0:
                continue
            shade = 0.35 + 0.65 * max(0, nn @ light)
            col = tuple(int(c * shade) for c in COLORS[name])
            pts = [(size[0] / 2 + x * s, size[1] - 20 - (y - lo[1]) * s) for x, y in t[:, :2]]
            polys.append((z, pts, col))
    for _, pts, col in sorted(polys, key=lambda q: q[0]):
        d.polygon(pts, fill=col)
    return img


if __name__ == "__main__":
    base, parts, targets = load(sys.argv[2] if len(sys.argv) > 6 else "Packages/VitalynCore/Sources/VitalynCore/Body/Resources/vitalyn-body.bin")
    params = tuple(float(x) for x in sys.argv[2:6]) if len(sys.argv) >= 6 else (1, 0.5, 0.5, 0.5)
    v = shape(base, targets, params)
    print("height dm", v[:, 1].max() - v[:, 1].min(), "volume L", volume(v, parts))
    views = [render(v, parts, yaw=y) for y in (0, 0.6, 1.57, 3.14)]
    out = Image.new("RGB", (360 * 4, 720))
    for i, im in enumerate(views):
        out.paste(im, (360 * i, 0))
    out.save(sys.argv[1])
