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

import http.client
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

UA = "TamilnaduFMRadio/1.3 (+https://github.com/universalrp2003/InternetRadio)"

# (category, query parameters, max kept, keep_unverified)
# keep_unverified=True is only used for Tamil, where the directory is genuinely
# thin and a station that answered last month is better than an empty screen -
# the app marks those rows as unchecked.
QUERIES = [
    # --- Tamil music, the heart of the app
    ({"category": "tamil", "params": {"language": "tamil", "order": "votes", "reverse": "true"}, "limit": 130, "keep_unverified": True}),
    ({"category": "tamil", "params": {"tagList": "tamil", "order": "votes", "reverse": "true"}, "limit": 60, "keep_unverified": True}),
    ({"category": "tamil", "params": {"countrycode": "IN", "language": "tamil", "order": "votes", "reverse": "true"}, "limit": 50}),
    # --- The stations people actually ask for by name
    ({"category": "tamil", "params": {"name": "hello fm"}, "limit": 12}),
    ({"category": "tamil", "params": {"name": "suryan"}, "limit": 12}),
    ({"category": "tamil", "params": {"name": "suryan fm"}, "limit": 12}),
    ({"category": "tamil", "params": {"name": "radio mirchi"}, "limit": 20}),
    ({"category": "tamil", "params": {"name": "mirchi tamil"}, "limit": 12}),
    ({"category": "tamil", "params": {"name": "radio city"}, "limit": 20}),
    ({"category": "tamil", "params": {"name": "big fm"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "fm rainbow"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "vividh bharti"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "ilayaraja"}, "limit": 20}),
    ({"category": "tamil", "params": {"name": "spb"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "a r rahman"}, "limit": 12}),
    ({"category": "tamil", "params": {"name": "tamil 80"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "tamil 90"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "old tamil"}, "limit": 15}),
    ({"category": "tamil", "params": {"name": "kovai"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "madurai"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "trichy"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "tiruchirappalli"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "dharmapuri"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "pondy"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "pondicherry"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "salem"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "tirunelveli"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "thoothukudi"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "ooty"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "kodaikanal"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "vanniyar"}, "limit": 8, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "coimbatore"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "erode"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "vellore"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "chennai"}, "limit": 12, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "tamil nadu"}, "limit": 15, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "tamilnadu"}, "limit": 15, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "nagercoil"}, "limit": 8, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "thanjavur"}, "limit": 8, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "dindigul"}, "limit": 8, "keep_unverified": True}),
    ({"category": "tamil", "params": {"tagList": "kovai"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"tagList": "madurai"}, "limit": 10, "keep_unverified": True}),
    ({"category": "tamil", "params": {"name": "tamil fm"}, "limit": 25}),
    ({"category": "tamil", "params": {"name": "tamil radio"}, "limit": 25}),
    # --- Tamil worldwide (Sri Lanka / Malaysia / Singapore / diaspora).
    # For these the name itself is proof enough that the station is Tamil.
    ({"category": "tamil_fm", "params": {"countrycode": "LK", "order": "votes", "reverse": "true"}, "limit": 40}),
    ({"category": "tamil_fm", "params": {"countrycode": "MY", "order": "votes", "reverse": "true"}, "limit": 30}),
    ({"category": "tamil_fm", "params": {"countrycode": "SG", "order": "votes", "reverse": "true"}, "limit": 15}),
    ({"category": "tamil_fm", "params": {"name": "shakthi"}, "limit": 12}),
    ({"category": "tamil_fm", "params": {"name": "sooriyan"}, "limit": 12}),
    ({"category": "tamil_fm", "params": {"name": "minnal"}, "limit": 12}),
    ({"category": "tamil_fm", "params": {"name": "raaga"}, "limit": 15}),
    ({"category": "tamil_fm", "params": {"name": "varnam"}, "limit": 10}),
    ({"category": "tamil_fm", "params": {"name": "yarl"}, "limit": 10}),
    ({"category": "tamil_fm", "params": {"name": "lankasri"}, "limit": 10}),
    ({"category": "tamil_fm", "params": {"name": "oli"}, "limit": 10}),
    ({"category": "tamil_fm", "params": {"name": "malaysia tamil"}, "limit": 15}),
    ({"category": "tamil_fm", "params": {"name": "jaffna"}, "limit": 12}),
    ({"category": "tamil_fm", "params": {"name": "eelam"}, "limit": 12}),
    # --- Tamil news: the directory is thin here, so try both tags and names
    ({"category": "tamil_news", "params": {"language": "tamil", "tagList": "news"}, "limit": 25}),
    ({"category": "tamil_news", "params": {"name": "tamil news"}, "limit": 20}),
    ({"category": "tamil_news", "params": {"name": "kalaignar"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "puthiya thalaimurai"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "thanthi"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "sun news"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "news7"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "polimer"}, "limit": 10}),
    ({"category": "tamil_news", "params": {"name": "seithi"}, "limit": 10}),
    # --- Devotional
    ({"category": "tamil_devotional", "params": {"language": "tamil", "tagList": "devotional"}, "limit": 25}),
    ({"category": "tamil_devotional", "params": {"language": "tamil", "tagList": "bhakti"}, "limit": 20}),
    ({"category": "tamil_devotional", "params": {"name": "bakthi"}, "limit": 12}),
    ({"category": "tamil_devotional", "params": {"name": "murugan"}, "limit": 10}),
    # --- World + India news in English
    ({"category": "world_news", "params": {"tagList": "news", "language": "english", "order": "votes", "reverse": "true"}, "limit": 60}),
    ({"category": "world_news", "params": {"tagList": "news", "countrycode": "US", "order": "votes", "reverse": "true"}, "limit": 25}),
    ({"category": "world_news", "params": {"tagList": "news", "countrycode": "GB", "order": "votes", "reverse": "true"}, "limit": 20}),
    ({"category": "world_news", "params": {"tagList": "news", "countrycode": "AU", "order": "votes", "reverse": "true"}, "limit": 15}),
    ({"category": "world_news", "params": {"tagList": "news", "countrycode": "CA", "order": "votes", "reverse": "true"}, "limit": 15}),
    ({"category": "world_news", "params": {"tagList": "news", "countrycode": "IE", "order": "votes", "reverse": "true"}, "limit": 10}),
    ({"category": "india_news", "params": {"countrycode": "IN", "tagList": "news", "order": "votes", "reverse": "true"}, "limit": 30}),
    ({"category": "india_news", "params": {"name": "air news"}, "limit": 15}),
    ({"category": "india_news", "params": {"name": "newsonair"}, "limit": 10}),
    # --- General English listening
    ({"category": "english", "params": {"language": "english", "order": "votes", "reverse": "true"}, "limit": 110}),
]

