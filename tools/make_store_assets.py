#!/usr/bin/env python3
"""Turn the apps' real launcher vectors into store art, with no external tools.

Why this exists: F-Droid (like Play) wants a `512x512` PNG icon and a `1024x500` feature
graphic, and the launcher icons in this repository are *vector* drawables. The sandbox used to
build them has neither librsvg nor Pillow, so this script parses the Android vector XML itself,
rasterises the paths (including elliptical arcs) with 4x supersampling for smooth edges, and
writes PNGs with zlib. The result is the same artwork the phones show, at store sizes.

  python3 tools/make_store_assets.py icons     # icon.png for every app + launcher fallbacks
  python3 tools/make_store_assets.py check     # report what exists, change nothing

`icon.png` lands in every fastlane locale's images folder:
  <module>/fastlane/metadata/android/<locale>/images/icon.png
"""

import binascii
import io
import math
import os
import re
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --------------------------------------------------------------------------------------- paths

NUM_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")

ARGC = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def tokenize_path(d):
    """Split Android/SVG pathData into command letters and numbers."""
    out = []
    i = 0
    while i < len(d):
        c = d[i]
        if c.isalpha():
            out.append(c)
            i += 1
        elif c in " ,\t\r\n":
            i += 1
        else:
            m = NUM_RE.match(d, i)
            if not m:
                raise ValueError("bad pathData at %d: %r" % (i, d[i:i + 12]))
            out.append(float(m.group()))
            i = m.end()
    return out


def arc_points(x0, y0, rx, ry, phi_deg, large_arc, sweep, x1, y1, steps=48):
    """Endpoint-parameterised elliptical arc -> polyline (SVG spec F.6.5)."""
    if rx == 0 or ry == 0 or (x0 == x1 and y0 == y1):
        return [(x1, y1)]
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(phi_deg)
    cosp, sinp = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x0 - x1) / 2.0, (y0 - y1) / 2.0
    x1p = cosp * dx2 + sinp * dy2
    y1p = -sinp * dx2 + cosp * dy2
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx, ry = rx * s, ry * s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large_arc == sweep:
        co = -co
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    cx = cosp * cxp - sinp * cyp + (x0 + x1) / 2.0
    cy = sinp * cxp + cosp * cyp + (y0 + y1) / 2.0

    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        n = math.hypot(ux, uy) * math.hypot(vx, vy)
        if n == 0:
            return 0.0
        a = math.acos(max(-1.0, min(1.0, dot / n)))
        return -a if (ux * vy - uy * vx) < 0 else a

    theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sweep and dtheta < 0:
        dtheta += 2 * math.pi
    pts = []
    for i in range(1, steps + 1):
        t = theta1 + dtheta * i / steps
        px, py = rx * math.cos(t), ry * math.sin(t)
        pts.append((cosp * px - sinp * py + cx, sinp * px + cosp * py + cy))
    return pts


def flatten(pathdata, curves=24):
    """pathData -> list of subpaths, each a list of (x, y) points."""
    toks = tokenize_path(pathdata)
    subpaths, cur = [], []
    x = y = sx = sy = 0.0
    prev_ctrl = None
    cmd = None
    i = 0
    while i < len(toks):
        t = toks[i]
        if isinstance(t, str):
            cmd = t
            i += 1
            if cmd in "Zz":
                if cur:
                    cur.append((sx, sy))
                    subpaths.append(cur)
                    cur = []
                x, y = sx, sy
            continue
        if cmd is None:
            raise ValueError("pathData starts with a number")
        up = cmd.upper()
        rel = cmd.islower()
        n = ARGC[up]
        if i + n > len(toks):
            break
        a = toks[i:i + n]
        i += n
        if up == "M":
            x, y = (x + a[0], y + a[1]) if rel else (a[0], a[1])
            if cur:
                subpaths.append(cur)
            cur = [(x, y)]
            sx, sy = x, y
            cmd = "l" if rel else "L"
            prev_ctrl = None
        elif up == "L":
            x, y = (x + a[0], y + a[1]) if rel else (a[0], a[1])
            cur.append((x, y))
            prev_ctrl = None
        elif up == "H":
            x = x + a[0] if rel else a[0]
            cur.append((x, y))
            prev_ctrl = None
        elif up == "V":
            y = y + a[0] if rel else a[0]
            cur.append((x, y))
            prev_ctrl = None
        elif up in ("C", "S"):
            if up == "C":
                c1 = (x + a[0], y + a[1]) if rel else (a[0], a[1])
                c2 = (x + a[2], y + a[3]) if rel else (a[2], a[3])
                end = (x + a[4], y + a[5]) if rel else (a[4], a[5])
            else:
                c1 = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl else (x, y)
                c2 = (x + a[0], y + a[1]) if rel else (a[0], a[1])
                end = (x + a[2], y + a[3]) if rel else (a[2], a[3])
            for k in range(1, curves + 1):
                u = k / curves
                mu = 1 - u
                cur.append((
                    mu ** 3 * x + 3 * mu * mu * u * c1[0] + 3 * mu * u * u * c2[0] + u ** 3 * end[0],
                    mu ** 3 * y + 3 * mu * mu * u * c1[1] + 3 * mu * u * u * c2[1] + u ** 3 * end[1],
                ))
            prev_ctrl = c2
            x, y = end
        elif up in ("Q", "T"):
            if up == "Q":
                c1 = (x + a[0], y + a[1]) if rel else (a[0], a[1])
                end = (x + a[2], y + a[3]) if rel else (a[2], a[3])
            else:
                c1 = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl else (x, y)
                end = (x + a[0], y + a[1]) if rel else (a[0], a[1])
            for k in range(1, curves + 1):
                u = k / curves
                mu = 1 - u
                cur.append((
                    mu * mu * x + 2 * mu * u * c1[0] + u * u * end[0],
                    mu * mu * y + 2 * mu * u * c1[1] + u * u * end[1],
                ))
            prev_ctrl = c1
            x, y = end
        elif up == "A":
            rx, ry, rot, laf, sf = a[0], a[1], a[2], int(a[3]), int(a[4])
            end = (x + a[5], y + a[6]) if rel else (a[5], a[6])
            cur.extend(arc_points(x, y, rx, ry, rot, laf, sf, end[0], end[1]))
            x, y = end
            prev_ctrl = None
    if cur:
        subpaths.append(cur)
    return subpaths


