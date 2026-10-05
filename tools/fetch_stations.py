#!/usr/bin/env python3
"""
Builds stations_seed.json for the Tamilnadu FM Radio app.

Two sources, merged:

1. tools/curated_stations.json - hand-picked popular Tamil / Tamil-diaspora /
   news streams. Every URL here is *probed* (a shallow GET that only checks the
   response, it does not download the stream) and only entries that really answer
   with audio or a valid HLS playlist are shipped. Curated entries that fail the
   probe are dropped, so the app never ships a link we know is dead.

2. Radio-Browser (https://www.radio-browser.info) - a free, open, community
   maintained directory of internet radio. The queries below are the ones that
   actually return Tamil and news stations; every result carries a health-check
   flag (lastcheckok) which becomes the app's "verified" marker.

Run by .github/workflows/refresh-stations.yml (manual, on changes, monthly), or:

    python3 tools/fetch_stations.py radio/src/main/assets/stations_seed.json
"""

import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
CURATED_PATH = os.path.join(HERE, "curated_stations.json")

MIRRORS = [
    "https://de1.api.radio-browser.info",
    "https://de2.api.radio-browser.info",
    "https://nl1.api.radio-browser.info",
    "https://at1.api.radio-browser.info",
    "https://fi1.api.radio-browser.info",
]

UA = "TamilnaduFMRadio/1.2 (+https://github.com/universalrp2003/InternetRadio)"

# (category, query parameters, max kept, keep_unverified)
# keep_unverified=True is only used for Tamil, where the directory is genuinely
# thin and a station that answered last month is better than an empty screen -
# the app marks those rows as unchecked.
QUERIES = [
    ("tamil", {"language": "tamil", "order": "votes", "reverse": "true"}, 130, True),
    ("tamil", {"tagList": "tamil", "order": "votes", "reverse": "true"}, 70, True),
    ("tamil", {"countrycode": "IN", "language": "tamil", "order": "votes", "reverse": "true"}, 50, False),
    ("tamil_devotional", {"language": "tamil", "tagList": "bhakti,devotional,temple", "order": "votes", "reverse": "true"}, 30, False),
    ("tamil_news", {"language": "tamil", "tagList": "news,talk", "order": "votes", "reverse": "true"}, 25, False),
    ("tamil_fm", {"countrycode": "LK", "order": "votes", "reverse": "true"}, 60, False),
    ("tamil_fm", {"countrycode": "MY", "order": "votes", "reverse": "true"}, 40, False),
    ("tamil_fm", {"countrycode": "SG", "order": "votes", "reverse": "true"}, 20, False),
    ("world_news", {"tagList": "news", "language": "english", "order": "votes", "reverse": "true"}, 60, False),
    ("world_news", {"tagList": "news", "countrycode": "US", "order": "votes", "reverse": "true"}, 25, False),
    ("world_news", {"tagList": "news", "countrycode": "GB", "order": "votes", "reverse": "true"}, 20, False),
    ("world_news", {"tagList": "news", "countrycode": "AU", "order": "votes", "reverse": "true"}, 15, False),
    ("world_news", {"tagList": "news", "countrycode": "CA", "order": "votes", "reverse": "true"}, 15, False),
    ("india_news", {"countrycode": "IN", "tagList": "news", "order": "votes", "reverse": "true"}, 30, False),
    ("english", {"language": "english", "order": "votes", "reverse": "true"}, 110, False),
]

KEEP_CODECS = ("mp3", "aac", "aac+", "ogg", "opus", "mp4a", "hls", "m3u8", "")
MAX_TOTAL = 560

# Where Tamil stations live. Used to classify a station the directory did not tag.
TAMIL_PLACES = ("IN", "LK", "MY", "SG", "AE", "GB", "CA", "US", "AU", "ZA", "FR", "DE", "CH", "QA", "MU", "RE")

NON_LATIN = re.compile(r"[\u0400-\u04FF\u0600-\u06FF\u0900-\u097F\u0B80-\u0BFF\u0C00-\u0C7F\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]")

NEWS_WORDS = ("news", "samachar", "seithi", "seithigal", "khabar", "talk", "current affairs")
DEVOTIONAL_WORDS = ("bhakti", "bakthi", "devotional", "temple", "bhajan", "gospel", "christian", "islamic", "quran", "spiritual")