KEEP_CODECS = ("mp3", "aac", "aac+", "ogg", "opus", "mp4a", "hls", "m3u8", "")
# Categories thin enough in the directory that it is worth probing rows whose
# codec label we do not trust.
PROBE_CATEGORIES = ("tamil", "tamil_fm", "tamil_news", "tamil_devotional", "india_news")
PROBE_PER_QUERY = 25
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


def _one_probe(url, timeout, ranged):
    headers = {"User-Agent": UA, "Icy-MetaData": "1", "Accept": "*/*"}
    if ranged:
        headers["Range"] = "bytes=0-4095"
    request = urllib.request.Request(url, headers=headers)
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            status = getattr(response, "status", 200) or 200
            content_type = (response.headers.get("Content-Type") or "").lower()
            head = response.read(2048)
    except http.client.BadStatusLine as line:
        # Shoutcast v1 servers answer with "ICY 200 OK" instead of an HTTP status
        # line; that is a live stream, not an error.
        if "200" in str(getattr(line, "line", "")):
            return True, "icy"
        raise
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


def hls_alternates(url):
    """
    AIR (and a few other broadcasters) publish both /playlist.m3u8 (the variant
    list) and /master.m3u8. One of the two regularly 404s while the other works,
    so try the sibling before declaring a station dead.
    """
    out = []
    if not url.lower().endswith(".m3u8"):
        return out
    if "/playlist.m3u8" in url.lower():
        out.append(re.sub(r"/playlist\.m3u8$", "/master.m3u8", url, flags=re.I))
    elif "/master.m3u8" in url.lower():
        out.append(re.sub(r"/master\.m3u8$", "/playlist.m3u8", url, flags=re.I))
    return out


def probe(url, timeout=15):
    """
    Two attempts per candidate URL (a ranged GET, then a plain GET: some servers
    dislike Range), and for HLS the playlist/master sibling. Returns
    (alive, detail, url_that_answered) - the caller keeps the URL that worked.
    """
    first_error = None
    for candidate in [url] + hls_alternates(url):
        for ranged in (True, False):
            try:
                alive, detail = _one_probe(candidate, timeout, ranged)
                if alive:
                    return True, detail, candidate
                first_error = detail
            except Exception as error:  # noqa: BLE001
                first_error = f"{type(error).__name__}: {error}"
    return False, first_error or "unreachable", url


def _unused_probe_original(url, timeout=12):
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