# ------------------------------------------------------------------------------------- picture

def parse_color(v):
    v = v.strip().lstrip("#")
    if len(v) == 8:      # Android allows #AARRGGBB
        v = v[2:]
    if len(v) == 6:
        return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))
    if len(v) == 3:
        return tuple(int(c * 2, 16) for c in v)
    raise ValueError("unsupported colour %r" % v)


def parse_vector(path):
    """Android vector XML -> (viewport_w, viewport_h, [(colour, subpaths), ...])."""
    xml = io.open(path, encoding="utf-8").read()
    vw = float(re.search(r'android:viewportWidth="([\d.]+)"', xml).group(1))
    vh = float(re.search(r'android:viewportHeight="([\d.]+)"', xml).group(1))
    shapes = []
    for m in re.finditer(r"<path\b([^>]*?)/?>", xml, re.S):
        attrs = m.group(1)
        d = re.search(r'android:pathData="([^"]+)"', attrs, re.S)
        c = re.search(r'android:fillColor="([^"]+)"', attrs)
        if not d or not c:
            continue
        shapes.append((parse_color(c.group(1)), flatten(d.group(1))))
    return vw, vh, shapes


class Canvas:
    """Scanline polygon filler. Even-odd rule, which is what these icon paths need."""

    def __init__(self, size, window, ss=4, bg=(0, 0, 0)):
        self.size = size
        self.ss = ss
        self.W = size * ss
        self.H = size * ss
        x0, y0, x1, y1 = window
        self.scale = self.W / (x1 - x0)
        self.x0, self.y0 = x0, y0
        self.px = bytearray(self.W * self.H * 3)
        r, g, b = bg
        row = bytes((r, g, b)) * self.W
        for y in range(self.H):
            self.px[y * self.W * 3:(y + 1) * self.W * 3] = row

    def _tx(self, p):
        return ((p[0] - self.x0) * self.scale, (p[1] - self.y0) * self.scale)

    def fill(self, subpaths, rgb):
        r, g, b = rgb
        edges = []
        for sp in subpaths:
            pts = [self._tx(p) for p in sp]
            if len(pts) < 3:
                continue
            for i in range(len(pts)):
                x1, y1 = pts[i]
                x2, y2 = pts[(i + 1) % len(pts)]
                if y1 != y2:
                    edges.append((x1, y1, x2, y2))
        if not edges:
            return
        for py in range(self.H):
            yc = py + 0.5
            xs = []
            for (ex1, ey1, ex2, ey2) in edges:
                if (ey1 <= yc < ey2) or (ey2 <= yc < ey1):
                    xs.append(ex1 + (yc - ey1) * (ex2 - ex1) / (ey2 - ey1))
            if not xs:
                continue
            xs.sort()
            base = py * self.W * 3
            for k in range(0, len(xs) - 1, 2):
                a = int(math.ceil(xs[k] - 0.5))
                c = int(math.floor(xs[k + 1] - 0.5))
                if c < 0 or a >= self.W:
                    continue
                a = max(0, a)
                c = min(self.W - 1, c)
                if c < a:
                    continue
                self.px[base + a * 3:base + c * 3 + 3] = bytes((r, g, b)) * (c - a + 1)

    def downsample(self):
        ss = self.ss
        out = bytearray(self.size * self.size * 3)
        n = ss * ss
        for y in range(self.size):
            o = y * self.size * 3
            for x in range(self.size):
                sr = sg = sb = 0
                for dy in range(ss):
                    base = ((y * ss + dy) * self.W + x * ss) * 3
                    row = self.px[base:base + ss * 3]
                    for dx in range(ss):
                        sr += row[dx * 3]
                        sg += row[dx * 3 + 1]
                        sb += row[dx * 3 + 2]
                out[o + x * 3] = sr // n
                out[o + x * 3 + 1] = sg // n
                out[o + x * 3 + 2] = sb // n
        return out


