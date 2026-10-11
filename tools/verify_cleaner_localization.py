#!/usr/bin/env python3
"""Source-only EN/TA placeholder/key and visible-clock checks. Not a UI/device/JVM test."""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "cleaner/src/main/java/com/universalrp/cleansweep"
STRING = r'"(?:\\.|[^"\\])*"'
EXPR = rf'{STRING}(?:\s*\+\s*{STRING})*'
# Ignore bare prose '%' (e.g. '90% after'); match only the format conversions used by the catalog.
FORMAT = re.compile(r'%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?([sdif%])')
PROPER = {"Wi-Fi", "Android", "CleanSweep", "Live Guard", "AM", "PM", "API", "CPU", "RAM", "NVIDIA", "Gemini", "OpenAI"}


def literal(expression: str) -> str:
    return "".join(json.loads(part) for part in re.findall(STRING, expression))


def verify() -> list[str]:
    catalog = (SOURCE / "data/Lang.kt").read_text(encoding="utf-8").split("private val TAMIL:", 1)[1]
    items = [(literal(m.group(1)), literal(m.group(2))) for m in re.finditer(rf'({EXPR})\s+to\s+({EXPR})', catalog)]
    keys: set[str] = set()
    for english, tamil in items:
        if english in keys:
            raise ValueError(f"Duplicate translation key: {english}")
        keys.add(english)
        if FORMAT.findall(english) != FORMAT.findall(tamil):
            raise ValueError(f"Placeholder mismatch: {english} -> {tamil}")
    missing: dict[str, set[str]] = {}
    for path in sorted(SOURCE.rglob("*.kt")):
        if path.name == "Lang.kt":
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(rf'\b(?:tr|t)\(\s*({EXPR})', text):
            key = literal(match.group(1))
            if key not in keys and key not in PROPER and re.search(r'[A-Za-z]{3}', key):
                missing.setdefault(key, set()).add(str(path.relative_to(SOURCE)))
    if missing:
        raise ValueError("Untranslated explicit UI keys:\n" + "\n".join(f"- {key}: {', '.join(sorted(paths))}" for key, paths in missing.items()))
    for directory in (SOURCE / "ui", SOURCE / "widget", SOURCE / "notify"):
        for path in directory.rglob("*.kt"):
            if re.search(r'\bHH:mm', path.read_text(encoding="utf-8")):
                raise ValueError(f"24-hour owned UI clock in {path.relative_to(SOURCE)}")
    store = (SOURCE / "data/PermissionReviewStore.kt").read_text(encoding="utf-8")
    if "System.currentTimeMillis" in store or "Instant.now" in store:
        raise ValueError("Saved review store must not invent a review date on load/open")
    return [f"{len(keys)} explicit EN/TA catalog keys; no duplicate keys or placeholder mismatches.",
            "All explicit literal tr()/t() UI keys in this audit have catalog entries.",
            "No 24-hour owned UI/widget/notification clock; saved review reads invent no date.",
            "Source-only: raw dynamic text, translation meaning, Kotlin type checking, rendering, engine/device/network behavior still need review/testing."]


def main() -> int:
    try:
        summaries = verify()
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    for summary in summaries:
        print(f"PASS: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
