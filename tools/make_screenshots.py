#!/usr/bin/env python3
"""Place phone screenshots into the fastlane folders every store reads, with optional redaction.

Stores want 2-8 portrait phone screenshots per app, named `1.png`, `2.png`, ... in
`<module>/fastlane/metadata/android/<locale>/images/phoneScreenshots/`. Two things go wrong at
this step and this tool is built around them:

  * **A private number stays visible.** `--redact` paints a solid bar over a region before the
    file is written, so hiding the public IP, the gateway, the DNS servers or an app list is one
    recorded command instead of a manual crop that silently changes the image shape.
  * **The image shape changes.** A hand-crop is rejected by the check below (min width 720,
    height/width 1.6-2.4), which is exactly what catches a screenshot that was cut instead of
    redacted.

By default the screenshot keeps its native size (a 1080x2400 phone screenshot stays 1080x2400 -
no invented device frame, no blurred filler). `--canvas WxH` instead scales to fill and centre
crops, which is what Google Play would need (its maximum aspect is 2:1, so 1080x2160).

Examples
--------
python3 tools/make_screenshots.py cleaner home.png scan.png results.png
python3 tools/make_screenshots.py radio --locale all home.png stations.png equalizer.png
python3 tools/make_screenshots.py cleaner shot1.png shot2.png shot3.png shot4.png \
        --redact 3:60,880,960,120 --redact 3:60,1010,960,60

A redaction box is `[N:]x,y,w,h` - pixel coordinates of that screenshot, in the order given on
the command line (`3:` is the third one; no prefix means every screenshot). Values may also be
percentages, like `3:5%,55%,90%,5%`, which survives a re-take at a different resolution. Boxes
paint over the region with the apps' card colour, so the file keeps its exact shape and the
hidden region is recorded in the command that produced it.

ImageMagick does the work (`convert`/`identify`), so there is nothing to install in Python.
"""

import argparse
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES = ("app", "cleaner", "builder", "equalizer", "radio")
MIN_WIDTH = 720
ASPECT = (1.6, 2.4)
BAR_COLOUR = "#0F1620"   # the card colour the apps use, so a bar reads as deliberate


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


def parse_box(text, w, h):
    """'x,y,w,h' in pixels, or with % signs, -> four integers."""
    parts = [p.strip() for p in text.replace("%", "").split(",")]
    if len(parts) != 4:
        raise ValueError("redaction box needs four numbers: x,y,w,h (got %r)" % text)
    pct = "%" in text
    vals = [float(p) for p in parts]
    if pct:
        vals = [vals[0] * w / 100.0, vals[1] * h / 100.0, vals[2] * w / 100.0, vals[3] * h / 100.0]
    x, y, bw, bh = (int(round(v)) for v in vals)
    return max(0, x), max(0, y), min(w - x, bw), min(h - y, bh)


def place(src, dst, canvas=None, redactions=()):
    """Redact (if asked), then write at native size or cover-cropped to `canvas`."""
    info = identify(src)
    w, h = info[0], info[1]
    args = ["convert", src]
    for box in redactions:
        x, y, bw, bh = parse_box(box, w, h)
        if bw <= 0 or bh <= 0:
            return "redaction box %r is outside the image" % box
        args += ["-fill", BAR_COLOUR, "-draw", "rectangle %d,%d %d,%d" % (x, y, x + bw, y + bh)]
    if canvas:
        cw, ch = canvas
        # Cover the canvas, then centre crop: nothing important is cut on a phone screenshot
        # beyond the status and navigation bars.
        args += ["-resize", "%dx%d^" % (cw, ch), "-gravity", "center",
                 "-extent", "%dx%d" % (cw, ch)]
    args += ["-strip", "-quality", "95", dst]
    out = subprocess.run(args, capture_output=True, text=True)
    if out.returncode != 0:
        tail = (out.stderr or out.stdout).strip().splitlines()
        return tail[-1] if tail else "convert failed"
    got = identify(dst)
    if got is None:
        return "wrote %s but it is not a readable image" % dst
    if canvas and tuple(got[:2]) != tuple(canvas):
        return "wrote %s but it is %s, not %dx%d" % (dst, got[:2], canvas[0], canvas[1])
    if not canvas and (got[0], got[1]) != (w, h):
        return "wrote %s but it is %s, not %dx%d" % (dst, got[:2], w, h)
    return None


def main():
    ap = argparse.ArgumentParser(description="Place phone screenshots for a store listing.")
    ap.add_argument("module", choices=MODULES)
    ap.add_argument("shots", nargs="+", help="screenshot files, in the order to show them")
    ap.add_argument("--locale", default="en-US", help="locale folder, or 'all'")
    ap.add_argument("--count", type=int, default=None,
                    help="fail unless exactly this many screenshots were given")
    ap.add_argument("--redact", action="append", default=[], metavar="N:x,y,w,h",
                    help="region to paint over on screenshot N (pixels or percentages); "
                         "repeat for more boxes. Without 'N:' the box applies to every shot")
    ap.add_argument("--canvas", default=None, metavar="WxH",
                    help="scale to fill and centre-crop to this size (Play needs at most 2:1); "
                         "default keeps the phone's own size")
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

    canvas = None
    if args.canvas:
        try:
            cw, ch = (int(v) for v in args.canvas.lower().split("x"))
        except ValueError:
            return fail("--canvas wants WxH, e.g. 1080x2160")
        if ch / float(cw) > 2.0:
            return fail("--canvas %dx%d has an aspect above 2:1, which Google Play rejects"
                        % (cw, ch))
        canvas = (cw, ch)

    base = os.path.join(ROOT, args.module, "fastlane", "metadata", "android")
    if args.locale == "all":
        locales = sorted(d for d in os.listdir(base) if "-" in d and len(d) == 5)
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
            boxes = [b for b in args.redact if ":" not in b]
            boxes += [b.split(":", 1)[1] for b in args.redact
                      if ":" in b and int(b.split(":", 1)[0]) == n]
            err = place(src, dst, canvas=canvas, redactions=boxes)
            if err:
                return fail(err)
            got = identify(dst)
            extra = " (redacted: %s)" % ", ".join(boxes) if boxes else ""
            print("wrote %s %dx%d from %s%s"
                  % (os.path.relpath(dst, ROOT), got[0], got[1], src, extra))
    return 0


if __name__ == "__main__":
    sys.exit(main())
