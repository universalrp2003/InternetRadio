# Store assets

| File | What it is |
|---|---|
| `fdroid/REPORT.md` | The honest store-route verdict: which free store can take these apps and why. Read this one first. |
| `fdroid/README.md` | IzzyOnDroid request text and the F-Droid step-by-step (the chosen route). |
| `fdroid/metadata/*.yml` | The five recipe files that go into F-Droid's `fdroiddata` repository. |
| `fdroid/RFP_ISSUE.md` | Paste-ready text for F-Droid's Requests-For-Packaging tracker. |
| `PLAY_SUBMISSION.md` | The Google Play copy-paste pack: which apps qualify, why CleanSweep cannot go there, the Data safety and content-rating answers, and the upload steps. |
| `../PRIVACY.md` | The privacy policy Play requires a public URL for. |
| `<module>/fastlane/metadata/android/<locale>/` | The listing text every store reads: `title.txt`, `short_description.txt`, `full_description.txt` and `changelogs/<versionCode>.txt`. English everywhere, plus Tamil (`ta-IN`) for CleanSweep and Ramesh Radio. |

## Screenshots and graphics

Store pages want 2–8 phone screenshots per app, plus a 512×512 icon and a 1024×500 feature
graphic. They go here, next to the text they belong to:

```
<module>/fastlane/metadata/android/<locale>/images/
    icon.png                     512 x 512    <- generated, do not edit by hand
    featureGraphic.png           1024 x 500   <- generated, do not edit by hand
    phoneScreenshots/1.png       portrait phone screenshots, numbered <- still to come
    phoneScreenshots/2.png
```

**The icon and the feature graphic are generated from the real launcher vectors**, not drawn
separately, so they can never drift from what the phone shows:

```bash
python3 tools/make_store_assets.py icons   # every app: 512x512 icons (+ bitmap fallbacks for
                                           # Internet Radio, whose minSdk is 24)
bash tools/make_feature_graphics.sh        # every app: 1024x500 graphics, text taken from the
                                           # fastlane listings, colours from the app themes
```

Both scripts are pure Python / ImageMagick — no Pillow, no librsvg, and nothing to install.
CI runs them on every push, so a fresh checkout always has a complete listing.

### The screenshots, and the exact API we render them to

The phone screenshots are the only piece that still has to come from a real phone, and they
are placed by `tools/make_screenshots.py`:

```bash
python3 tools/make_screenshots.py cleaner home.png scan.png results.png
python3 tools/make_screenshots.py radio --locale all home.png stations.png equalizer.png
python3 tools/make_screenshots.py app --count 4 1.png 2.png 3.png 4.png
```

It numbers them in the order you pass them and writes `1.png`, `2.png`, ... into
`<module>/fastlane/metadata/android/<locale>/images/phoneScreenshots/`. Each one is scaled to
the 1080x1920 canvas, keeps **no invented device frame**, and fills the edges with a blurred
copy of the same screenshot so nothing is cut off. Before writing anything it refuses files that
are not readable portrait phone screenshots (minimum width 720, height/width 1.6-2.4) - that
check is the one that catches a hand-crop, which is what a redaction becomes if it is done by
scalpel rather than by cropping bar-free.
