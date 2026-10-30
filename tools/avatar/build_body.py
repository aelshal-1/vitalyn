#!/usr/bin/env python3
"""Packs the MakeHuman (CC0) base body and its shape targets into VitalynCore (Body/Resources/vitalyn-body.bin).

Usage: python3 tools/avatar/build_body.py <makehuman/data dir> [out.bin]

The app shapes this body on the phone from height, weight, body fat, sex and age (see BodyShape.swift).
Clothing is painted by region (shirt, leggings, shoes) so no extra assets are needed.

Binary layout (little endian):
  "VBDY" u32 version
  u32 vertexCount, then vertexCount * 3 f32 base positions (decimetres, y up, +z front)
  u32 partCount, per part: u8 nameLen, name, u32 indexCount, indexCount * u16 triangle indices
  u32 targetCount, per target: u8 nameLen, name, f32 scale, u32 count, count * (u16 vertex, 3 * i16 delta/scale)
"""
import collections
import itertools
import os
import struct
import sys

import numpy as np

PARTS = ["skin", "shirt", "leggings", "shoes", "sole", "hair", "longhair", "brows", "eyes", "iris"]


def load_obj(path):
    verts, groups = [], collections.defaultdict(list)
    group = None
    for line in open(path):
        if line.startswith("v "):
            verts.append([float(v) for v in line.split()[1:4]])
        elif line.startswith("g "):
            group = line.split()[1]
        elif line.startswith("f "):
            groups[group].append([int(p.split("/")[0]) - 1 for p in line.split()[1:]])
    return np.array(verts), groups


def load_target(path):
    deltas = {}
    for line in open(path):
        if line.startswith("#") or not line.strip():
            continue
        parts = line.split()
        deltas[int(parts[0])] = np.array([float(v) for v in parts[1:4]])
    return deltas


def seg_distance(points, a, b):
    ab = b - a
    t = np.clip(((points - a) @ ab) / (ab @ ab), 0, 1)
    return np.linalg.norm(points - (a + t[:, None] * ab), axis=1), t


