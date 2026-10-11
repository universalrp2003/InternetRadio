#!/usr/bin/env python3
"""Reject any retired Live Guard notification-reader capability.

Checks, in order:
  1. an Android manifest (the source manifest by default, or a real merged manifest),
  2. the production source tree (retired files, retired symbols, archive not compiled),
  3. optional DEX blobs taken out of a built debug/release APK.

Success here means "this artifact declares no notification-listener / SMS / Contacts /
call-log / Accessibility / device-admin capability and contains no retired reader code".
It does NOT mean Google Play Protect approves the build, that installation succeeds on a
device, or that any remaining permission is acceptable to Google or to a reviewer.
"""
from __future__ import annotations

from pathlib import Path
import argparse
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

DEFAULT_MANIFEST = ROOT / "cleaner/src/main/AndroidManifest.xml"
PRODUCTION_ROOT = ROOT / "cleaner/src/main/java/com/universalrp/cleansweep"
ARCHIVE_ROOT = ROOT / "docs/archived-notification-reader-2.39"
TEST_ROOT = ROOT / "cleaner/src/test/java/com/universalrp/cleansweep"

# Capabilities the core app must never declare again.
RETIRED_MANIFEST_SIGNATURES = (
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "android.service.notification.NotificationListenerService",
    ".notifications.MessageReaderService",
    "MessageReaderService",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.accessibilityservice.AccessibilityService",
    "android.permission.BIND_DEVICE_ADMIN",
    "android.app.action.DEVICE_ADMIN_ENABLED",
)

# Class/type names that must not exist in production sources or in a shipped DEX.
RETIRED_SYMBOLS = (
    "MessageReaderService",
    "LocalMessageTranslator",
    "NotificationReaderPrefs",
    "NotificationReaderScreen",
    "NotificationReadingPolicy",
)

RETIRED_PRODUCTION_FILES = (
    "notifications/MessageReaderService.kt",
    "notifications/LocalMessageTranslator.kt",
    "notifications/NotificationReaderPrefs.kt",
    "notifications/NotificationReadingPolicy.kt",
    "ui/NotificationReaderScreen.kt",
)

# The one retired file that legitimately survives, in TEST sources only.
TEST_ONLY_FILES = ("notifications/NotificationReadingPolicy.kt",)


def _flatten(element: ET.Element) -> str:
    """Every attribute value and text node of the tree, for signature matching."""
    parts: list[str] = []
    for node in element.iter():
        for key, value in node.attrib.items():
            parts.append(f"{key}={value}")
        if node.text:
            parts.append(node.text)
    return "\n".join(parts)


def manifest_problems(manifest: ET.Element, label: str = "manifest") -> list[str]:
    problems: list[str] = []
    text = _flatten(manifest)
    for signature in RETIRED_MANIFEST_SIGNATURES:
        if signature in text:
            problems.append(f"{label}: retired capability signature present: {signature}")

    permissions = {node.get(ANDROID + "name") for node in manifest.findall("uses-permission")}
    # Own alerts and the verified updater must stay: this is a removal, not a purge.
    for required in ("android.permission.POST_NOTIFICATIONS", "android.permission.REQUEST_INSTALL_PACKAGES"):
        if required not in permissions:
            problems.append(f"{label}: required retained permission is missing: {required}")
    for retired in ("android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG"):
        if retired in permissions:
            problems.append(f"{label}: retired permission declared: {retired}")

    application = manifest.find("application")
    if application is None:
        problems.append(f"{label}: no <application> element")
        return problems
    for service in application.findall("service"):
        name = service.get(ANDROID + "name") or ""
        if "notification" in name.lower() or "MessageReader" in name:
            problems.append(f"{label}: notification service declared: {name}")

    # The source manifest uses the "${applicationId}.updates.files" placeholder; a real merged
    # manifest has it substituted (com.universalrp.cleansweep.updates.files), so match the suffix.
    providers = [node for node in application.findall("provider")
                 if (node.get(ANDROID + "authorities") or "").endswith(".updates.files")]
    if not providers:
        problems.append(f"{label}: verified-update FileProvider is missing")
    elif providers[0].get(ANDROID + "exported") != "false":
        problems.append(f"{label}: verified-update FileProvider must not be exported")
    return problems


