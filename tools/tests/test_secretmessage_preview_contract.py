#!/usr/bin/env python3
"""Guardrails for the deliberately local-only Secret Message UI preview."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "secretmessage/product/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SecretMessagePreviewContractTest(unittest.TestCase):
    def test_preview_is_an_included_standalone_module(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        build = (ROOT / "secretmessage/product/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('include(":secretmessage")', settings)
        self.assertIn('file("secretmessage/product")', settings)
        self.assertIn('applicationId = "com.rpideas.secretmessage.preview"', build)
        self.assertIn('versionName = "0.1.0-preview"', build)
        self.assertIn("minSdk = 26", build)

    def test_manifest_requests_no_permissions_and_disables_backup(self):
        manifest = ET.parse(MANIFEST).getroot()
        permissions = manifest.findall("uses-permission")
        self.assertEqual([], permissions, [item.attrib for item in permissions])
        app = manifest.find("application")
        self.assertIsNotNone(app)
        assert app is not None
        self.assertEqual("false", app.get(f"{ANDROID}allowBackup"))
        self.assertEqual("Secret Message Preview (NOT SECURE)", app.get(f"{ANDROID}label"))

    def test_activity_discloses_scope_and_uses_screenshot_deterrent_best_effort(self):
        source = (
            ROOT
            / "secretmessage/product/src/main/java/com/rpideas/secretmessage/preview/MainActivity.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("FLAG_SECURE", source)
        self.assertIn("Best-effort screenshot deterrent only", source)
        self.assertIn("NOT SECURE · LOCAL PREVIEW ONLY", source)
        self.assertIn("does not connect to another phone or encrypt messages", source)
        self.assertIn("Pairing not enabled", source)

    def test_preview_expiry_is_elapsed_realtime_policy_not_wall_clock(self):
        policy = (
            ROOT
            / "secretmessage/product/src/main/java/com/rpideas/secretmessage/preview/EphemeralMessagePolicy.kt"
        ).read_text(encoding="utf-8")
        tests = (
            ROOT
            / "secretmessage/product/src/test/java/com/rpideas/secretmessage/preview/EphemeralMessagePolicyTest.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("PREVIEW_MESSAGE_TTL_MILLIS = 60_000L", policy)
        self.assertIn("expiresAtElapsedRealtime", policy)
        self.assertNotIn("System.currentTimeMillis", policy)
        self.assertIn("previewTtlIsSixtySecondsFromLocalSend", tests)
        self.assertIn("visibleMessagesExcludesExpiredItems", tests)


if __name__ == "__main__":
    unittest.main()
