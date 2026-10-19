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
EXPECTED_OOR_RELEASE_URLS = {
    "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46",
    "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11",
    "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5",
    "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11",
    "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.1.0",
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


def verify_oor_bilingual_catalog() -> int:
    path = ROOT / "oorarivippu/src/main/java/com/oorarivippu/voiceannouncer/BilingualStrings.kt"
    source = path.read_text(encoding="utf-8")
    data_class = source.split("data class UiStrings(", 1)[1].split("\n)", 1)[0]
    expected_fields = set(re.findall(r"\bval\s+(\w+)\s*:\s*String", data_class))
    if not expected_fields:
        raise ValueError("Oor Arivippu UiStrings must declare its bilingual fields.")

    for language in ("TAMIL", "ENGLISH"):
        marker = f"val {language} = UiStrings("
        if marker not in source:
            raise ValueError(f"Oor Arivippu is missing the {language} string catalog.")
        body = source.split(marker, 1)[1].split("\n    )", 1)[0]
        assignments = re.findall(r"^\s*(\w+)\s*=\s*(.+?)\s*,?\s*$", body, re.M)
        counts = Counter(name for name, _ in assignments)
        duplicates = sorted(name for name, count in counts.items() if count > 1)
        missing = expected_fields - set(counts)
        extra = set(counts) - expected_fields
        if duplicates or missing or extra:
            raise ValueError(
                f"Oor Arivippu {language} catalog mismatch: missing={sorted(missing)}, "
                f"extra={sorted(extra)}, duplicates={duplicates}"
            )
        for name, expression in assignments:
            if not expression.strip() or expression.strip() in {'""', "\"\"\"\"\"\""}:
                raise ValueError(f"Oor Arivippu {language}.{name} is empty.")

    main_source = (ROOT / "oorarivippu/src/main/java/com/oorarivippu/voiceannouncer/MainActivity.kt").read_text(encoding="utf-8")
    referenced_fields = set(re.findall(r"(?<![.\w])ui\.(\w+)\b", main_source))
    unknown_fields = referenced_fields - expected_fields
    if unknown_fields:
        raise ValueError(f"Oor Arivippu UI references missing bilingual fields: {sorted(unknown_fields)}")
    return len(expected_fields)


def verify_about_links() -> None:
    about_files = {
        "cleaner": ROOT / "cleaner/src/main/java/com/universalrp/cleansweep/ui/AboutScreen.kt",
        "radio": ROOT / "radio/src/main/java/com/universalrp/tamilnadufm/ui/PlayScreens.kt",
        "equalizer": ROOT / "equalizer/src/main/java/com/universalrp/pulseeq/ui/AboutScreen.kt",
        "vocalremover": ROOT / "vocalremover/src/main/java/com/universalrp/vocalremover/MainActivity.kt",
    }
    sister_apps_path = ROOT / "shared/src/main/java/com/universalrp/updates/SisterApps.kt"
    sister_apps_source = sister_apps_path.read_text(encoding="utf-8") if sister_apps_path.exists() else ""
    shared_links = "fun releaseLinks()" in sister_apps_source
    if shared_links:
        expected_sister_tags = (
            'tagPrefix = "live-guard"', 'version = "2.46"',
            'tagPrefix = "ramesh-radio"', 'version = "1.11"',
            'tagPrefix = "pulseeq"', 'version = "1.5"',
            'tagPrefix = "rp-vocal-remover-studio"', 'version = "0.1.11"',
            'tagPrefix = "oor-arivippu"', 'version = "1.1.0"',
            'tagPrefix = "secret-message"', 'version = "1.1.0"',
        )
        missing_tags = [tag for tag in expected_sister_tags if tag not in sister_apps_source]
        if missing_tags:
            raise ValueError(f"Shared sister-app list is missing current app/version entries: {missing_tags}")

    for app, path in about_files.items():
        source = path.read_text(encoding="utf-8")
        if shared_links:
            if "com.universalrp.updates.SisterApps.releaseLinks()" not in source:
                raise ValueError(f"{app} About does not render the shared six-app release list")
        else:
            urls = set(re.findall(r'https://github\.com/universalrp2003/InternetRadio/releases/tag/[^"\\]+', source))
            missing = EXPECTED_RELEASE_URLS - urls
            if missing:
                raise ValueError(f"{app} About is missing release links: {sorted(missing)}")
        if "universalrp2003@gmail.com" not in source or "Arena.ai Agent Mode" not in source or "Project development" not in source:
            raise ValueError(f"{app} About must include the user contact, project-development credit and Arena.ai Agent Mode credit.")
        if re.search(r'\bAuthor\b', source, re.I):
            raise ValueError(f"{app} About still uses the disallowed 'Author' label.")

    oor_path = ROOT / "oorarivippu/src/main/java/com/oorarivippu/voiceannouncer/MainActivity.kt"
    oor_source = oor_path.read_text(encoding="utf-8")
    oor_strings = (ROOT / "oorarivippu/src/main/java/com/oorarivippu/voiceannouncer/BilingualStrings.kt").read_text(encoding="utf-8")
    oor_about_text = oor_source + "\n" + oor_strings
    oor_urls = set(re.findall(r'https://github\.com/universalrp2003/RP-Android-Apps/releases/tag/[^"\\]+', oor_source))
    missing_oor = EXPECTED_OOR_RELEASE_URLS - oor_urls
    if missing_oor:
        raise ValueError(f"Oor Arivippu About is missing current release links: {sorted(missing_oor)}")
    for required in (
        "https://github.com/universalrp2003/InternetRadio",
        "https://github.com/universalrp2003/RP-Android-Apps",
        "universalrp2003@gmail.com",
        "Arena.ai Agent Mode",
        "Project development",
        'AppUpdates.Control("Oor-Arivippu")',
        'AppUpdates.Control("Oor-Arivippu", automatic = true)',
    ):
        if required not in oor_about_text:
            raise ValueError(f"Oor Arivippu About/updater is missing required content: {required}")
    if re.search(r'\bAuthor\b', oor_about_text, re.I):
        raise ValueError("Oor Arivippu About still uses the disallowed 'Author' label.")


def main() -> int:
    try:
        keys = catalog_keys()
        checked = sum(verify_visible_text(module, keys) for module in ("radio", "equalizer", "vocalremover", "shared"))
        oor_fields = verify_oor_bilingual_catalog()
        verify_about_links()
        verify_appstrings_accessibility_and_toasts(keys)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {len(keys)} unique shared Tamil catalog entries; {checked} explicit Compose labels are covered.")
    print(f"PASS: all {oor_fields} Oor Arivippu UI fields have Tamil and English strings and are referenced consistently.")
    print("PASS: all four legacy About sections and the Oor Arivippu About/update screen have their required release links and contributor credits.")
    print("Source-only check; translated meaning, Kotlin compilation, runtime layout and device behavior still require their own checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