def dex_problems(blobs: dict[str, bytes]) -> list[str]:
    """Retired classes inside a compiled APK's DEX. JVM type names are stored as plain text."""
    problems: list[str] = []
    for name, data in blobs.items():
        for symbol in RETIRED_SYMBOLS:
            if symbol.encode("utf-8") in data:
                problems.append(f"{name}: retired reader class found inside compiled DEX: {symbol}")
    return problems


def source_problems(production_root: Path = PRODUCTION_ROOT,
                    archive_root: Path = ARCHIVE_ROOT,
                    test_root: Path = TEST_ROOT) -> list[str]:
    problems: list[str] = []
    for relative in RETIRED_PRODUCTION_FILES:
        if (production_root / relative).exists():
            problems.append(f"production source still contains retired file: {relative}")
    for path in sorted(production_root.rglob("*.kt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for symbol in RETIRED_SYMBOLS:
            if symbol in text:
                problems.append(f"{path.relative_to(production_root)} still references retired symbol: {symbol}")
    for relative in TEST_ONLY_FILES:
        if not (test_root / relative).is_file():
            problems.append(f"test-only historical policy file is missing: {relative}")
    # The archive is historical reference only: it must exist and must not be a source set.
    if not (archive_root / "README.md").is_file():
        problems.append("docs/archived-notification-reader-2.39/README.md is missing")
    archived = [path.name for path in archive_root.rglob("*.kt")]
    if not archived:
        problems.append("the archived 2.39 reader sources are missing from the archive directory")
    build_scripts = [ROOT / "cleaner/build.gradle.kts", ROOT / "settings.gradle.kts"]
    for script in build_scripts:
        text = script.read_text(encoding="utf-8")
        if "archived-notification-reader" in text:
            problems.append(f"{script.name} references the historical archive; it must never be compiled")
        for dependency in ("com.google.mlkit:translate", "com.google.mlkit:language-id"):
            if dependency in text:
                problems.append(f"{script.name} still declares the removed reader dependency: {dependency}")
    return problems


def verify(manifest_path: Path = DEFAULT_MANIFEST,
           dex_blobs: dict[str, bytes] | None = None,
           production_root: Path = PRODUCTION_ROOT,
           archive_root: Path = ARCHIVE_ROOT,
           test_root: Path = TEST_ROOT) -> list[str]:
    label = str(manifest_path.relative_to(ROOT)) if str(manifest_path).startswith(str(ROOT)) else str(manifest_path)
    problems = manifest_problems(ET.parse(manifest_path).getroot(), label)
    problems += source_problems(production_root, archive_root, test_root)
    problems += dex_problems(dex_blobs or {})
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="Reject retired Live Guard notification-reader capability.")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST, help="source or merged AndroidManifest.xml")
    parser.add_argument("--dex", type=Path, action="append", default=[], help="DEX file extracted from a built APK (repeatable)")
    parser.add_argument("--skip-source", action="store_true", help="skip the production-source checks (merged-manifest/APK pass)")
    args = parser.parse_args()

    blobs = {path.name: path.read_bytes() for path in args.dex}
    try:
        problems = manifest_problems(ET.parse(args.manifest).getroot(), str(args.manifest))
        if not args.skip_source:
            problems += source_problems()
        problems += dex_problems(blobs)
    except (OSError, ET.ParseError) as error:
        raise SystemExit(f"FAIL: {error}")
    if problems:
        for problem in problems:
            print(f"FAIL: {problem}", file=sys.stderr)
        return 1
    print(f"PASS: {args.manifest} declares no notification-listener, SMS, Contacts, call-log, Accessibility or device-admin capability.")
    if not args.skip_source:
        print("PASS: production sources contain no retired reader file/symbol; the 2.39 archive is reference-only and not compiled.")
    if blobs:
        print(f"PASS: no retired reader class inside {len(blobs)} compiled DEX file(s): {', '.join(sorted(blobs))}.")
    print("Source/static checks only: not Play Protect approval, not a device installation test and not Google review.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
