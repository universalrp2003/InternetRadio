#!/usr/bin/env python3
"""
Sanity-checks a station seed list. Used by CI right after the APK is unpacked, so a
broken or Tamil-less seed list fails the build instead of shipping silently.

    python3 tools/check_seed.py <path-to-stations_seed.json>
"""

import json
import sys


def main():
    if len(sys.argv) < 2:
        print("usage: check_seed.py <stations_seed.json>")
        return 2
    path = sys.argv[1]
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)

    stations = data.get("stations") or []
    assert stations, "the seed list has no stations"

    tamil = [s for s in stations if str(s.get("category", "")).startswith("tamil")]
    assert tamil, "the seed list has no Tamil stations"

    news = [s for s in stations if "news" in str(s.get("category", ""))]
    assert news, "the seed list has no news stations"

    verified = [s for s in stations if s.get("verified")]
    assert verified, "no station in the seed list is marked verified"

    http_only = [
        s for s in stations
        if not str(s.get("url", "")).startswith(("http://", "https://"))
    ]
    assert not http_only, f"{len(http_only)} stations do not use http(s) URLs"

    print(
        f"seed list OK: {len(stations)} stations "
        f"({len(tamil)} Tamil, {len(news)} news, {len(verified)} verified)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
