#!/usr/bin/env python3
"""Decide whether a release needs a recorded physical-device install before publication.

**Owner decision, 12 October 2026 (Live Guard 2.42).** An install-and-verify record is no
longer required for *every* release — only for a release that **adds a dangerous permission**.
Every other honesty rule stays exactly as it was:

  * when a device-evidence file *is* present, publication still uses the tested bytes — a
    rebuild that reproduces the recorded digest, or the recorded run's promoted artifact;
  * a rebuilt digest can never inherit an earlier device test;
  * release notes must never claim a device test that did not happen;
  * Play Protect, package identity and the signing key are Android's and the owner's business,
    never something this repository bypasses, weakens or claims to have satisfied.

The comparison is deliberately made on **built artifacts**, not on the source manifest: the
merged manifest can carry permissions contributed by a library, and only the APK shows what the
phone is actually asked to grant.

This module is pure — it compares two sets of permission names and reports what changed. It
does not read an APK (the workflow does that with ``aapt2 dump badging``) and it cannot decide
anything about Play Protect.

Fail-closed rule: when the previously published release's permissions cannot be determined
(asset missing, download refused, unreadable badging) the answer is "evidence required", never
"assume nothing was added".
"""
from __future__ import annotations

import argparse
import re
import sys

# Android's runtime ("dangerous" protection level) permissions, by group. Anything not listed
# here is normal / signature / appop / development and is NOT a runtime prompt: notably
# MANAGE_EXTERNAL_STORAGE, PACKAGE_USAGE_STATS, SYSTEM_ALERT_WINDOW, REQUEST_INSTALL_PACKAGES
# and QUERY_ALL_PACKAGES are all outside this list on purpose, so an unchanged declaration of
# one of them can never be mistaken for a new dangerous permission.
DANGEROUS_PERMISSIONS: frozenset[str] = frozenset(
    "android.permission." + name
    for name in (
        # CALENDAR
        "READ_CALENDAR", "WRITE_CALENDAR",
        # CALL_LOG
        "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS",
        # CAMERA
        "CAMERA",
        # CONTACTS
        "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS",
        # LOCATION
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
        "ACCESS_MEDIA_LOCATION",
        # MICROPHONE
        "RECORD_AUDIO",
        # NEARBY_DEVICES
        "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES",
        # NOTIFICATIONS (runtime from Android 13)
        "POST_NOTIFICATIONS",
        # PHONE
        "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ADD_VOICEMAIL", "USE_SIP",
        "ANSWER_PHONE_CALLS", "ACCEPT_HANDOVER",
        # SENSORS
        "BODY_SENSORS", "BODY_SENSORS_BACKGROUND",
        # SMS
        "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_WAP_PUSH", "RECEIVE_MMS",
        # STORAGE / READ_MEDIA
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
        "READ_MEDIA_VISUAL_USER_SELECTED",
    )
)

BADGING_PERMISSION = re.compile(r"^uses-permission:\s+name='([^']+)'", re.MULTILINE)
# aapt2 also prints the SDK-versioned variants; they name the same permission.
BADGING_PERMISSION_SDK = re.compile(r"^uses-permission-sdk-(\d+):\s+name='([^']+)'", re.MULTILINE)


def parse_badging(text: str) -> set[str]:
    """Every permission an ``aapt2 dump badging`` report declares, without duplicates."""
    found = set(BADGING_PERMISSION.findall(text))
    found.update(name for _, name in BADGING_PERMISSION_SDK.findall(text))
    return {name for name in found if name}


def dangerous(permissions: set[str]) -> set[str]:
    """Only the runtime permissions — the ones a phone actually asks the owner about."""
    return {name for name in permissions if name in DANGEROUS_PERMISSIONS}


def added(candidate: set[str], previous: set[str]) -> set[str]:
    """Dangerous permissions this build asks for that the published release did not."""
    return dangerous(candidate) - dangerous(previous)


def removed(candidate: set[str], previous: set[str]) -> set[str]:
    """Dangerous permissions the published release asked for and this build no longer does."""
    return dangerous(previous) - dangerous(candidate)


def evidence_required(
    candidate: set[str],
    previous: set[str] | None,
    evidence_present: bool,
) -> tuple[bool, str]:
    """
    Whether a recorded physical-device install must exist before this release is published.

    ``previous is None`` means the previously published release's permissions could not be
    determined, and that fails closed. A present evidence file never *blocks* anything: the
    workflow then publishes exactly the tested bytes, which is the stricter path.
    """
    if evidence_present:
        return False, "a device-evidence record exists; the tested bytes are published as recorded"
    if previous is None:
        return True, ("the previously published release's permissions could not be determined, "
                      "so it cannot be shown that no dangerous permission was added")
    newly = added(candidate, previous)
    if newly:
        return True, "this release adds dangerous permission(s): " + ", ".join(sorted(newly))
    return False, ("no dangerous permission was added against the previously published release "
                   f"({len(dangerous(candidate))} declared, unchanged)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--candidate-badging", required=True,
                        help="file holding `aapt2 dump badging` output for the built release APK")
    parser.add_argument("--previous-badging",
                        help="the same for the previously published release APK")
    parser.add_argument("--previous-unavailable",
                        help="why the previous release could not be read; fails closed")
    parser.add_argument("--evidence-present", action="store_true",
                        help="a device-evidence file exists for this version")
    args = parser.parse_args()

    try:
        candidate = parse_badging(open(args.candidate_badging, encoding="utf-8", errors="replace").read())
    except OSError as error:
        print(f"FAIL: cannot read the candidate badging report: {error}", file=sys.stderr)
        return 2
    if not candidate:
        print("FAIL: the candidate badging report lists no permission at all; refusing to guess.",
              file=sys.stderr)
        return 2

    previous: set[str] | None = None
    if args.previous_badging:
        try:
            previous = parse_badging(open(args.previous_badging, encoding="utf-8", errors="replace").read())
        except OSError as error:
            print(f"FAIL: cannot read the previous release's badging report: {error}", file=sys.stderr)
            return 2
        if not previous:
            previous = None
            print("WARN: the previous release's badging report lists no permission; failing closed.")

    required, reason = evidence_required(candidate, previous, args.evidence_present)
    newly = added(candidate, previous) if previous is not None else set()
    dropped = removed(candidate, previous) if previous is not None else set()

    print(f"CANDIDATE-PERMISSIONS: {len(candidate)}")
    print(f"CANDIDATE-DANGEROUS: {', '.join(sorted(dangerous(candidate))) or 'none'}")
    print(f"ADDED-DANGEROUS: {', '.join(sorted(newly)) or 'none'}")
    print(f"REMOVED-DANGEROUS: {', '.join(sorted(dropped)) or 'none'}")
    print(f"EVIDENCE-PRESENT: {'yes' if args.evidence_present else 'no'}")
    print(f"EVIDENCE-REQUIRED: {'yes' if required else 'no'}")
    print(f"REASON: {reason}")
    print("A 'no' here means no dangerous permission was added. It is NOT Play Protect approval, "
          "NOT a device installation test and NOT all-device certification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