# --------------------------------------------------------------------- fetching

def fetch(query, retries=2):
    """Fetch one search from the first mirror that answers."""
    params = dict(query)
    params.update({"limit": "200", "hidebroken": "false"})
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


def probe(url, timeout=12):
    """
    Cheap liveness check: ask for the first few kB only and look at what comes
    back. Icecast/Shoutcast answer with an audio content type, HLS with an
    m3u8 playlist. Anything else (HTML error page, 404, timeout) fails.
    """
    headers = {
        "User-Agent": UA,
        "Icy-MetaData": "1",
        "Range": "bytes=0-4095",
        "Accept": "*/*",
    }
    try:
        request = urllib.request.Request(url, headers=headers)
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            status = getattr(response, "status", 200) or 200
            content_type = (response.headers.get("Content-Type") or "").lower()
            head = response.read(2048)
    except Exception as error:  # noqa: BLE001 - any failure means "not usable"
        return False, f"{type(error).__name__}: {error}"

    if status >= 400:
        return False, f"http {status}"
    if not head:
        return False, "empty response"
    if head.startswith(b"#EXTM3U"):
        return True, "hls playlist"
    if head.startswith(b"ID3"):
        return True, "mp3/id3"
    if len(head) > 1 and head[0] == 0xFF and (head[1] & 0xE0) == 0xE0:
        return True, "aac/mp3 frame"
    if head.startswith(b"OggS"):
        return True, "ogg"
    for token in ("audio", "mpegurl", "ogg", "mp3", "aac", "octet-stream", "mpeg"):
        if token in content_type:
            return True, content_type
    return False, f"not audio (content-type={content_type or 'none'})"


# ------------------------------------------------------------------- filtering

def tamil_evidence(row):
    text = " ".join(
        str(row.get(key) or "").lower() for key in ("language", "tags", "name", "state")
    )
    return "tamil" in text or "தமிழ்" in text


def looks_non_latin(name):
    letters = [c for c in name if c.isalpha()]
    if not letters:
        return False
    foreign = sum(1 for c in letters if NON_LATIN.match(c))
    return foreign / len(letters) > 0.3


def usable(row, category, keep_unverified):
    last_check = row.get("lastcheckok")
    if last_check != 1 and not keep_unverified:
        return False, "unchecked"
    codec = (row.get("codec") or "").strip().lower()
    if codec not in KEEP_CODECS:
        return False, "codec"
    url = (row.get("url_resolved") or row.get("url") or "").strip()
    if not url.startswith(("http://", "https://")):
        return False, "url"
    name = (row.get("name") or "").strip()
    if not name:
        return False, "name"

    if category.startswith("tamil"):
        if not tamil_evidence(row):
            return False, "not-tamil"
    if category == "english":
        language = (row.get("language") or "").lower()
        tags = (row.get("tags") or "").lower()
        if "english" not in language and "english" not in tags:
            return False, "not-english"
        if looks_non_latin(name):
            return False, "non-latin-name"
    if category in ("world_news", "india_news"):
        language = (row.get("language") or "").lower()
        if language and "english" not in language and category == "world_news":
            return False, "not-english"
        if looks_non_latin(name):
            return False, "non-latin-name"
    return True, "ok"


def classify(row, fallback):
    """Refine the category from the station's own name and tags."""
    text = ((row.get("name") or "") + " " + (row.get("tags") or "")).lower()
    country = (row.get("countrycode") or "").strip().upper()
    tamil = tamil_evidence(row)

    if tamil and any(word in text for word in NEWS_WORDS):
        return "tamil_news"
    if tamil and any(word in text for word in DEVOTIONAL_WORDS):
        return "tamil_devotional"
    if fallback == "tamil_fm" and tamil:
        return "tamil_fm"
    if tamil and country in ("LK", "MY", "SG", "AE", "GB", "CA", "US", "AU", "ZA", "FR", "MU", "QA"):
        return "tamil_fm"
    if tamil:
        return "tamil"
    if fallback == "india_news" and country == "IN":
        return "india_news"
    return fallback


