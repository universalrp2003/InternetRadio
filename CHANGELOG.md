# Changelog — maintained Android apps

Detailed historical notes remain in [`releases/`](releases/). This summary does not
claim app-store publication or physical-device certification.

## CleanSweep / Live Guard 2.37 (code 42) — 11 October 2026

**Verified/published** as `v2026.10.11-1` at2026-10-11T09:08:41Z, immutable source
`325576df99dc3d56ad635e5a2d229a0e468dd7da`. [CI 38127107781](https://github.com/universalrp2003/InternetRadio/actions/runs/38127107781) passed 177 required cases
in each Cleaner variant plus build/feature/version/API26/signing gates; six assets checked.
See [release notes](releases/v2026.10.11-1.md) for details and physical-device limits.

- Automatic TTS main-thread initialization, bounded per-language queue, callbacks,
  checked return values, awaited background events, one same-language offline retry,
  start-based cooldowns and connection/job deduplication. Runtime power/battery/network
  receivers are owner-scoped; registration evidence is shown, and manifest-only
  cold-background power delivery is not guaranteed. Voice diagnostics distinguish
  queued/blocked/failed/engine outcomes; real-gated automatic test and guarded TTS settings.
- Real publication-today Tamil RSS/Atom news in phone-local timezone, canonical title/URL
  deduplication, day-scoped unseen rotation ending when caught up, no publisher ribbon,
  old/static/synthetic fallback or refresh reset of seen titles.
- Local configured API/model identity answers without web/API calls; relevant unique
  collapsed evidence for other questions, not an unrelated first-hit Wikipedia fallback.
- Larger resizable launcher widget: battery% + battery-side W, per-widget views,
  available measurements and snapshot time, unique actions and coalesced off-main queries.
  Only genuinely reported fine percent resolution; two W decimals do not certify accuracy.
- Correct microamp API conversion without a magnitude multiplier; fractional SOC does
  not falsely certify exact20→80 charging-history boundaries.
- Retain active Home phone-age estimate, all six 2.35 tools/privacy controls, targeted
  settings and floating pill behaviour. Add52 required cases, retaining all 125 prior.
- Ramesh Radio1.8/PulseEQ1.2 and the two legacy modules unchanged in source.

## CleanSweep / Live Guard 2.36 (code41)

[Verified release](releases/v2026.10.11.md): restored wrapping/tappable estimated-age
badge to active Home with truthful unsupported evidence and off-main daily cache.
125 required Cleaner cases each variant. All six2.35 improvements retained.

## CleanSweep / Live Guard 2.35 (code40)

[Verified release](releases/v2026.10.10-9.md): truthful dated dashboard, allowlisted
compatibility diagnostics, opt-in security history, observed charging/charts,
conditional configurable data budgeting/history, and grounded manually reviewed
checklists/fresh rechecks.113 required Cleaner cases per variant.
