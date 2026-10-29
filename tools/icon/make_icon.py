# Generates the Vitalyn app icon (1024x1024, opaque): python3 tools/icon/make_icon.py Vitalyn/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png
import math, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 4096  # supersampled canvas
OUT = sys.argv[1] if len(sys.argv) > 1 else "icon.png"

def lerp(a, b, t): return tuple(int(a[i] + (b[i]-a[i])*t) for i in range(3))

# 1. Background: diagonal gradient deep emerald -> forest, with a soft light bloom top-left
top = (22, 160, 110)
bottom = (6, 52, 40)
import numpy as np
yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
def gradient(c0, c1, t):
    t = np.clip(t, 0, 1)[..., None]
    arr = np.array(c0, np.float32) * (1 - t) + np.array(c1, np.float32) * t
    return Image.fromarray(arr.astype(np.uint8), "RGB")
grad = gradient(top, bottom, (xx*0.35 + yy*0.65) / S)
bloom = Image.new("L", (S, S), 0)
ImageDraw.Draw(bloom).ellipse((-S*0.25, -S*0.3, S*0.75, S*0.6), fill=110)
bloom = bloom.filter(ImageFilter.GaussianBlur(S*0.12))
grad = Image.composite(Image.new("RGB", (S, S), (120, 230, 180)), grad, bloom)

# 2. Leaf: two arcs meeting at tips, built in a local frame then rotated 40 degrees
def leaf_points(cx, cy, length, width, angle_deg, n=200):
    pts_top, pts_bot = [], []
    for i in range(n+1):
        t = i / n
        x = (t - 0.5) * length
        # asymmetric leaf: fuller near the base
        w = width * math.sin(math.pi * t) * (1.0 - 0.25 * t)
        pts_top.append((x, -w/2))
        pts_bot.append((x, w/2))
    pts = pts_top + pts_bot[::-1]
    a = math.radians(angle_deg)
    return [(cx + px_*math.cos(a) - py*math.sin(a), cy + px_*math.sin(a) + py*math.cos(a)) for px_, py in pts]

angle = -45
cx, cy = S*0.5, S*0.5
L, W = S*0.70, S*0.42

# soft shadow under the leaf
shadow = Image.new("L", (S, S), 0)
ImageDraw.Draw(shadow).polygon(leaf_points(cx + S*0.02, cy + S*0.035, L, W, angle), fill=150)
shadow = shadow.filter(ImageFilter.GaussianBlur(S*0.03))
grad = Image.composite(Image.new("RGB", (S, S), (3, 30, 22)), grad, shadow)

# leaf body: white -> mint gradient
leaf_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(leaf_mask).polygon(leaf_points(cx, cy, L, W, angle), fill=255)
leaf_fill = gradient((255, 255, 255), (190, 240, 214), (xx*0.5 + yy*0.5) / S - 0.15)
grad = Image.composite(leaf_fill, grad, leaf_mask)

# 3. Heartbeat line along the leaf's midrib (clipped to the leaf), in deep emerald
a = math.radians(angle)
def to_world(u, v):
    return (cx + u*math.cos(a) - v*math.sin(a), cy + u*math.sin(a) + v*math.cos(a))
half = L * 0.5
# faint midrib along the leaf axis
rib = Image.new("L", (S, S), 0)
ImageDraw.Draw(rib).line([to_world(-half*0.95, 0), to_world(half*0.9, 0)], fill=255, width=int(S*0.010))
rib = ImageChops.multiply(rib, leaf_mask)
grad = Image.composite(Image.new("RGB", (S, S), (170, 222, 196)), grad, rib)

# horizontal ECG trace across the leaf (spikes stay vertical so it reads as a heartbeat)
pulse_rel = [(-0.40, 0), (-0.17, 0), (-0.13, -0.035), (-0.09, 0), (-0.05, 0), (-0.035, 0.035),
             (0.0, -0.17), (0.04, 0.11), (0.07, 0), (0.13, 0), (0.17, -0.05), (0.21, 0), (0.40, 0)]
pulse = [(cx + u*S, cy + v*S) for u, v in pulse_rel]
line = Image.new("L", (S, S), 0)
ld = ImageDraw.Draw(line)
stroke = int(S*0.034)
ld.line(pulse, fill=255, width=stroke, joint="curve")
for p in pulse[1:-1]:
    r = stroke/2
    ld.ellipse((p[0]-r, p[1]-r, p[0]+r, p[1]+r), fill=255)
line = ImageChops.multiply(line, leaf_mask)
grad = Image.composite(Image.new("RGB", (S, S), (12, 110, 76)), grad, line)

# 4. Lime spark at the leaf tip: a small four-point star
tip = to_world(half*0.98, 0)
spark = Image.new("L", (S, S), 0)
sd = ImageDraw.Draw(spark)
sx, sy = tip[0] + S*0.035, tip[1] - S*0.035
r1, r2 = S*0.062, S*0.018
pts = []
for i in range(8):
    ang = math.pi/2 * (i//2) + (math.pi/4 if i % 2 else 0) - math.pi/2
    r = r1 if i % 2 == 0 else r2
    pts.append((sx + r*math.cos(ang), sy + r*math.sin(ang)))
sd.polygon(pts, fill=255)
glow = spark.filter(ImageFilter.GaussianBlur(S*0.02))
grad = Image.composite(Image.new("RGB", (S, S), (220, 255, 120)), grad, glow.point(lambda v: int(v*0.55)))
grad = Image.composite(Image.new("RGB", (S, S), (198, 244, 50)), grad, spark)

grad.resize((1024, 1024), Image.LANCZOS).save(OUT, optimize=True)
print("saved", OUT)
