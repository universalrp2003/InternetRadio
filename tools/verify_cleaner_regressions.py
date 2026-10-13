#!/usr/bin/env python3
"""Fail publication if a required CleanSweep regression suite was missing or skipped."""
from pathlib import Path
import os
import sys
import xml.etree.ElementTree as ET

EXPECTED = {
    "com.universalrp.cleansweep.notify.StatusPillContentTest": 7,
    "com.universalrp.cleansweep.data.SecuritySettingsRoutesTest": 14,
    "com.universalrp.cleansweep.ai.SecurityAiPromptTest": 9,
    "com.universalrp.cleansweep.data.ReadingPolicyTest": 10,
    "com.universalrp.cleansweep.data.PhoneAgeEstimatorTest": 12,
    "com.universalrp.cleansweep.data.NewsPolicyTest": 14,
    "com.universalrp.cleansweep.data.BatteryValuePolicyTest": 11,
    "com.universalrp.cleansweep.ai.AssistantEvidencePolicyTest": 10,
    "com.universalrp.cleansweep.widget.WidgetReadingPolicyTest": 12,
    "com.universalrp.cleansweep.voice.VoiceDeliveryPolicyTest": 12,
    "com.universalrp.cleansweep.data.CompatibilityReportTest": 4,
    "com.universalrp.cleansweep.data.SecurityHistoryPolicyTest": 12,
    "com.universalrp.cleansweep.data.ChargeHistoryPolicyTest": 14,
    "com.universalrp.cleansweep.data.DataBudgetPolicyTest": 14,
    "com.universalrp.cleansweep.data.MobileCounterPolicyTest": 8,
    "com.universalrp.cleansweep.data.PermissionObservationPolicyTest": 6,
    "com.universalrp.cleansweep.data.InsightsJsonTest": 6,
    "com.universalrp.cleansweep.ai.ActionChecklistTest": 12,
    "com.universalrp.cleansweep.ai.AiRequestPolicyTest": 8,
    "com.universalrp.cleansweep.ai.ProductEvidencePolicyTest": 12,
    "com.universalrp.cleansweep.ai.AssistantModePolicyTest": 6,
    "com.universalrp.cleansweep.ai.AiFailurePolicyTest": 8,
    "com.universalrp.cleansweep.ai.AiSecretStorageTest": 8,
    "com.universalrp.cleansweep.data.DisplayTimeTest": 8,
    "com.universalrp.cleansweep.data.PermissionReviewSummaryTest": 8,
    "com.universalrp.cleansweep.data.LocalizedTextTest": 4,
    "com.universalrp.cleansweep.voice.AnswerVoicePolicyTest": 12,
    "com.universalrp.cleansweep.updates.UpdatePolicyTest": 14,
    # The reader itself is gone from production (2.40 core removal). Its historical pure
    # policy now lives in the TEST source set, so these 12 cases still run.
    "com.universalrp.cleansweep.notifications.NotificationReadingPolicyTest": 12,
    "com.universalrp.cleansweep.data.CorePrivacyMigrationPolicyTest": 6,
    "com.universalrp.cleansweep.data.LastUnplugPolicyTest": 8,
    "com.universalrp.cleansweep.widget.CompactWidgetPolicyTest": 8,
    "com.universalrp.cleansweep.data.FeatureSetupPolicyTest": 8,
    # 2.41: honest battery-side watts/amps, the owner-set 44 °C warning and the AI
    # output-budget + provider-model-check policies.
    "com.universalrp.cleansweep.data.ChargingHonestyPolicyTest": 9,
    "com.universalrp.cleansweep.data.ThermalWarningPolicyTest": 6,
    "com.universalrp.cleansweep.ai.AiOutputBudgetPolicyTest": 6,
    # 2.42: the battery-current source fallback, automatic answer voices, the default-on
    # monitors, one language source of truth and the home-screen spacing scale.
    "com.universalrp.cleansweep.data.LanguageSourcePolicyTest": 6,
    "com.universalrp.cleansweep.data.MonitorDefaultsPolicyTest": 6,
    "com.universalrp.cleansweep.ui.HomeLayoutPolicyTest": 5,
    # Follow-up review: observed charging ETA, full-content duplicate confirmation, live
    # socket-vs-historical network count, and AI secret redaction/storage codec.
    "com.universalrp.cleansweep.data.ChargingEtaPolicyTest": 5,
    "com.universalrp.cleansweep.data.DuplicateContentHashTest": 3,
    "com.universalrp.cleansweep.data.AppNetworkTrackerReportTest": 2,
}


def verify(results_root: Path) -> list[str]:
    summaries = []
    for variant in ("Debug", "Release"):
        directory = results_root / f"test{variant}UnitTest"
        reports = [ET.parse(path).getroot() for path in sorted(directory.glob("TEST-*.xml"))]
        if not reports:
            raise ValueError(f"No {variant} unit-test XML reports in {directory}")
        suites = {report.get("name"): report for report in reports}
        for name, expected in EXPECTED.items():
            suite = suites.get(name)
            if suite is None:
                raise ValueError(f"{variant}: required suite {name} did not run")
            actual = int(suite.get("tests", "0"))
            if actual != expected or len(suite.findall("testcase")) != expected:
                raise ValueError(f"{variant}: {name} ran {actual}, expected {expected} tests")
            for field in ("failures", "errors", "skipped"):
                if int(suite.get(field, "0")) != 0:
                    raise ValueError(f"{variant}: {name} has nonzero {field}")
            if any(case.find("skipped") is not None for case in suite.findall("testcase")):
                raise ValueError(f"{variant}: {name} contains a skipped test case")
        if any(int(report.get(field, "0")) for report in reports for field in ("failures", "errors")):
            raise ValueError(f"{variant}: an existing CleanSweep test failed")
        total = sum(int(report.get("tests", "0")) for report in reports)
        summaries.append(f"CleanSweep {variant.lower()}: {total} tests passed; all {sum(EXPECTED.values())} required regression tests ran, none skipped.")
    return summaries


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("cleaner/build/test-results")
    try:
        summaries = verify(root)
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    for summary in summaries:
        print(f"PASS: {summary}")
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as stream:
            stream.write("## CleanSweep regression validation\n\n")
            stream.writelines(f"- {summary}\n" for summary in summaries)
            stream.write("\nPure JVM tests only; OEM settings and overlay rendering still require physical-device validation.\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