def write_png(path, size, rgb):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", binascii.crc32(tag + data) & 0xFFFFFFFF))

    raw = bytearray()
    stride = size * 3
    for y in range(size):
        raw.append(0)
        raw.extend(rgb[y * stride:(y + 1) * stride])
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


def render_icon(vector_path, size, bg, window=(18.0, 18.0, 90.0, 90.0), ss=4):
    """Draw one icon: background, then the vector's shapes, cropped to Android's 72dp safe area."""
    _, _, shapes = parse_vector(vector_path)
    c = Canvas(size, window, ss=ss, bg=parse_color(bg))
    for colour, subpaths in shapes:
        c.fill(subpaths, colour)
    return c.downsample()


# ---------------------------------------------------------------------------------------- plan

APPS = [
    dict(module="cleaner", name="CleanSweep", accent="#22D3EE", bg="#070D1A",
         vector="cleaner/src/main/res/drawable/ic_launcher_foreground.xml",
         locales=["en-US", "ta-IN"], legacy_pngs=False),
    dict(module="radio", name="Ramesh Radio", accent="#FF8A3D", bg="#0B0713",
         vector="radio/src/main/res/drawable/ic_launcher_foreground.xml",
         locales=["en-US", "ta-IN"], legacy_pngs=False),
    dict(module="app", name="Internet Radio", accent="#22D3EE", bg="#08111F",
         vector="app/src/main/res/drawable/ic_launcher_foreground.xml",
         locales=["en-US"], legacy_pngs=True),
    dict(module="builder", name="AppForge", accent="#22D3EE", bg="#0B0F1E",
         vector="builder/src/main/res/drawable/ic_launcher_foreground.xml",
         locales=["en-US"], legacy_pngs=False),
    dict(module="equalizer", name="PulseEQ", accent="#34D399", bg="#05070F",
         vector="equalizer/src/main/res/drawable/ic_launcher_foreground.xml",
         locales=["en-US"], legacy_pngs=False),
]

LAUNCHER_SIZES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]


def cmd_icons():
    made = []
    for app in APPS:
        vec = os.path.join(ROOT, app["vector"])
        if not os.path.exists(vec):
            print("SKIP %s: no vector at %s" % (app["module"], app["vector"]))
            continue
        big = render_icon(vec, 512, app["bg"])
        for loc in app["locales"]:
            out = os.path.join(ROOT, app["module"], "fastlane", "metadata", "android", loc,
                               "images", "icon.png")
            write_png(out, 512, big)
            made.append(os.path.relpath(out, ROOT))
        if app["legacy_pngs"]:
            # minSdk is 24 here, so the app also needs a bitmap for API 24-25 where adaptive
            # icons do not exist: without it the launcher would find no icon at all.
            for dens, px in LAUNCHER_SIZES:
                small = render_icon(vec, px, app["bg"], ss=6)
                out = os.path.join(ROOT, app["module"], "src", "main", "res",
                                   "mipmap-%s" % dens, "ic_launcher.png")
                write_png(out, px, small)
                made.append(os.path.relpath(out, ROOT))
    for f in made:
        print("wrote", f, os.path.getsize(os.path.join(ROOT, f)), "bytes")
    return 0


def cmd_check():
    for app in APPS:
        for loc in app["locales"]:
            p = os.path.join(ROOT, app["module"], "fastlane", "metadata", "android", loc,
                             "images", "icon.png")
            print("%-10s %-6s %s" % (app["module"], loc,
                                     "OK %d bytes" % os.path.getsize(p) if os.path.exists(p)
                                     else "MISSING"))
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "icons"
    sys.exit({"icons": cmd_icons, "check": cmd_check}[cmd]())
