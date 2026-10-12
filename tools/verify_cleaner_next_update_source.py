#!/usr/bin/env python3
"""Static source contracts only. Does NOT build Android, execute JVM tests or certify a phone."""
from pathlib import Path
import re
import runpy
import xml.etree.ElementTree as ET

import verify_cleaner_core_manifest as core

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'cleaner/src/main/java/com/universalrp/cleansweep'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def source(path):
    return (JAVA / path).read_text(encoding='utf-8')


def require(condition, description):
    if not condition:
        raise ValueError(description)


def main():
    manifest = ET.parse(ROOT / 'cleaner/src/main/AndroidManifest.xml').getroot()
    permissions = {node.get(ANDROID + 'name') for node in manifest.findall('uses-permission')}
    require('android.permission.REQUEST_INSTALL_PACKAGES' in permissions, 'Install permission missing')
    require('android.permission.READ_CONTACTS' not in permissions, 'Reader must not silently add Contacts permission')
    require(core.manifest_problems(manifest, 'source') == [], 'Retired notification-reader capability returned to the manifest')
    core_problems = core.source_problems()
    require(not core_problems, 'Retired reader sources returned to production: ' + '; '.join(core_problems))
    app = manifest.find('application')
    provider = next(node for node in app.findall('provider') if node.get(ANDROID+'authorities') == '${applicationId}.updates.files')
    require(provider.get(ANDROID+'exported') == 'false', 'Update files exported')
    paths = ET.parse(ROOT / 'cleaner/src/main/res/xml/liveguard_update_paths.xml').getroot()
    require(len(paths) == 1 and paths[0].tag == 'cache-path' and paths[0].get('path') == 'verified-updates/', 'Overbroad update FileProvider scope')
    for widget in ('CleanSweepWidget', 'CleanSweepDataWidget', 'CleanSweepPhoneWidget'):
        node = next(item for item in app.findall('receiver') if item.get(ANDROID+'name') == '.widget.' + widget)
        require(node.get(ANDROID+'exported') == 'false', 'Widget exported')
    for name in ('widget_cleansweep_info', 'widget_cleansweep_data_info', 'widget_cleansweep_phone_info'):
        widget = ET.parse(ROOT / 'cleaner/src/main/res/xml' / (name + '.xml')).getroot()
        require(widget.get(ANDROID+'targetCellWidth') == '2' and widget.get(ANDROID+'targetCellHeight') == '2', 'Not a compact widget preset')
    announcer = source('voice/Announcer.kt')
    require('speakNotification' not in announcer and 'Event.MESSAGES' not in announcer and 'voice_messages' not in announcer, 'Private-message speech path returned')
    require('fun speakAnswer' in announcer and 'fun speakNow' in announcer and 'retryOffline' in announcer, 'General speech/offline fallback was removed with the reader')
    require('never save utterance text' in announcer, 'Voice diagnostics privacy contract missing')
    require('CorePrivacyMigration.run' in source('MainViewModel.kt') and 'CorePrivacyMigrationPolicy' in source('data/CorePrivacyMigration.kt'), 'Core privacy cleanup not wired')
    require('SHA-256' in source('updates/LiveGuardUpdates.kt') and 'getPackageArchiveInfo' in source('updates/LiveGuardUpdates.kt') and 'trustedSigner' in source('updates/LiveGuardUpdates.kt'), 'APK identity/signing checks missing')
    require('180_000' in source('updates/VerifiedApkDownload.kt') and 'instanceFollowRedirects = false' in source('updates/VerifiedApkDownload.kt'), 'Download deadline/redirect controls missing')
    require('officialAsset' in source('updates/LiveGuardUpdates.kt') and 'release.optBoolean("prerelease")' in source('updates/LiveGuardUpdates.kt'), 'Release trust/stability gate missing')
    require('AppUpdates.Control' not in source('MainActivity.kt') and 'AppUpdates.Control' not in source('ui/AboutScreen.kt'), 'Cleaner browser updater still wired')
    require('LastUnplugRepo.observe' in source('data/InsightsStore.kt') and 'explicitDisconnect' in source('data/LastUnplug.kt'), 'Genuine unplug owner missing')
    require('val selected: Boolean = false' in source('data/AppCacheRepo.kt'), 'Cache rows auto-selected')
    require('val includeSystemApps: Boolean = true' in source('MainViewModel.kt') and 'val securityIncludeSystemApps: Boolean = true' in source('MainViewModel.kt'), 'Safe system scope defaults missing')
    require('vm.loadAppCaches()' in source('ui/AppCacheScreen.kt'), 'Cache reopening refresh missing')
    require('daily_ai", false' in source('work/HealthWatchWorker.kt'), 'Automatic online daily summaries not opt-in')
    require('CURRENT_WINDOW_MS' in source('ai/ArenaRatingPolicy.kt') and '.singleOrNull()' in source('ai/ArenaRatingPolicy.kt'), 'Rating exact/freshness policy missing')
    require('rating_q025' in source('ai/ArenaRatings.kt') and 'git/blobs' in source('ai/ArenaRatings.kt'), 'Verified official rating schema missing')
    gradle = (ROOT/'cleaner/build.gradle.kts').read_text()
    for rule in ('minSdk = 26', 'targetSdk = 36', 'versionCode = 46', 'versionName = "2.41"'):
        require(rule in gradle, 'Release candidate version/API differs: ' + rule)
    for removed in ('com.google.mlkit:translate', 'com.google.mlkit:language-id'):
        require(removed not in gradle, 'Removed reader dependency is still declared: ' + removed)
    # 2.41 honest watts/amps: the battery-side policy must exist and be wired into the
    # notification card and the health screen (no amp-for-watt substitution anywhere).
    honesty = source('data/ChargingHonestyPolicy.kt')
    require('batterySideWatts' in honesty and 'MAX_PLAUSIBLE_WATTS' in honesty and 'ScreenNote' in honesty,
            'Honest battery-side watts/amps policy missing')
    require('ChargingHonestyPolicy.batterySideWatts' in source('notify/ChargeNotifier.kt') and
            'ChargingHonestyPolicy.screenNote' in source('notify/ChargeNotifier.kt') and
            'ChargingHonestyPolicy.screenNote' in source('ui/HealthScreen.kt'),
            'Charging honesty policy is not wired into the card and health screen')
    # 2.41 heat warning: one owner-set 44 °C threshold, a hush window, and no invented value.
    thermal = source('data/ThermalWarningPolicy.kt')
    require('WARN_BATTERY_C = 44.0f' in thermal and 'HUSH_MS' in thermal and 'shouldAnnounce' in thermal,
            '44 °C thermal warning policy missing')
    require('HOT_BATTERY_C = 44.0' in source('voice/Announcer.kt'),
            'The voice heat threshold and ThermalWarningPolicy.WARN_BATTERY_C are out of step')
    require('ThermalWarningPolicy.decide' in source('work/HealthWatchWorker.kt') and
            'ThermalWarningPolicy.shouldAnnounce' in source('work/HealthWatchWorker.kt'),
            'Thermal warning policy not wired into the background heat watch')
    require('ThermalWarningPolicy.decide' in source('notify/ChargeNotifier.kt') and
            'ThermalWarningPolicy.decide' in source('ui/HealthScreen.kt'),
            'Thermal warning policy not wired into the card and health screen')
    # 2.41 AI output budget + provider model check, enforced in the client and reported in the UI.
    output = source('ai/AiOutputBudgetPolicy.kt')
    require('MAX_ANSWER_TOKENS' in output and 'checkModel' in output and 'effectiveOutputTokens' in output,
            'AI output budget / provider model check policy missing')
    require('AiOutputBudgetPolicy.effectiveOutputTokens' in source('ai/AiClient.kt') and
            'AiOutputBudgetPolicy.checkModel' in source('ai/AiClient.kt'),
            'AI output budget / provider model check not enforced in the client')
    require('AiOutputBudgetPolicy.checkModel' in source('MainViewModel.kt'),
            'Provider model check not surfaced after loading the model list')
    expected = runpy.run_path(str(ROOT/'tools/verify_cleaner_regressions.py'))['EXPECTED']
    require(sum(expected.values()) == 321 and len(expected) == 36, 'Future required regression gate count differs')
    for name, count in expected.items():
        path = ROOT/'cleaner/src/test/java'/Path(*name.split('.')).with_suffix('.kt')
        require(path.is_file() and len(re.findall(r'@Test\b', path.read_text())) == count, 'Required test source missing: ' + name)
    for xml in (ROOT/'cleaner/src/main').rglob('*.xml'):
        ET.parse(xml)
    print('PASS: Static manifest, no-notification-reading/update/rating/widget/scope contracts and XML well-formedness.')
    print('PASS: 36 required suites / 321 prepared declarations; tests NOT executed. Candidate 2.41/code46/API26 matches.')
    print('Source checks are NOT Kotlin/Compose/resource compilation, APK verification, device delivery or a released build.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, StopIteration, ET.ParseError, OSError) as error:
        raise SystemExit('FAIL: ' + str(error))
