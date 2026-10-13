#!/usr/bin/env python3
"""Require Vocal Remover restart/source-access/export regressions in both Android test variants."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "com.universalrp.vocalremover.processing.RecoveryReliabilityTest": 3,
    "com.universalrp.vocalremover.processing.WavStitcherTest": 6,
}


def verify_sources() -> None:
    test_root = ROOT / "vocalremover/src/test/java"
    for name, expected in EXPECTED.items():
        path = test_root.joinpath(*name.split(".")).with_suffix(".kt")
        if not path.is_file():
            raise ValueError(f"Required Vocal Remover test source is missing: {name}")
        actual = len(re.findall(r"@Test\b", path.read_text(encoding="utf-8")))
        if actual != expected:
            raise ValueError(f"{name} declares {actual} tests; expected {expected}")


def verify_results() -> list[str]:
    result_root = ROOT / "vocalremover/build/test-results"
    summaries = []
    for variant in ("Debug", "Release"):
        directory = result_root / f"test{variant}UnitTest"
        reports = [ET.parse(path).getroot() for path in sorted(directory.glob("TEST-*.xml"))]
        if not reports:
            raise ValueError(f"No {variant} Vocal Remover test reports in {directory}")
        suites = {report.get("name"): report for report in reports}
        for name, expected in EXPECTED.items():
            suite = suites.get(name)
            if suite is None:
                raise ValueError(f"{variant}: required reliability suite {name} did not run")
            if int(suite.get("tests", "0")) != expected or len(suite.findall("testcase")) != expected:
                raise ValueError(f"{variant}: {name} did not run all {expected} required tests")
            if any(int(suite.get(field, "0")) for field in ("failures", "errors", "skipped")):
                raise ValueError(f"{variant}: {name} has a failure, error, or skipped test")
        if any(int(report.get(field, "0")) for report in reports for field in ("failures", "errors")):
            raise ValueError(f"{variant}: a Vocal Remover unit test failed")
        summaries.append(f"Vocal Remover {variant.lower()}: restart/source-access/export reliability tests passed.")
    return summaries


def main() -> int:
    try:
        verify_sources()
        if "--source-only" in sys.argv:
            print("PASS: source contains 3 recovery/source-access and 6 WAV export/cleanup tests.")
            print("Source-only; no JVM tests were executed.")
            return 0
        for summary in verify_results():
            print(f"PASS: {summary}")
        return 0
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
