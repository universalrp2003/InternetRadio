#!/usr/bin/env python3
"""Unit tests for the 2.42 "dangerous permission added" publication gate."""
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import verify_release_permission_delta as delta  # noqa: E402

BADGING = """package: name='com.universalrp.cleansweep' versionCode='47' versionName='2.42'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.MANAGE_EXTERNAL_STORAGE'
uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'
uses-permission: name='android.permission.SYSTEM_ALERT_WINDOW'
uses-permission: name='android.permission.QUERY_ALL_PACKAGES'
uses-permission: name='android.permission.PACKAGE_USAGE_STATS'
uses-permission-sdk-23: name='android.permission.READ_EXTERNAL_STORAGE'
uses-permission-sdk-23: name='android.permission.WRITE_EXTERNAL_STORAGE'
uses-permission: name='android.permission.ACCESS_FINE_LOCATION'
uses-permission: name='android.permission.NEARBY_WIFI_DEVICES'
uses-permission: name='android.permission.READ_PHONE_STATE'
"""


class PermissionDeltaTest(unittest.TestCase):

    def test_badging_parsing_includes_sdk_versioned_lines(self):
        permissions = delta.parse_badging(BADGING)
        self.assertIn("android.permission.POST_NOTIFICATIONS", permissions)
        self.assertIn("android.permission.READ_EXTERNAL_STORAGE", permissions)
        self.assertIn("android.permission.WRITE_EXTERNAL_STORAGE", permissions)
        self.assertEqual(12, len(permissions))
        self.assertEqual(set(), delta.parse_badging("no permissions here\n"))

    def test_appop_and_signature_permissions_are_not_called_dangerous(self):
        """
        The app's most sensitive declarations are appop/signature level, not runtime prompts.
        Misclassifying them would demand a device install for a release that changed nothing.
        """
        permissions = delta.parse_badging(BADGING)
        dangerous = delta.dangerous(permissions)
        for not_runtime in ("MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES",
                            "SYSTEM_ALERT_WINDOW", "QUERY_ALL_PACKAGES",
                            "PACKAGE_USAGE_STATS", "INTERNET"):
            self.assertNotIn("android.permission." + not_runtime, dangerous)
        for runtime in ("POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION", "NEARBY_WIFI_DEVICES",
                        "READ_PHONE_STATE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"):
            self.assertIn("android.permission." + runtime, dangerous)
        self.assertEqual(6, len(dangerous))

    def test_an_unchanged_permission_set_needs_no_device_evidence(self):
        permissions = delta.parse_badging(BADGING)
        required, reason = delta.evidence_required(permissions, set(permissions), evidence_present=False)
        self.assertFalse(required)
        self.assertIn("no dangerous permission was added", reason)
        self.assertEqual(set(), delta.added(permissions, permissions))

    def test_an_added_dangerous_permission_requires_a_recorded_device_install(self):
        previous = delta.parse_badging(BADGING)
        candidate = previous | {"android.permission.READ_SMS", "android.permission.CAMERA"}
        required, reason = delta.evidence_required(candidate, previous, evidence_present=False)
        self.assertTrue(required)
        self.assertIn("android.permission.CAMERA", reason)
        self.assertIn("android.permission.READ_SMS", reason)
        self.assertEqual({"android.permission.READ_SMS", "android.permission.CAMERA"},
                         delta.added(candidate, previous))

    def test_an_unknown_previous_release_fails_closed(self):
        candidate = delta.parse_badging(BADGING)
        required, reason = delta.evidence_required(candidate, None, evidence_present=False)
        self.assertTrue(required)
        self.assertIn("could not be determined", reason)

    def test_a_recorded_device_test_is_never_blocked_by_this_gate(self):
        """The evidence path is the stricter one: it publishes exactly the tested bytes."""
        candidate = delta.parse_badging(BADGING) | {"android.permission.CAMERA"}
        required, reason = delta.evidence_required(candidate, None, evidence_present=True)
        self.assertFalse(required)
        self.assertIn("tested bytes", reason)

    def test_a_removed_permission_is_reported_but_never_blocks(self):
        previous = delta.parse_badging(BADGING) | {"android.permission.READ_CONTACTS"}
        candidate = delta.parse_badging(BADGING)
        self.assertEqual({"android.permission.READ_CONTACTS"}, delta.removed(candidate, previous))
        self.assertEqual(set(), delta.added(candidate, previous))
        required, _ = delta.evidence_required(candidate, previous, evidence_present=False)
        self.assertFalse(required)

    def test_a_newly_added_appop_permission_is_not_treated_as_dangerous(self):
        """Adding MANAGE_EXTERNAL_STORAGE is a policy question, not a runtime-prompt question."""
        previous = delta.parse_badging(BADGING) - {"android.permission.MANAGE_EXTERNAL_STORAGE"}
        candidate = delta.parse_badging(BADGING)
        self.assertEqual(set(), delta.added(candidate, previous))
        required, _ = delta.evidence_required(candidate, previous, evidence_present=False)
        self.assertFalse(required)


if __name__ == "__main__":
    unittest.main()
