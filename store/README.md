# Store assets

| File | What it is |
|---|---|
| `PLAY_SUBMISSION.md` | The Google Play copy-paste pack: which apps qualify, why CleanSweep cannot go there, the Data safety and content-rating answers, and the upload steps. |
| `../PRIVACY.md` | The privacy policy Play requires a public URL for. |
| `<module>/fastlane/metadata/android/<locale>/` | The listing text every store reads: `title.txt`, `short_description.txt`, `full_description.txt` and `changelogs/<versionCode>.txt`. English everywhere, plus Tamil (`ta-IN`) for CleanSweep and Ramesh Radio. |

## Screenshots and graphics

Store pages want 2–8 phone screenshots per app, plus a 512×512 icon and a 1024×500 feature
graphic. They go here, next to the text they belong to:

```
<module>/fastlane/metadata/android/<locale>/images/
    icon.png                     512 x 512
    featureGraphic.png           1024 x 500   (F-Droid, IzzyOnDroid, Play)
    phoneScreenshots/1.png       portrait phone screenshots, numbered
    phoneScreenshots/2.png
```

The launcher icon in each module is a vector asset, which store uploaders will not accept, so
the 512×512 PNG has to be exported once from it.
