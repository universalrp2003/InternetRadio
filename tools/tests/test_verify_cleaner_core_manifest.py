#!/usr/bin/env python3
"""Unit tests for the retired-notification-reader manifest/source/DEX validator."""
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import verify_cleaner_core_manifest as core  # noqa: E402

BASE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.universalrp.cleansweep">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <application android:label="Live Guard">
        <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.updates.files"
            android:exported="false" android:grantUriPermissions="true" />
    </application>
</manifest>
"""


def manifest(xml: str) -> ET.Element:
    return ET.fromstring(xml)


def problems(xml: str) -> list[str]:
    return core.manifest_problems(manifest(xml))


class CoreManifestTest(unittest.TestCase):

    def test_shipped_source_manifest_has_no_retired_capability(self):
        self.assertEqual(core.manifest_problems(ET.parse(core.DEFAULT_MANIFEST).getroot(), "source"), [])
        self.assertEqual(core.source_problems(), [])

    def test_notification_listener_service_is_rejected(self):
        xml = BASE.replace(
            "</application>",
            '  <service android:name=".notifications.MessageReaderService" android:exported="true"'
            ' android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">'
            '<intent-filter><action android:name="android.service.notification.NotificationListenerService" />'
            "</intent-filter></service></application>")
        found = problems(xml)
        self.assertTrue(any("retired capability signature" in item for item in found), found)
        self.assertTrue(any("notification service declared" in item for item in found), found)

    def test_sms_contacts_and_call_log_permissions_are_rejected(self):
        for permission in ("READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CONTACTS", "READ_CALL_LOG", "WRITE_CALL_LOG"):
            xml = BASE.replace("</manifest>", f'<uses-permission android:name="android.permission.{permission}" /></manifest>')
            found = problems(xml)
            self.assertTrue(any(permission in item for item in found), (permission, found))

    def test_accessibility_service_is_rejected(self):
        xml = BASE.replace(
            "</application>",
            '<service android:name=".AutoTapper" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">'
            '<intent-filter><action android:name="android.accessibilityservice.AccessibilityService" />'
            "</intent-filter></service></application>")
        self.assertTrue(any("BIND_ACCESSIBILITY_SERVICE" in item or "accessibilityservice" in item for item in problems(xml)))

    def test_device_admin_binding_is_rejected(self):
        xml = BASE.replace(
            "</application>",
            '<receiver android:name=".AdminReceiver" android:permission="android.permission.BIND_DEVICE_ADMIN">'
            '<meta-data android:name="android.app.device_admin" android:resource="@xml/admin" />'
            "</receiver></application>")
        self.assertTrue(any("BIND_DEVICE_ADMIN" in item for item in problems(xml)))

    def test_own_alert_permission_and_update_provider_stay_required(self):
        without_notifications = BASE.replace('<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />', "")
        self.assertTrue(any("POST_NOTIFICATIONS" in item for item in problems(without_notifications)))
        exported_provider = BASE.replace('android:exported="false"', 'android:exported="true"')
        self.assertTrue(any("FileProvider must not be exported" in item for item in problems(exported_provider)))
        no_provider = BASE.replace("</application>", "</application>").replace(
            '<provider android:name="androidx.core.content.FileProvider"\n            android:authorities="${applicationId}.updates.files"\n            android:exported="false" android:grantUriPermissions="true" />', "")
        self.assertTrue(any("FileProvider is missing" in item for item in problems(no_provider)))
        # A real merged manifest has ${applicationId} substituted; that must still pass.
        merged = BASE.replace("${applicationId}.updates.files", "com.universalrp.cleansweep.updates.files")
        self.assertEqual(problems(merged), [])

    def test_retired_classes_are_rejected_in_compiled_dex(self):
        clean = {"cleaner.dex": b"\x00\x01\x02com/universalrp/cleansweep/MainActivity\x03"}
        self.assertEqual(core.dex_problems(clean), [])
        dirty = {"cleaner.dex": b"Lcom/universalrp/cleansweep/notifications/MessageReaderService;"}
        self.assertTrue(any("MessageReaderService" in item for item in core.dex_problems(dirty)))

    def test_production_tree_and_archive_discipline(self):
        with tempfile.TemporaryDirectory() as tmp:
            production = Path(tmp) / "production"
            (production / "notifications").mkdir(parents=True)
            (production / "MainActivity.kt").write_text("class MainActivity\n", encoding="utf-8")
            archive = Path(tmp) / "archive"
            (archive / "production-sources/notifications").mkdir(parents=True)
            (archive / "README.md").write_text("reference only\n", encoding="utf-8")
            (archive / "production-sources/notifications/MessageReaderService.kt").write_text("archived\n", encoding="utf-8")
            test_root = Path(tmp) / "test/notifications"
            test_root.mkdir(parents=True)
            (test_root / "NotificationReadingPolicy.kt").write_text("object NotificationReadingPolicy\n", encoding="utf-8")

            self.assertEqual(core.source_problems(production, archive, Path(tmp) / "test"), [])

            (production / "notifications/NotificationReaderPrefs.kt").write_text("object NotificationReaderPrefs\n", encoding="utf-8")
            (production / "MainActivity.kt").write_text("class MainActivity { val x = NotificationReadingPolicy }\n", encoding="utf-8")
            found = core.source_problems(production, archive, Path(tmp) / "test")
            self.assertTrue(any("retired file: notifications/NotificationReaderPrefs.kt" in item for item in found), found)
            self.assertTrue(any("retired symbol: NotificationReadingPolicy" in item for item in found), found)


if __name__ == "__main__":
    unittest.main()