def normalise(row, category, verified=True):
    url = (row.get("url_resolved") or row.get("url") or "").strip()
    try:
        bitrate = int(row.get("bitrate") or 0)
    except (TypeError, ValueError):
        bitrate = 0
    return {
        "id": row.get("stationuuid") or row.get("id") or url,
        "name": (row.get("name") or "").strip()[:120],
        "url": url,
        "favicon": (row.get("favicon") or "").strip(),
        "homepage": (row.get("homepage") or "").strip(),
        "tags": (row.get("tags") or "").strip()[:200],
        "country": (row.get("country") or "").strip(),
        "countryCode": (row.get("countrycode") or row.get("countryCode") or "").strip().upper(),
        "state": (row.get("state") or "").strip(),
        "language": (row.get("language") or "").strip(),
        "codec": (row.get("codec") or "").strip(),
        "bitrate": bitrate,
        "votes": int(row.get("votes") or 0),
        "category": category,
        "isCustom": False,
        "verified": bool(verified),
        "source": row.get("source") or "directory",
        "order": int(row.get("order") or 0),
    }


def load_curated():
    with open(CURATED_PATH, encoding="utf-8") as handle:
        payload = json.load(handle)
    return payload.get("stations", [])


def key_of(url):
    return re.sub(r"/+$", "", url.strip().lower())


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "radio/src/main/assets/stations_seed.json"
    stations = {}
    counts = {}
    debug = []

    # ---------------------------------------------------------------- curated
    curated = load_curated()
    print(f"probing {len(curated)} curated streams")
    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(pool.map(lambda item: (item, probe(item["url"])), curated))

    curated_ok = 0
    for index, (item, (alive, detail)) in enumerate(results):
        if not alive:
            debug.append({"stage": "curated", "name": item["name"], "url": item["url"], "result": detail})
            print(f"   x {item['name']}: {detail}")
            continue
        row = dict(item)
        row["source"] = "curated"
        row["order"] = 1000 - index
        item_key = key_of(item["url"])
        stations[item_key] = normalise(row, item.get("category", "tamil"), verified=True)
        curated_ok += 1
        counts[stations[item_key]["category"]] = counts.get(stations[item_key]["category"], 0) + 1
    print(f"   {curated_ok}/{len(curated)} curated streams answered")

    # -------------------------------------------------------------- directory
    for category, query, limit, keep_unverified in QUERIES:
        print(f"-> {category}: {query}")
        rows = fetch(query)
        kept = 0
        rejected = {}
        for row in rows:
            if kept >= limit:
                break
            ok, reason = usable(row, category, keep_unverified)
            if not ok:
                rejected[reason] = rejected.get(reason, 0) + 1
                continue
            final_category = classify(row, category)
            url = (row.get("url_resolved") or row.get("url") or "").strip()
            item_key = key_of(url)
            if item_key in stations:
                continue
            row = dict(row)
            row["source"] = "directory"
            row.setdefault("order", 0)
            item = normalise(row, final_category, verified=row.get("lastcheckok") == 1)
            stations[item_key] = item
            counts[final_category] = counts.get(final_category, 0) + 1
            kept += 1
        print(f"   kept {kept} of {len(rows)} (rejected: {rejected})")
        debug.append({"stage": "directory", "category": category, "query": query,
                      "returned": len(rows), "kept": kept, "rejected": rejected})
        time.sleep(1)

    # ------------------------------------------------------------------ order
    category_order = {
        "tamil": 0,
        "tamil_fm": 1,
        "tamil_devotional": 2,
        "tamil_news": 3,
        "india_news": 4,
        "world_news": 5,
        "english": 6,
    }
    ordered = sorted(
        stations.values(),
        key=lambda s: (
            category_order.get(s["category"], 9),
            0 if s["verified"] else 1,
            -s["order"],
            -s["votes"],
            s["name"].lower(),
        ),
    )
    ordered = ordered[:MAX_TOTAL]
    verified = sum(1 for s in ordered if s["verified"])

    payload = {
        "source": "radio-browser.info + curated, probed list",
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "note": (
            "Seed list shipped with the app. 'verified' means the stream answered a health "
            "check or a build-time probe; the app marks the rest as unchecked. The app can "
            "also search the live directory for thousands more stations."
        ),
        "counts": counts,
        "verifiedCount": verified,
        "total": len(ordered),
        "debug": debug,
        "stations": ordered,
    }

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=0)
    print(f"wrote {len(ordered)} stations ({verified} verified) to {out_path}")
    print("counts:", counts)


if __name__ == "__main__":
    main()
