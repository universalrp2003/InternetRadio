#!/usr/bin/env python3
"""Place phone screenshots into the fastlane folders every store reads.

Stores want 2-8 portrait phone screenshots per app, named `1.png`, `2.png`, ... in
`<module>/fastlane/metadata/android/<locale>/images/phoneScreenshots/`. Phone screenshots are
rarely exactly 1080x1920, and a hand-cropped image is a common mistake (a crop that removes
private data changes the shape). This tool:

  * refuses anything that is not a readable, portrait, full-screen phone screenshot
    (min width 720, height/width between 1.6 and 2.4) - which is what catches a hand-crop;
  * scales to a 1080x1920 canvas, filling the leftover space with a blurred copy of the
    screenshot itself, so nothing important is cut off and no device frame is invented;
  * numbers the files in the order you pass them, and writes them for the right locale.

Examples
--------
python3 tools/make_screenshots.py cleaner home.png scan.png results.png
python3 tools/make_screenshots.py radio --locale all home.png stations.png equalizer.png
python3 tools/make_screenshots.py app --count 4 1.png 2.png 3.png 4.png

ImageMagick does the scaling (`convert`/`identify`), so there is nothing to install in Python.
"""

import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES = ("app", "cleaner", "builder", "equalizer", "radio")
CANVAS = (1080, 1920)
MIN_WIDTH = 720
ASPECT = (1.6, 2.4)


def fail(msg):
    print("ERROR: " + msg)
    return 1


def identify(path):
    out = subprocess.run(["identify", "-format", "%w %h %m", path],
                         capture_output=True, text=True)
    if out.returncode != 0:
        return None
    w, h, fmt = out.stdout.split()
    return int(w), int(h), fmt


def check(path):
    """Return an error string, or None when the image looks like a full phone screenshot."""
    if not os.path.exists(path):
        return "%s: file does not exist" % path
    info = identify(path)
    if info is None:
        return "%s: not a readable image (is ImageMagick installed?)" % path
    w, h, fmt = info
    if fmt not in ("PNG", "JPEG"):
        return "%s: %s is not a PNG or JPEG" % (path, fmt)
    if w < MIN_WIDTH:
        return ("%s: %dx%d is too small for a store page (minimum width %d) - a screenshot that "
                "was scaled down will look blurry" % (path, w, h, MIN_WIDTH))
    ratio = h / float(w)
    if not (ASPECT[0] <= ratio <= ASPECT[1]):
        return ("%s: %dx%d is not a portrait phone screenshot (height/width = %.2f, expected "
                "%.1f-%.1f). A cropped or rotated image is usually the cause."
                % (path, w, h, ratio, ASPECT[0], ASPECT[1]))
    return None


def place(src, dst):
    """Scale to the canvas, filling the edges with a blurred copy of the same screenshot."""
    cmd = [
        "convert",
        "(", src, "-resize", "%dx%d^" % CANVAS, "-blur", "0x40", "-modulate", "55", ")",
        "(", src, "-resize", "%dx%d" % CANVAS, ")",
        "-gravity", "center", "-composite",
        "-extent", "%dx%d" % CANVAS,
        "-strip", "-quality", "92",
        dst,
    ]
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        tail = (out.stderr or out.stdout).strip().splitlines()
        return tail[-1] if tail else "convert failed"
    info = identify(dst)
    if info is None or tuple(info[:2]) != CANVAS:
        return "wrote %s but it is %s, not %dx%d" % (dst, info, CANVAS[0], CANVAS[1])
    return None


def main():
    ap = argparse.ArgumentParser(description="Place phone screenshots for a store listing.")
    ap.add_argument("module", choices=MODULES)
    ap.add_argument("shots", nargs="+", help="screenshot files, in the order to show them")
    ap.add_argument("--locale", default="en-US", help="locale folder, or 'all'")
    ap.add_argument("--count", type=int, default=None,
                    help="fail unless exactly this many screenshots were given")
    args = ap.parse_args()

    if not 2 <= len(args.shots) <= 8:
        return fail("stores want 2-8 screenshots; %d given" % len(args.shots))
    if args.count is not None and args.count != len(args.shots):
        return fail("--count %d but %d screenshots were given" % (args.count, len(args.shots)))

    problems = [p for p in (check(s) for s in args.shots) if p]
    if problems:
        for p in problems:
            print("ERROR: " + p)
        return 1

    base = os.path.join(ROOT, args.module, "fastlane", "metadata", "android")
    if args.locale == "all":
        locales = sorted(d for d in os.listdir(base) if re.match(r"^[a-z]{2}(-[A-Z]{2})?$", d))
    else:
        locales = [args.locale]
    if not locales:
        return fail("no locale folders under %s" % base)

    for loc in locales:
        outdir = os.path.join(base, loc, "images", "phoneScreenshots")
        if not os.path.isdir(outdir):
            if not os.path.isdir(os.path.dirname(outdir)):
                return fail("%s has no images folder - run tools/make_store_assets.py first" % loc)
            os.makedirs(outdir)
        for n, src in enumerate(args.shots, start=1):
            dst = os.path.join(outdir, "%d.png" % n)
            err = place(src, dst)
            if err:
                return fail(err)
            print("wrote %s (%dx%d) from %s" % (os.path.relpath(dst, ROOT), CANVAS[0], CANVAS[1], src))
    return 0


if __name__ == "__main__":
    sys.exit(main())