def main():
    data = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "../../Packages/VitalynCore/Sources/VitalynCore/Body/Resources/vitalyn-body.bin")
    base, groups = load_obj(os.path.join(data, "3dobjs/base.obj"))
    joint = {name[6:]: base[sorted({i for f in faces for i in f})].mean(0) for name, faces in groups.items() if name.startswith("joint-")}

    # Bones on the left side; right side handled by mirroring x.
    bones = {
        "torso": (joint["pelvis"], joint["neck"]),
        "head": (joint["neck"], joint["head"] + np.array([0, 1.0, 0])),
        "upperarm": (joint["l-shoulder"], joint["l-elbow"]),
        "forearm": (joint["l-elbow"], joint["l-hand"]),
        "thigh": (joint["l-upper-leg"], joint["l-knee"]),
        "shin": (joint["l-knee"], joint["l-ankle"]),
        "foot": (joint["l-ankle"], joint["l-foot-2"]),
    }

    def classify(points):
        mirrored = points.copy()
        mirrored[:, 0] = np.abs(mirrored[:, 0])
        names = list(bones)
        dist = np.stack([seg_distance(mirrored, *bones[n])[0] for n in names], 1)
        # The torso is wide: measure it from its axis but discount its radius.
        dist[:, names.index("torso")] -= 1.0
        dist[:, names.index("thigh")] -= 0.35
        nearest = np.array(names)[dist.argmin(1)]
        ts = {n: seg_distance(mirrored, *bones[n])[1] for n in names}
        return nearest, ts

    body_faces = groups["body"]
    eye_faces = groups["helper-l-eye"] + groups["helper-r-eye"]

    labels = collections.defaultdict(list)
    quads = np.array(body_faces)
    centroids = base[quads].mean(1)
    normals = np.cross(base[quads[:, 2]] - base[quads[:, 0]], base[quads[:, 3]] - base[quads[:, 1]])
    nearest, ts = classify(centroids)
    y = centroids[:, 1]
    shirt_bottom = joint["pelvis"][1] + 0.55
    neck_y = joint["neck"][1]
    top = base[sorted({i for f in body_faces for i in f})][:, 1].max()
    skull = np.array([0.0, top - 1.1, joint["mouth"][2]])  # roughly the centre of the skull
    for i, face in enumerate(body_faces):
        bone, c = nearest[i], centroids[i]
        # Crew neckline: a U at the front, higher at the back.
        neckline = neck_y - 0.5 + 0.35 * min(1, (c[0] / 0.75) ** 2) + (0.3 if c[2] < 0 else 0)
        part = "skin"
        if y[i] > neck_y + 0.3:
            bone = "head"
        if bone in ("torso", "head") and shirt_bottom < y[i] < neckline:
            part = "shirt"
        elif bone == "upperarm" and ts["upperarm"][i] < 0.42:
            part = "shirt"
        elif bone in ("torso", "thigh") and y[i] <= shirt_bottom:
            part = "leggings"
        elif bone == "shin" and ts["shin"][i] < 0.88:
            part = "leggings"
        elif bone == "foot" or (bone == "shin" and ts["shin"][i] > 0.95):
            part = "sole" if c[1] < joint["ground"][1] + 0.18 else "shoes"
        elif bone == "head" and y[i] > neck_y + 0.3:
            # Hairline: a plane rising from the nape to the forehead, skipping the ears and face.
            rel = c - skull
            hairline = 0.55 - 0.7 * max(0.0, 0.6 - rel[2]) + 0.25 * max(0.0, abs(rel[0]) - 0.45)
            if rel[1] > hairline and not (abs(rel[0]) > 0.6 and -0.55 < rel[2] < 0.3 and rel[1] < 0.2):
                part = "hair"
        labels[part].append(face)

    # Eyebrows: small arcs above each eye, on the front of the face.
    eyes = {side: base[sorted({i for f in groups[f"helper-{side}-eye"] for i in f})] for side in ("l", "r")}
    for side, pts in eyes.items():
        center, radius = pts.mean(0), (pts.max(0) - pts.min(0)).max() / 2
        moved = []
        for face in labels["skin"]:
            c = base[face].mean(0)
            dx, dy = abs(c[0] - center[0]), c[1] - center[1]
            if c[2] > center[2] - 0.05 and dx < radius * 1.45 and radius * 1.2 < dy - dx * 0.18 < radius * 1.65:
                moved.append(face)
        for face in moved:
            labels["skin"].remove(face)
            labels["brows"].append(face)

    for side in ("l", "r"):
        pts = eyes[side]
        center = pts.mean(0)
        front = pts[:, 2].max()
        for face in groups[f"helper-{side}-eye"]:
            c = base[face].mean(0)
            labels["iris" if c[2] > front - 0.07 else "eyes"].append(face)

    # Shoulder-length hair from MakeHuman's hair helper (shown for women).
    for face in groups["helper-hair"]:
        rel = base[face].mean(0) - skull
        # Drop the part of the helmet that covers the face; keep the fringe line at the forehead.
        if rel[1] > neck_y + 0.1 - skull[1] and (rel[2] < 0.15 or rel[1] > 0.55):
            labels["longhair"].append(face)

    used = sorted({i for faces in labels.values() for f in faces for i in f})
    remap = {old: new for new, old in enumerate(used)}
    assert len(used) < 65536

    blob = bytearray(b"VBDY") + struct.pack("<I", 1)
    blob += struct.pack("<I", len(used)) + base[used].astype("<f4").tobytes()
    blob += struct.pack("<I", len(PARTS))
    for name in PARTS:
        tris = []
        for f in labels[name]:
            f = [remap[i] for i in f]
            tris += [f[0], f[1], f[2]] + ([f[0], f[2], f[3]] if len(f) == 4 else [])
        blob += struct.pack("<B", len(name)) + name.encode() + struct.pack("<I", len(tris)) + np.array(tris, "<u2").tobytes()
        print(f"part {name}: {len(tris) // 3} triangles")

    macro = os.path.join(data, "targets/macrodetails")
    targets = {}
    for gender, age, muscle, weight in itertools.product(("female", "male"), ("young", "old"), ("minmuscle", "averagemuscle", "maxmuscle"), ("minweight", "averageweight", "maxweight")):
        name = f"universal-{gender}-{age}-{muscle}-{weight}"
        deltas = load_target(os.path.join(macro, name + ".target"))
        if deltas:
            targets[name] = deltas
    # Ethnicity is fixed at MakeHuman's default (an even mix), so it is pre-blended.
    for gender, age in itertools.product(("female", "male"), ("young", "old")):
        mix = collections.defaultdict(lambda: np.zeros(3))
        for race in ("african", "asian", "caucasian"):
            for i, d in load_target(os.path.join(macro, f"{race}-{gender}-{age}.target")).items():
                mix[i] += d / 3
        targets[f"race-{gender}-{age}"] = dict(mix)

    blob += struct.pack("<I", len(targets))
    for name, deltas in targets.items():
        idx = [i for i in deltas if i in remap and np.abs(deltas[i]).max() > 1e-5]
        values = np.array([deltas[i] for i in idx])
        scale = float(np.abs(values).max()) / 32767
        q = np.round(values / scale).astype("<i2")
        blob += struct.pack("<B", len(name)) + name.encode() + struct.pack("<fI", scale, len(idx))
        rec = np.zeros(len(idx), dtype=[("v", "<u2"), ("d", "<i2", 3)])
        rec["v"] = [remap[i] for i in idx]
        rec["d"] = q
        blob += rec.tobytes()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "wb").write(blob)
    print(f"{len(used)} vertices, {len(targets)} targets, {len(blob) / 1e6:.2f} MB -> {out}")


if __name__ == "__main__":
    main()
