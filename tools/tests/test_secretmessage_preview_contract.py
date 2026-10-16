#!/usr/bin/env python3
"""Guardrails for the deliberately local-only Secret Message UI preview."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "secretmessage/product"
MANIFEST = MODULE / "src/main/AndroidManifest.xml"
SOURCE_ROOT = MODULE / "src/main/java/com/rpideas/secretmessage/preview"
TEST_ROOT = MODULE / "src/test/java/com/rpideas/secretmessage/preview"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"

# The preview must never gain these through a library; the source manifest removes them explicitly.
REQUIRED_REMOVED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_CONTACTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
}

# Networking, native MLS libraries and hand-written cryptography are out of scope for the preview.
FORBIDDEN_SOURCE_TOKENS = (
    "java.net.",
    "Socket",
    "HttpURLConnection",
    "okhttp",
    "WebSocket",
    "System.loadLibrary",
    "javax.crypto",
    "KeyAgreement",
    "MessageDigest",
    "Cipher.getInstance",
    "SHA256withECDSA",
    "signTranscript",
    "KeyPairGenerator",
    "sharedSecret",
    "System.currentTimeMillis",
    "SharedPreferences.Editor.commit",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def source_files():
    return sorted(SOURCE_ROOT.rglob("*.kt"))


def all_sources() -> str:
    return "\n".join(read(path) for path in source_files())


class SecretMessagePreviewContractTest(unittest.TestCase):
    def test_preview_is_an_included_standalone_module(self):
        settings = read(ROOT / "settings.gradle.kts")
        build = read(MODULE / "build.gradle.kts")
        self.assertIn('include(":secretmessage")', settings)
        self.assertIn('file("secretmessage/product")', settings)
        self.assertIn('applicationId = "com.rpideas.secretmessage.preview"', build)
        self.assertIn('versionName = "0.2.1-preview"', build)
        self.assertIn("versionCode = 3", build)
        self.assertIn("minSdk = 26", build)
        self.assertNotIn("native-mls-rs", build.split("plugins", 1)[1])
        self.assertNotIn("jniLibs", build)

    def test_manifest_requests_no_permissions_and_blocks_backup(self):
        manifest = ET.parse(MANIFEST).getroot()
        removed = set()
        for item in manifest.findall("uses-permission"):
            self.assertEqual("remove", item.get(f"{TOOLS}node"), item.attrib)
            removed.add(item.get(f"{ANDROID}name"))
        self.assertTrue(REQUIRED_REMOVED_PERMISSIONS <= removed, REQUIRED_REMOVED_PERMISSIONS - removed)
        self.assertEqual([], manifest.findall("uses-permission-sdk-23"))
        app = manifest.find("application")
        self.assertIsNotNone(app)
        assert app is not None
        self.assertEqual("false", app.get(f"{ANDROID}allowBackup"))
        self.assertEqual("@xml/data_extraction_rules", app.get(f"{ANDROID}dataExtractionRules"))
        self.assertEqual("@xml/backup_rules", app.get(f"{ANDROID}fullBackupContent"))
        self.assertEqual("Secret Message Preview (NOT SECURE)", app.get(f"{ANDROID}label"))
        activity = app.find("activity")
        assert activity is not None
        self.assertEqual("stateAlwaysHidden", activity.get(f"{ANDROID}windowSoftInputMode"))
        rules = read(MODULE / "src/main/res/xml/data_extraction_rules.xml")
        for section in ("cloud-backup", "device-transfer"):
            self.assertIn(f"<{section}>", rules)
        self.assertIn('domain="sharedpref"', rules)

    def test_activity_discloses_scope_and_uses_screenshot_deterrent_best_effort(self):
        activity = read(SOURCE_ROOT / "MainActivity.kt")
        self.assertIn("FLAG_SECURE", activity)
        self.assertIn("Best-effort screenshot deterrent only", activity)
        ui = all_sources()
        self.assertIn("NOT SECURE · LOCAL PREVIEW ONLY", ui)
        self.assertIn("does not connect to another phone or encrypt messages", ui)
        self.assertIn("Pairing not enabled", ui)
        self.assertIn("It is not a secure messenger.", ui)

    def test_timers_use_elapsed_realtime_and_owner_selected_limits(self):
        policy = read(SOURCE_ROOT / "chat/ChatTimerPolicy.kt")
        self.assertIn("UNDELIVERED_TTL_MILLIS = 30_000L", policy)
        self.assertIn("MIN_VIEW_SECONDS = 60", policy)
        self.assertIn("MAX_VIEW_SECONDS = 300", policy)
        self.assertIn("SystemClock.elapsedRealtime()", read(SOURCE_ROOT / "chat/ChatViewModel.kt"))
        tests = read(TEST_ROOT / "chat/ChatStateMachineTest.kt")
        for name in (
            "sendStartsThirtySecondUndeliveredTimerAndExpiresWithoutDelivery",
            "deliveryAckStopsUndeliveredTimerAndWaitsForView",
            "viewedAckStartsSenderCountdownWithOwnConfiguredDuration",
            "incomingMessageWhileNotViewingWaitsUntilRecipientViews",
            "viewDurationIsClampedBetweenSixtyAndThreeHundredSeconds",
        ):
            self.assertIn(f"fun {name}()", tests)

    def test_only_the_unconnected_transport_is_implemented(self):
        implementations = re.findall(r"class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*SecretMessageMlsTransport", all_sources())
        self.assertEqual(["UnavailableMlsTransport"], implementations)
        transport = read(SOURCE_ROOT / "transport/SecretMessageMlsTransport.kt")
        self.assertIn("override suspend fun sendMlsApplicationMessage(payload: DecryptedChatPayload): Boolean = false", transport)
        self.assertIn("transport: SecretMessageMlsTransport = UnavailableMlsTransport()", read(SOURCE_ROOT / "chat/ChatViewModel.kt"))

    def test_no_network_native_library_or_custom_crypto(self):
        for path in source_files():
            text = read(path)
            for token in FORBIDDEN_SOURCE_TOKENS:
                self.assertNotIn(token, text, f"{token} in {path.relative_to(ROOT)}")

    def test_app_lock_avoids_unsupported_android_9_and_10_combination(self):
        policy = read(SOURCE_ROOT / "security/AppLockPolicy.kt")
        self.assertIn("sdkInt == 28 || sdkInt == 29", policy)
        self.assertIn("BIOMETRIC_WEAK or DEVICE_CREDENTIAL", policy)
        self.assertIn("BIOMETRIC_STRONG or DEVICE_CREDENTIAL", policy)
        build = read(MODULE / "build.gradle.kts")
        self.assertIn('"androidx.biometric:biometric:1.1.0"', build)

    def test_whisperpad_product_modules_are_present(self):
        expected = {
            "ui/CustomInputDeck.kt": ("INLINE_LETTER", "FULL_PICTURE", "valueRange = 2f..18f", "Draw pad"),
            # Owner request after testing 0.2.0 on two phones: a drawing pad about half the screen tall.
            "ui/DrawingPadLayout.kt": ("TARGET_HEIGHT_FRACTION = 0.5f",),
            "audio/BuiltInNotificationSound.kt": ("QUANTUM_SHIMMER",),
            "audio/ToneSynthesizer.kt": ("SAMPLE_RATE = 44_100",),
            "chat/ChatViewModel.kt": ("class ChatViewModel",),
            "model/EphemeralMessage.kt": ("PurgeNowSync", "ViewedAck", "DeliveryAck"),
            "security/AppLockController.kt": ("BiometricPrompt",),
        }
        for relative, tokens in expected.items():
            text = read(SOURCE_ROOT / relative)
            for token in tokens:
                self.assertIn(token, text, f"{token} missing from {relative}")


if __name__ == "__main__":
    unittest.main()
