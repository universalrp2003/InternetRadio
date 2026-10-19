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
    release_sources = (ROOT/'shared/src/main/java/com/universalrp/updates/ReleaseRepositorySources.kt').read_text()
    require('PAGE_SIZE = 100' in release_sources and 'MAX_RELEASE_PAGES = 20' in release_sources,
            'Updater pagination no longer covers up to 2,000 releases per repository')
    require('Source("universalrp2003", "RP-Android-Apps")' in release_sources and
            'Source("universalrp2003", "InternetRadio")' in release_sources,
            'Dual-repository migration source order or old endpoint fallback changed')
    liveguard_updater = source('updates/LiveGuardUpdates.kt')
    shared_updater = (ROOT/'shared/src/main/java/com/universalrp/updates/AppUpdates.kt').read_text()
    for updater in (liveguard_updater, shared_updater):
        require('MAX_RELEASE_PAGES' in updater and 'releasesPage(page)' in updater and
                'isOfficialReleasePage' in updater and
                ('isOfficialAsset' in updater or 'officialAsset' in updater),
                'One of the four app updater paths is missing scalable pagination or canonical URL checks')
    require('versionCode(candidate) > versionCode(installed)' in shared_updater and
            'signerDigests(candidate) == expected' in shared_updater and
            'actualHash == release.sha256' in shared_updater and
            shared_updater.index('ensureApkMatches(context, file, app, release)', shared_updater.index('fun installUpdate()')) <
            shared_updater.index('launchInstaller(context, file)', shared_updater.index('fun installUpdate()')),
            'Shared updater does not recheck digest/package/version/signer before Android installer handoff')
    require('code(apk) > code(installed)' in liveguard_updater and
            'UpdatePolicy.trustedSigner(signers(apk), signers(installed))' in liveguard_updater and
            'verify(app, file, release) // Recheck private-cache bytes/installed version immediately before handing off.' in liveguard_updater,
            'Live Guard updater does not recheck APK identity and signer before Android installer handoff')
    require('AppUpdates.Control' not in source('MainActivity.kt') and 'AppUpdates.Control' not in source('ui/AboutScreen.kt'), 'Cleaner browser updater still wired')
    require('LastUnplugRepo.observe' in source('data/InsightsStore.kt') and 'explicitDisconnect' in source('data/LastUnplug.kt'), 'Genuine unplug owner missing')
    require('val selected: Boolean = false' in source('data/AppCacheRepo.kt'), 'Cache rows auto-selected')
    require('val includeSystemApps: Boolean = MonitorDefaultsPolicy.CACHE_INCLUDE_SYSTEM' in source('MainViewModel.kt') and
            'val securityIncludeSystemApps: Boolean = MonitorDefaultsPolicy.SECURITY_INCLUDE_SYSTEM' in source('MainViewModel.kt'),
            'Safe system scope defaults missing')
    require('vm.loadAppCaches()' in source('ui/AppCacheScreen.kt'), 'Cache reopening refresh missing')
    require('daily_ai", false' in source('work/HealthWatchWorker.kt'), 'Automatic online daily summaries not opt-in')
    removed_rating_paths = (
        ROOT/'cleaner/src/main/java/com/universalrp/cleansweep/ai/ArenaRatingPolicy.kt',
        ROOT/'cleaner/src/main/java/com/universalrp/cleansweep/ai/ArenaRatings.kt',
        ROOT/'cleaner/src/main/java/com/universalrp/cleansweep/ui/ArenaRatingCard.kt',
        ROOT/'cleaner/src/test/java/com/universalrp/cleansweep/ai/ArenaRatingPolicyTest.kt',
    )
    require('ArenaRatingCard' not in source('ui/AiScreens.kt') and
            all(not path.exists() for path in removed_rating_paths),
            'Arena model-rating card, policy, data and tests must remain removed')
    gradle = (ROOT/'cleaner/build.gradle.kts').read_text()
    for rule in ('minSdk = 26', 'targetSdk = 36', 'versionCode = 52', 'versionName = "2.47"'):
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
    # ---------------------------------------------------------------- 2.42 owner work list
    # Fix 1: the battery broadcast extra rescues kernels whose current *property* reports
    # nothing (the owner's OPPO A3s read 0.00 W / -0.00 A while its own charge history
    # recorded real 4.6-4.8 W sessions). An unsupported sensor still never becomes "0.00 W",
    # and BatteryValuePolicy stays the only decider of the unit.
    battery = source('data/BatteryValuePolicy.kt')
    require('fun currentReading' in battery and 'enum class CurrentSource' in battery and 'BROADCAST' in battery,
            'Battery-current source policy missing')
    # The broadcast key is a literal on purpose: BatteryManager only exposed the constant in
    # API 34, and minSdk is 26. Referencing the constant would trip NewApi on older devices.
    require('"current_now"' in source('data/DeviceHealth.kt') and
            'BatteryValuePolicy.currentReading' in source('data/DeviceHealth.kt'),
            'The battery broadcast current extra is not read')
    require('currentSource = current.source' in source('data/DeviceHealth.kt'),
            'The battery reading no longer records which source supplied the current')
    # 2.43: the owner's OPPO A3s answers zero through BOTH paths, so a zero is never a
    # measurement. The "broadcast zero is a trickle" branch that 2.42 briefly carried must
    # stay gone, or the card prints the fake 0.00 W again.
    require('A zero is never a measurement' in source('data/BatteryValuePolicy.kt') and
            'fromBroadcast != null -> CurrentReading(fromBroadcast, CurrentSource.BROADCAST)' not in source('data/BatteryValuePolicy.kt'),
            'A zero can still be reported as a measured current')
    # Fix 2: Voice A/B are chosen automatically from the installed inventory; the old error is gone.
    voices = source('voice/AnswerVoicePolicy.kt')
    require('fun automaticPair' in voices and 'fun effectivePair' in voices,
            'Automatic answer-voice selection policy missing')
    require('fun saveAutomatic' in source('voice/AnswerVoicePreferences.kt'),
            'The automatic answer-voice pair cannot be saved')
    require('Choose and preview two voices' not in source('MainViewModel.kt') and
            'AnswerVoicePolicy.effectivePair' in source('MainViewModel.kt'),
            'Answers still refuse with the "choose and preview two voices" error')
    # Fixes 3-5: one owner of the default-on monitors and security scope, plus a real re-arm.
    defaults = source('data/MonitorDefaultsPolicy.kt')
    require('CHARGE_CARD = true' in defaults and 'PERSISTENT_MONITOR = true' in defaults and
            'MALWARE_INCLUDE_SYSTEM = true' in defaults and 'STATUS_PILL = false' in defaults,
            'Monitor defaults are not the 2.42 owner decision (the overlay pill must stay opt-in)')
    require('fun rearmAfterUpdateOrBoot' in defaults and 'fun healthWatchWanted' in defaults,
            'Monitor re-arm / health-watch policy missing')
    require('MonitorDefaultsPolicy.CHARGE_CARD' in source('notify/ChargeMonitorService.kt') and
            'MonitorDefaultsPolicy.PERSISTENT_MONITOR' in source('notify/ChargeMonitorService.kt'),
            'The monitor service still reads its own literal default')
    require('MonitorDefaultsPolicy.CHARGE_CARD' in source('data/SettingsRepo.kt') and
            'MonitorDefaultsPolicy.PERSISTENT_MONITOR' in source('data/SettingsRepo.kt'),
            'The DataStore flow and the prefs mirror can still disagree')
    require('rearmAfterUpdateOrBoot' in source('notify/BootReceiver.kt'),
            'An app update or reboot no longer re-arms the ongoing meter')
    require('MonitorDefaultsPolicy.MALWARE_INCLUDE_SYSTEM' in source('MainViewModel.kt') and
            'MonitorDefaultsPolicy.healthWatchWanted' in source('MainViewModel.kt'),
            'The hash-check scope / hourly health watch no longer follow the shared defaults')
    # Fix 6: a permission finding opens that app's own permission page, not an unscoped ROM hub.
    routes = source('data/SecuritySettingsRoutes.kt')
    require('fun appPermissionRoutes' in routes and 'PERMISSION_EDITOR' in routes and
            'EXTRA_PERMISSION' in routes and 'fun isColoros' in routes,
            'Per-app permission routing policy missing')
    require('result += appPermissionRoutes(' in routes,
            'The SMS finding no longer routes to that app\'s own permission page')
    # Fix 7: one spacing scale, including the list top inset that was missing entirely.
    layout = source('ui/HomeLayoutPolicy.kt')
    require('LIST_TOP_DP' in layout and 'BASE_DP' in layout and 'fun onScale' in layout,
            'Home-screen spacing scale missing')
    require('HomeLayoutPolicy.LIST_TOP_DP' in source('ui/HomeScreen.kt') and
            'HomeLayoutPolicy.SCREEN_HORIZONTAL_DP' in source('ui/HomeScreen.kt') and
            'HomeLayoutPolicy.CARD_GAP_DP' in source('ui/HomeScreen.kt'),
            'The home screen does not use the spacing scale')
    # Fix 8: one language source of truth, resolved before the first composition.
    require('object LanguageSourcePolicy' in source('data/LanguageSourcePolicy.kt'),
            'Language source-of-truth policy missing')
    require('Lang.set(Lang.languageIn(this))' in source('MainActivity.kt'),
            'The language is not resolved before the first composition')
    require('key(state.lang)' in source('ui/AppRoot.kt'),
            'A language toggle no longer rebuilds screens that observe no state')
    for display in ('ui/HomeScreen.kt', 'ui/SettingsScreen.kt', 'ui/PhoneScreens.kt',
                    'ui/VoiceScreen.kt', 'ui/AssistantScreen.kt', 'ui/LiveGuardMetricsMatrix.kt'):
        require('state.lang' not in source(display),
                'A display site still reads the state mirror instead of the language authority: ' + display)
    expected = runpy.run_path(str(ROOT/'tools/verify_cleaner_regressions.py'))['EXPECTED']
    require(sum(expected.values()) == 365 and len(expected) == 42, 'Future required regression gate count differs')
    for name, count in expected.items():
        path = ROOT/'cleaner/src/test/java'/Path(*name.split('.')).with_suffix('.kt')
        require(path.is_file() and len(re.findall(r'@Test\b', path.read_text())) == count, 'Required test source missing: ' + name)
    for xml in (ROOT/'cleaner/src/main').rglob('*.xml'):
        ET.parse(xml)
    for module in ('cleaner', 'radio', 'equalizer', 'vocalremover'):
        module_root = ROOT / module / 'src/main'
        app_manifest = ET.parse(module_root / 'AndroidManifest.xml').getroot().find('application')
        require(app_manifest is not None and app_manifest.get(ANDROID + 'allowBackup') == 'false' and
                app_manifest.get(ANDROID + 'fullBackupContent') == '@xml/backup_rules' and
                app_manifest.get(ANDROID + 'dataExtractionRules') == '@xml/data_extraction_rules',
                f'{module}: local-only policy is missing explicit backup/device-transfer controls')
        for rules_path in (module_root / 'res/xml/backup_rules.xml', module_root / 'res/xml/data_extraction_rules.xml'):
            rules = ET.parse(rules_path).getroot()
            excluded_domains = {node.get('domain') for node in rules.iter('exclude') if node.get('path') == '.'}
            require({'root', 'file', 'database', 'sharedpref', 'external'} <= excluded_domains,
                    f'{module}: backup rules no longer exclude app-private data')
    require('Time-to-empty estimate unavailable' in source('data/DeviceHealth.kt') and
            'LiveChargingEta.estimate(battery)?.minutesToFull' in source('data/DeviceHealth.kt') and
            'in-memory battery-level observations while charging' in source('ui/AiScreens.kt'),
            'A battery ETA no longer requires recent observed charging evidence')
    vocal_service = (ROOT/'vocalremover/src/main/java/com/universalrp/vocalremover/processing/SeparationService.kt').read_text()
    require('PrivateSourceCopyRecovery.findExisting' in vocal_service and
            'sameContents' in source('data/DuplicateContentHash.kt'),
            'Private-source recovery or full-content duplicate confirmation was removed')
    print('PASS: Static manifest, privacy/backup, updater, charging, duplicate, rating/widget/scope contracts and XML well-formedness.')
    print('PASS: 42 required suites / 365 prepared declarations; tests NOT executed. Release 2.46/code51/API26 matches.')
    print('Source checks are NOT Kotlin/Compose/resource compilation, APK verification, device delivery or a released build.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, StopIteration, ET.ParseError, OSError) as error:
        raise SystemExit('FAIL: ' + str(error))
