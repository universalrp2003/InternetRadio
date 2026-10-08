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
