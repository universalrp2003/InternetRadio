# Moving the source to RP-Android-Apps

**Owner decision, 18 October 2026:** move the code and future development to `universalrp2003/RP-Android-Apps` now, and keep `InternetRadio` as the update backup until the grace-period review (ask_user answer: “Move the code now; keep the backup until November”).

**Status:** prepared in `InternetRadio`. On 18 October 2026 the owner added the three secrets below to `RP-Android-Apps` (names checked from the owner's screenshot; the `InternetRadio` token is limited to that one repository with Contents read and write and expires on 15 February 2027). Next: the import, done by a new Arena session opened on `RP-Android-Apps` (an Arena session pushes only to its own branch).

## What changes and what does not

- **Moves:** all source, workflows, docs, release notes and the committed (password-protected or already public) keystores, with full history, from the `InternetRadio` branch `arena/d4fb2344-internetradio`. That branch is 268 commits ahead of `InternetRadio`'s outdated `main` (`69c117e`); import the branch, not `main`. Import the branch's latest commit.
- **Does not change for anyone using the apps:** all six apps (Live Guard, Ramesh Radio, PulseEQ, RP Vocal Remover Studio, Oor Arivippu, Secret Message) keep checking `RP-Android-Apps` first and `InternetRadio` second. `InternetRadio` stays public and unarchived with every release, tag and APK.
- **Unchanged plan:** Phase 3 and Phase 4 of [`repository-migration.md`](repository-migration.md) still apply: review around 14 November 2026; only then, with the owner's confirmation, updater-only versions without the `InternetRadio` source, and archiving (never deleting) `InternetRadio`.

## Owner steps (about 10 minutes)

1. In `RP-Android-Apps` → **Settings → Secrets and variables → Actions → New repository secret**, add (never paste the values in a chat):

   | Secret | Value | Needed for |
   |---|---|---|
   | `SECRETMESSAGE_SIGNING_PASSWORD` | The same password you saved on 17 October 2026 | Signing Secret Message. Never create a new key: installed copies accept updates only from the existing one. |
   | `CLOUDFLARE_API_TOKEN` | A Cloudflare API token from the “Edit Cloudflare Workers” template (a new one is fine) | Deploying the Secret Message relay (same Worker, same account). |
   | `INTERNETRADIO_RELEASE_TOKEN` | A new fine-grained GitHub token: repository `InternetRadio` only, permission **Contents: Read and write** | Copying each new release to the `InternetRadio` backup until Phase 4. |

   `RP_ANDROID_APPS_RELEASE_TOKEN` is not needed there: a workflow publishes to its own repository with GitHub's built-in token.
2. In `RP-Android-Apps` → **Settings → Actions → General**, keep actions allowed.
3. Open a new Arena session on `universalrp2003/RP-Android-Apps` and paste the handoff prompt at the end of this document.

## Checklist for the new session (in RP-Android-Apps)

1. **Import with history, without tags:**

   ```sh
   git remote add internetradio https://github.com/universalrp2003/InternetRadio.git
   git fetch --no-tags internetradio arena/d4fb2344-internetradio
   git merge --allow-unrelated-histories --no-ff internetradio/arena/d4fb2344-internetradio
   ```

   Expected conflicts: `README.md` (use `InternetRadio`'s README and name `RP-Android-Apps` as the project home) and `LICENSE` (both are GPL-3.0; keep `RP-Android-Apps`' copy). **Never push tags:** `RP-Android-Apps` already has release tags (`live-guard-v2.46` … `secret-message-v1.1.0`) on its own commits, and `InternetRadio`'s tags must not replace them.

2. **Workflows** (repository and branch names are hard-coded):
   - Publishing is locked to the old session branch `arena/d4fb2344-internetradio`. Change it to the new session's branch in `oorarivippu-release.yml` (`on.push.branches`), `secret-message-wifi-test.yml` (`PUBLISH_REQUESTED` and the `publish` job's `if`) and `secret-message-signing-key.yml` (the one-time key job; it must remain a no-op because the key exists).
   - The roles flip, because the workflows now run in `RP-Android-Apps`: publish there with `github.token`, and copy to `InternetRadio` with `secrets.INTERNETRADIO_RELEASE_TOKEN`. This affects `oorarivippu-release.yml` (`source_repo`/`target_repo` around line 159, and `SOURCE_TOKEN`/`TARGET_TOKEN` in the five steps at lines 153–280), `secret-message-wifi-test.yml` (the `publish` job) and `build-apk.yml` (`DESTINATION_REPO` at line 989, every `RP_ANDROID_APPS_RELEASE_TOKEN` use from line 975, and the `InternetRadio` URL checks at lines 1278–1297).
   - Keep: `RP-Android-Apps` published first and `InternetRadio` second, the size and SHA-256 checks of both copies, and the refusal to overwrite an existing tag.
3. **Verify every lane in `RP-Android-Apps` before any release:** `build-apk` (build only), OorArivippu test APK, the Secret Message app build (signed; certificate SHA-256 `58e370e417cf6f33234689acd5bf379474f5a8c55f79181d9e892a321bc04f08`), the Secret Message preview, and the relay (local tests, deploy, live checks). Use no publish marker until all pass.
4. **Docs:** update `README.md`, `AGENTS.md` and `PUBLISHING.md` to say development happens in `RP-Android-Apps`, keeping links to the `InternetRadio` history.
5. **Never:** rename, archive or delete `InternetRadio`; force-push `RP-Android-Apps` `main`; publish the same version from both repositories; remove the `InternetRadio` updater fallback before Phase 4.

## After the move

- Publish only from `RP-Android-Apps`. `InternetRadio`'s release workflows publish only from the old session branch, which then receives no more pushes.
- Each new release is still copied to `InternetRadio` until Phase 4, so the backup stays complete.

## Handoff prompt for the new session

> Continue the RP Ideas Android project in this repository (universalrp2003/RP-Android-Apps). Follow https://github.com/universalrp2003/InternetRadio/blob/arena/d4fb2344-internetradio/docs/source-migration.md: import branch arena/d4fb2344-internetradio of universalrp2003/InternetRadio with full history and no tags, adapt the workflows as listed there, and verify every CI lane before any release. The secrets SECRETMESSAGE_SIGNING_PASSWORD, CLOUDFLARE_API_TOKEN and INTERNETRADIO_RELEASE_TOKEN are set. Keep InternetRadio and the apps' InternetRadio update fallback unchanged until the November review.
