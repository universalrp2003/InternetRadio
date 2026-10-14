#!/usr/bin/env python3
"""Source-only checks for the shared Tamil-default catalogs in Radio, PulseEQ and Vocal Remover."""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "shared/src/main/java/com/universalrp/updates/AppLanguage.kt"
STRING = r'"(?:\\.|[^"\\])*"'
EXPR = rf'{STRING}(?:\s*\+\s*{STRING})*'
PROPER = {"Ramesh Radio", "PulseEQ", "RP VOCAL REMOVER", "STUDIO", "›"}
FORMAT_TEMPLATES = {"%.1f dB", "Preamp %.1f dB", "preamp %.1f dB", "Volume boost: %.0f dB"}
EXPECTED_RELEASE_URLS = {
    "https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.46",
    "https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.11",
    "https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.5",
    "https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.11",
}


def decode_expression(expression: str) -> str:
    return "".join(json.loads(part) for part in re.findall(STRING, expression))


def catalog_keys() -> set[str]:
    source = CATALOG.read_text(encoding="utf-8")
    if "TAMIL(\"ta\")" not in source or "?: TAMIL" not in source or "current: AppLanguage by mutableStateOf(AppLanguage.TAMIL)" not in source:
        raise ValueError("Shared language must default to Tamil when no English choice is saved.")
    body = source.split("private val TAMIL = mapOf(", 1)[1].split("\n    )", 1)[0]
    entries = [
        (json.loads(match.group(1)), json.loads(match.group(2)))
        for match in re.finditer(rf'^\s*({STRING})\s+to\s+({STRING})\s*,?\s*$', body, re.M)
    ]
    counts = Counter(key for key, _ in entries)
    duplicates = sorted(key for key, count in counts.items() if count > 1)
    if duplicates:
        raise ValueError("Duplicate shared Tamil keys: " + ", ".join(repr(key) for key in duplicates))
    for english, tamil in entries:
        if not english.strip() or not tamil.strip():
            raise ValueError(f"Empty Tamil catalog value for {english!r}")
    for dynamic_label in ('Regex("^Preamp ', 'Regex("^preamp ', 'Regex("^Volume boost: '):
        if dynamic_label not in source:
            raise ValueError(f"Missing runtime Tamil formatter for {dynamic_label}")
    return set(counts)


def verify_visible_text(module: str, keys: set[str]) -> int:
    source_root = ROOT / module / "src/main/java"
    missing: dict[str, list[str]] = {}
    checked = 0
    for path in sorted(source_root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for match in re.finditer(rf'\bText\s*\(\s*(?:text\s*=\s*)?({EXPR})', source):
            key = decode_expression(match.group(1))
            after = source[match.end():].lstrip()
            # A concatenated/dynamic suffix is formatted at runtime; its static prefix is
            # not a full catalog key. The relevant user-facing templates are checked below.
            if after.startswith("+") or after.startswith(".") or "$" in key:
                continue
            if key in keys or key in PROPER or key in FORMAT_TEMPLATES or not re.search(r"[A-Za-z]{3}", key):
                checked += 1
                continue
            missing.setdefault(key, []).append(str(path.relative_to(ROOT)))
        if module == "shared":
            for match in re.finditer(rf'\btext\(\s*({EXPR})', source):
                key = decode_expression(match.group(1))
                after = source[match.end():].lstrip()
                if after.startswith("+") or after.startswith(".") or "$" in key:
                    continue
                if key in keys or key in PROPER or not re.search(r"[A-Za-z]{3}", key):
                    checked += 1
                else:
                    missing.setdefault(key, []).append(str(path.relative_to(ROOT)))
    if missing:
        detail = "\n".join(f"- {key!r}: {', '.join(sorted(set(paths)))}" for key, paths in sorted(missing.items()))
        raise ValueError(f"Untranslated explicit Compose labels in {module}:\n{detail}")
    return checked


def verify_appstrings_accessibility_and_toasts(keys: set[str]) -> None:
    missing: dict[str, list[str]] = {}
    for module in ("radio", "equalizer", "vocalremover"):
        source_root = ROOT / module / "src/main/java"
        for path in sorted(source_root.rglob("*.kt")):
            for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                # Accessibility labels are not visible Text composables, but still part of the
                # bilingual UI. Dynamic exception fragments are handled by explicit formatters.
                if "AppStrings.translate(" in line:
                    for match in re.finditer(STRING, line):
                        key = json.loads(match.group())
                        if "$" not in key and re.search(r"[A-Za-z]{3}", key) and key not in keys:
                            missing.setdefault(key, []).append(f"{path.relative_to(ROOT)}:{line_no}")
                # Toast copy is user-facing but does not pass through the Compose Text audit.
                for match in re.finditer(rf'showToast\(context,\s*({STRING})', line):
                    key = decode_expression(match.group(1))
                    if "$" not in key and re.search(r"[A-Za-z]{3}", key) and key not in keys:
                        missing.setdefault(key, []).append(f"{path.relative_to(ROOT)}:{line_no}")
    if missing:
        detail = "\n".join(f"- {key!r}: {', '.join(paths)}" for key, paths in sorted(missing.items()))
        raise ValueError("Untranslated accessibility labels or Vocal Remover toast messages:\n" + detail)

    dynamic = CATALOG.read_text(encoding="utf-8")
    for formatter in (
        'Regex("^Download failed:',
        'Regex("^Could not control the background queue:',
        r'Regex("^(\\d+) MP3s?',
        'Regex("^Android could not play this audio',
        'Regex("^Could not play the audio:',
    ):
        if formatter not in dynamic:
            raise ValueError("Missing Tamil runtime formatter for user-facing dynamic text: " + formatter)


def verify_about_links() -> None:
    about_files = {
        "cleaner": ROOT / "cleaner/src/main/java/com/universalrp/cleansweep/ui/AboutScreen.kt",
        "radio": ROOT / "radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt",
        "equalizer": ROOT / "equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt",
        "vocalremover": ROOT / "vocalremover/src/main/java/com/universalrp/vocalremover/MainActivity.kt",
    }
    for app, path in about_files.items():
        source = path.read_text(encoding="utf-8")
        urls = set(re.findall(r'https://github\.com/universalrp2003/InternetRadio/releases/tag/[^"\\]+', source))
        missing = EXPECTED_RELEASE_URLS - urls
        if missing:
            raise ValueError(f"{app} About is missing release links: {sorted(missing)}")
        if "universalrp2003@gmail.com" not in source or "Arena.ai Agent Mode" not in source or "Project development" not in source:
            raise ValueError(f"{app} About must include the user contact, project-development credit and Arena.ai Agent Mode credit.")
        if re.search(r'\bAuthor\b', source, re.I):
            raise ValueError(f"{app} About still uses the disallowed 'Author' label.")


def main() -> int:
    try:
        keys = catalog_keys()
        checked = sum(verify_visible_text(module, keys) for module in ("radio", "equalizer", "vocalremover", "shared"))
        verify_about_links()
        verify_appstrings_accessibility_and_toasts(keys)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {len(keys)} unique shared Tamil catalog entries; {checked} explicit Compose labels are covered.")
    print("PASS: all four About sections link to the four versioned releases and contain the required contributor credits.")
    print("Source-only check; translated meaning, Kotlin compilation, runtime layout and device behavior still require their own checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
