#!/usr/bin/env python3
"""
Builds station_seed.json for the Tamilnadu FM Radio app.

Source: Radio-Browser (https://www.radio-browser.info) — a free, open, community
maintained directory of internet radio stations. We only keep stations whose last
health check passed and which stream over plain MP3/AAC/Ogg, then group them into
the categories the app shows (Tamil, Tamil news, world news, English, ...).

Run by .github/workflows/refresh-stations.yml (manual or monthly), and by hand:

    python3 tools/fetch_stations.py radio/src/main/assets/stations_seed.json
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

MIRRORS = [
    "https://de1.api.radio-browser.info",
    "https://de2.api.radio-browser.info",
    "https://nl1.api.radio-browser.info",
    "https://at1.api.radio-browser.info",
    "https://fi1.api.radio-browser.info",
]

UA = "TamilnaduFMRadio/1.0 (+https://github.com/universalrp2003/InternetRadio)"

# category -> (query parameters, max stations kept)
QUERIES = [
    ("tamil", {"language": "tamil", "order": "votes", "reverse": "true"}, 120),
    ("tamil_news", {"language": "tamil", "tag": "news", "order": "votes", "reverse": "true"}, 40),
    ("tamil_fm", {"countrycode": "LK", "language": "tamil", "order": "votes", "reverse": "true"}, 30),
    ("tamil_fm", {"countrycode": "MY", "language": "tamil", "order": "votes", "reverse": "true"}, 20),
    ("tamil_fm", {"countrycode": "SG", "language": "tamil", "order": "votes", "reverse": "true"}, 15),
    ("tamil_devotional", {"language": "tamil", "tag": "devotional", "order": "votes", "reverse": "true"}, 25),
    ("world_news", {"language": "english", "tag": "news", "order": "votes", "reverse": "true"}, 60),
    ("india_news", {"countrycode": "IN", "tag": "news", "order": "votes", "reverse": "true"}, 40),
    ("english", {"language": "english", "order": "clicks", "reverse": "true"}, 80),
]

KEEP_CODECS = ("mp3", "aac", "aac+", "ogg", "opus", "mp4a")
EXTRA_PARAMS = {
    "limit": "200",
    "hidebroken": "true",
    "is_https": "false",  # both http and https are fine; this only skips the filter
}


def fetch(query, retries=2):
    """Fetch one search from the first mirror that answers."""
    params = dict(query)
    params.update(EXTRA_PARAMS)
    qs = "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in params.items())
    last_error = None
    for mirror in MIRRORS:
        url = f"{mirror}/json/stations/search?{qs}"
        for attempt in range(retries):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": UA})
                with urllib.request.urlopen(request, timeout=45) as response:
                    return json.loads(response.read().decode("utf-8", "replace"))
            except (urllib.error.URLError, urllib.error.HTTPError, OSError, ValueError) as error:
                last_error = error
                time.sleep(2 + attempt * 3)
    print(f"  ! giving up on {query}: {last_error}", file=sys.stderr)
    return []


def usable(station):
    if station.get("lastcheckok") != 1:
        return False
    codec = (station.get("codec") or "").strip().lower()
    if codec not in KEEP_CODECS:
        return False
    url = (station.get("url_resolved") or station.get("url") or "").strip()
    if not url.startswith(("http://", "https://")):
        return False
    name = (station.get("name") or "").strip()
    if not name:
        return False
    return True


def normalise(station, category):
    url = (station.get("url_resolved") or station.get("url") or "").strip()
    try:
        bitrate = int(station.get("bitrate") or 0)
    except (TypeError, ValueError):
        bitrate = 0
    return {
        "id": station.get("stationuuid") or url,
        "name": (station.get("name") or "").strip()[:120],
        "url": url,
        "favicon": (station.get("favicon") or "").strip(),
        "homepage": (station.get("homepage") or "").strip(),
        "tags": (station.get("tags") or "").strip()[:200],
        "country": (station.get("country") or "").strip(),
        "countrycode": (station.get("countrycode") or "").strip().upper(),
        "state": (station.get("state") or "").strip(),
        "language": (station.get("language") or "").strip(),
        "codec": (station.get("codec") or "").strip(),
        "bitrate": bitrate,
        "votes": int(station.get("votes") or 0),
        "category": category,
    }


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "radio/src/main/assets/stations_seed.json"
    stations = {}
    counts = {}

    for category, query, limit in QUERIES:
        print(f"-> {category}: {query}")
        rows = fetch(query)
        kept = 0
        for row in rows:
            if kept >= limit:
                break
            if not usable(row):
                continue
            item = normalise(row, category)
            key = item["url"].lower()
            if key in stations:
                continue
            stations[key] = item
            kept += 1
        counts[category] = counts.get(category, 0) + kept
        print(f"   kept {kept} (total {len(stations)})")
        time.sleep(1)

    ordered = sorted(
        stations.values(),
        key=lambda s: (s["category"], -s["votes"], s["name"].lower()),
    )
    payload = {
        "source": "radio-browser.info",
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "note": "Seed list shipped with the app; the app can also search the live directory.",
        "counts": counts,
        "stations": ordered,
    }

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
    print(f"wrote {len(ordered)} stations to {out_path}")


if __name__ == "__main__":
    import urllib.parse  # noqa: E402  (used inside fetch)

    main()
