# Radio 1.6 — required device checks

CI can compile and test PCM arithmetic, but cannot verify vendor audio effects,
DocumentsProvider permissions or a real two-hour stream interruption.

- Select a music folder with nested folders, duplicate names and mixed audio formats.
  Verify the list excludes unrelated directories, cancel the picker, refresh it,
  then close/reopen the app. Revoke folder permission and verify recovery messaging.
- Start a song in the middle of the folder. Next/Previous must stay in that queue;
  test wraparound, Pause/Play, Stop/Play, widget and notification controls.
- Stop local playback, let Android recreate the service, then resume. The saved
  song should be selected rather than silently switching to an old radio station.
- With stereo headphones, centre must preserve both channels. Far left mutes right,
  far right mutes left; halfway attenuates, never amplifies. Mono should be unchanged.
- Enable other-app EQ, start a compatible player, change the Radio EQ curve and
  listen. A non-cooperating player is not evidence of a successful global effect.
  Test global attempt blocked/allowed, Stop EQ in notification, and disable the
  separate PulseEQ app to avoid competing ownership.
- Pause an HTTP radio stream for two hours, then resume from the app and notification.
  Test Wi-Fi/mobile transitions, airplane mode, an offline station and recovery.
  A stalled connection should report an error within the 30-second watchdog window.
- Karaoke is NOT in this version. Do not label it as vocal separation.