def usable(row, category, keep_unverified, trust_tamil=False):
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
        if not trust_tamil and not tamil_evidence(row):
            return False, "not-tamil"
    if category == "english":
        language = (row.get("language") or "").lower()
        tags = (row.get("tags") or "").lower()
        if "english" not in language and "english" not in tags:
            return False, "not-english"
        if looks_non_latin(name):
            return False, "non-latin-name"
        letters = [c for c in name if c.isalpha()]
        latin = sum(1 for c in letters if c.isascii())
        if letters and latin / len(letters) < 0.6:
            return False, "not-latin-name"
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


def url_of(row):
    return (row.get("url_resolved") or row.get("url") or "").strip()


def add_station(stations, counts, row, category, source="directory", verified=True):
    """Deduplicate by URL, normalise and count. Returns 1 if it was really added."""
    url = url_of(row)
    item_key = key_of(url)
    if not url or item_key in stations:
        return 0
    row = dict(row)
    row["source"] = source
    row.setdefault("order", 0)
    final_category = classify(row, category)
    item = normalise(row, final_category, verified=verified)
    stations[item_key] = item
    counts[final_category] = counts.get(final_category, 0) + 1
    return 1


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
    for index, (item, (alive, detail, used_url)) in enumerate(results):
        if not alive:
            debug.append({"stage": "curated", "name": item["name"], "url": item["url"], "result": detail})
            print(f"   x {item['name']}: {detail}")
            continue
        row = dict(item)
        if used_url != item["url"]:
            debug.append({"stage": "curated-url-swap", "name": item["name"],
                          "from": item["url"], "to": used_url})
            print(f"   ~ {item['name']}: using {used_url}")
            row["url"] = used_url
        row["source"] = "curated"
        row["order"] = 1000 - index
        item_key = key_of(item["url"])
        stations[item_key] = normalise(row, item.get("category", "tamil"), verified=True)
        curated_ok += 1
        counts[stations[item_key]["category"]] = counts.get(stations[item_key]["category"], 0) + 1
    print(f"   {curated_ok}/{len(curated)} curated streams answered")

    # -------------------------------------------------------------- directory
    for query in QUERIES:
        category = query["category"]
        params = query["params"]
        limit = query["limit"]
        keep_unverified = bool(query.get("keep_unverified", False))
        # A "name=..." search for a specific station implies language.
        trust_tamil = "name" in params and any(
            token in params["name"]
            for token in ("tamil", "shakthi", "sooriyan", "minnal", "raaga", "varnam", "yarl",
                          "lankasri", "oli", "jaffna", "eelam", "malaysia", "kovai", "madurai",
                          "pondy", "pondicherry", "seithi", "murugan", "bakthi", "kalaignar",
                          "puthiya", "thanthi", "polimer", "news7")
        )
        print(f"-> {category}: {params}")
        rows = fetch(params)
        kept = 0
        rejected = {}
        dropped_names = []
        codec_rejects = []      # rows the directory labels with an odd codec - probed below
        for row in rows:
            if kept >= limit:
                break
            ok, reason = usable(row, category, keep_unverified, trust_tamil)
            if not ok:
                rejected[reason] = rejected.get(reason, 0) + 1
                if "name" in params and len(dropped_names) < 6:
                    dropped_names.append(f"{row.get('name')} ({reason})")
                # The directory's codec column is community-maintained and often wrong
                # (AIR and several Indian stations are filed as "asp" or left blank while
                # their public HLS/MP3 feed plays fine). For Tamil and news rows, ask the
                # stream itself instead of trusting the label.
                if (reason == "codec" and category in PROBE_CATEGORIES
                        and len(codec_rejects) < PROBE_PER_QUERY
                        and (trust_tamil or not category.startswith("tamil") or tamil_evidence(row))):
                    codec_rejects.append(row)
                continue
            kept += add_station(stations, counts, row, category, source="directory",
                                verified=row.get("lastcheckok") == 1)

        if codec_rejects:
            with ThreadPoolExecutor(max_workers=8) as pool:
                probed = list(pool.map(lambda row: (row, probe(url_of(row))), codec_rejects))
            for row, (alive, detail, used_url) in probed:
                if not alive:
                    continue
                row = dict(row)
                row["url_resolved"] = used_url
                row["codec"] = "probed"
                added = add_station(stations, counts, row, category, source="directory-probe",
                                    verified=True)
                kept += added
                debug.append({"stage": "probe-recovered", "category": category,
                              "name": row.get("name"), "url": used_url, "detail": detail})
                print(f"   + probed {row.get('name')}: {detail}")
        print(f"   kept {kept} of {len(rows)} (rejected: {rejected})")
        entry = {"stage": "directory", "category": category, "query": params,
                 "returned": len(rows), "kept": kept, "rejected": rejected}
        if dropped_names:
            entry["dropped"] = dropped_names
        debug.append(entry)
        time.sleep(0.6)

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
