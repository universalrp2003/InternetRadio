#!/usr/bin/env python3
"""Static source contracts only. Does NOT build Android, execute JVM tests or certify a phone."""
from pathlib import Path
import re
import runpy
import xml.etree.ElementTree as ET

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
    app = manifest.find('application')
    reader = next(node for node in app.findall('service') if node.get(ANDROID+'name') == '.notifications.MessageReaderService')
    require(reader.get(ANDROID+'permission') == 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE', 'Listener binding not protected')
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
    notification_files = '\n'.join(p.read_text() for p in (JAVA / 'notifications').glob('*.kt'))
    require('AiClient' not in notification_files and 'WebLookup' not in notification_files and 'generate_speech' not in notification_files, 'Message-to-online-provider path introduced')
    translator = source('notifications/LocalMessageTranslator.kt')
    require('if (!ready()) return null' in translator and 'downloadModelIfNeeded' in translator and 'identifyLanguage' in translator, 'Local translation/model gating missing')
    announcer = source('voice/Announcer.kt')
    require('forceOffline = true' in announcer and 'startGuard' in announcer, 'Private speech lacks offline/late-privacy gates')
    require('never save utterance text' in announcer, 'Voice diagnostics privacy contract missing')
    require('getBoolean("enabled", false)' in source('notifications/NotificationReaderPrefs.kt'), 'Reader not opt-in')
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
    for rule in ('minSdk = 26', 'targetSdk = 36', 'versionCode = 44', 'versionName = "2.39"'):
        require(rule in gradle, 'Release candidate version/API differs: ' + rule)
    expected = runpy.run_path(str(ROOT/'tools/verify_cleaner_regressions.py'))['EXPECTED']
    require(sum(expected.values()) == 295 and len(expected) == 32, 'Future required regression gate count differs')
    for name, count in expected.items():
        path = ROOT/'cleaner/src/test/java'/Path(*name.split('.')).with_suffix('.kt')
        require(path.is_file() and len(re.findall(r'@Test\b', path.read_text())) == count, 'Required test source missing: ' + name)
    for xml in (ROOT/'cleaner/src/main').rglob('*.xml'):
        ET.parse(xml)
    print('PASS: Static manifest, private access/translation/update/rating/widget/scope contracts and XML well-formedness.')
    print('PASS: 32 required suites / 295 prepared declarations; tests NOT executed. Release candidate2.39/code44/API26 matches.')
    print('Source checks are NOT Kotlin/Compose/resource compilation, APK verification, device delivery or a released build.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, StopIteration, ET.ParseError, OSError) as error:
        raise SystemExit('FAIL: ' + str(error))
