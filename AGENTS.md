# Standing project requirements

- Actively maintain CleanSweep, Ramesh Radio and PulseEQ. Preserve the two legacy
  apps, AppForge and the original Internet Radio; do not delete them.
- Target Android 8.0/API 26 and newer across all manufacturers, not one user's phone.
  Use runtime guards/fallbacks and honestly label device-dependent capabilities.
- Following the owner's 2026-10-08 instruction, prepare README, changelogs and
  release notes for future app updates; publish the verified release APKs for the
  three maintained apps to GitHub Releases rather than leaving only CI artifacts.
  Do not publish failed builds, mislabel incomplete features or overwrite an
  existing release asset with different code under the same version.
- Include an honest validation summary and identify required real-device tests.
- Never upload selected audio for karaoke without explicit approval; online and
  offline AI separation are still unfinished (see docs/radio-karaoke-investigation.md).
- Preserve the owner's phone-age estimate on the Home dashboard. On 2026-10-11
  the owner explicitly requested its restoration and asked that future redesigns
  not silently remove it. Keep it labelled as an estimate, not an exact manufacture/
  purchase date; unsupported model/factory evidence must remain unavailable.

- Following the owner's 2026-10-11 voice/news/widget choices: redesign the **launcher
  home-screen widget**, not the floating pill; default to larger battery % plus
  battery-side watts together, with a per-widget view selector. Keep floating
  watts-only charging/unplug behaviour intact. Never invent five-decimal battery
  precision or turn display decimal places into an accuracy claim.
- Home news must be **today only in the phone's local timezone**, real publication
  timestamps required, deduplicated and not automatically repeating read headlines.
  No publisher ribbon/static safety tips/synthetic "today" fallback in the banner.
  Retain article links/source metadata internally; keep security review separate.
- The owner reports manual speech works but automatic alerts are silent on Redmi
  13 5G (Android16/API36 screenshots, exact ROM unknown). Diagnose delivery gates,
  callbacks/background paths honestly; never claim enabled switches or TTS engine
  completion certify audible speech/OEM background survival.
