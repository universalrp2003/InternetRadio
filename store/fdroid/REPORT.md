# Publishing these apps for free — honest status (6 Oct 2026)

## Read this first

**Free means free — that part was right.** No Google Play, no $25, no fee anywhere. But while
checking why you could not find the button on Codeberg, I read **IzzyOnDroid's App Inclusion
Policy**, and it contains an AI policy that decides this for us. Here is the honest table.

| Route | Cost | Who signs the APK | What happens to *these* apps |
|---|---|---|---|
| **GitHub Releases** (what you use now) | free | you | **Works today.** Nothing to file, no policy to satisfy. |
| **F-Droid main repo** | free | F-Droid generates its own key | **A real chance** — F-Droid has no AI policy yet. Needs the honest disclosure below, and one reinstall for the family. |
| **IzzyOnDroid** | free | you (keeps your key) | **Refused as they stand.** Two separate policy reasons, quoted below. |
| Google Play | $25 | you | You said no. It also refuses CleanSweep's All-files access. |

## Why IzzyOnDroid will refuse — their words, not mine

From <https://izzyondroid.org/docs/general/AppInclusionPolicy/#ai-policy>:

> **We are strongly opposed to apps which are fully or in part created by generative AI tools.**
> …
> - **Vibe-coded apps will be rejected.**
> - Apps acting as front-end for LLMs … **or integrate with such services, will be rejected.**
> - Readme, Changelogs and similar documentation files are allowed to include LLM-generated
>   texts, **but the code itself should be free of it.**
> - Using LLMs for research, brainstorming, inspiration, debugging, look-ups, and comparable
>   „read-only“ tasks, is acceptable – **provided their output is not included in the app's code.**
>
> **Any lack of transparency discovered by us, can lead to the project being degraded to rejected
> state.**

Two independent strikes against these five apps:

1. **The code was written by an AI coding agent** (Arena.ai Agent Mode) working from your feature
   requests. You chose what each app does, tested every build on your phone, reported what was
   wrong and directed the fixes — but the Kotlin was not typed by a human. That is IzzyOnDroid's
   definition of "vibe-coded": rejected. And their issue form **requires** an honest
   "Assistance Level" answer (None / Minimal / Moderate / Substantial / Dominant), so there is no
   version of this where we file it truthfully and get a yes.
2. **CleanSweep integrates LLM services** — the optional assistant can be pointed at Google
   Gemini, NVIDIA, OpenRouter, Groq, OpenAI or a custom endpoint with a key you paste (plus a
   keyless "Free" option). "Apps … which integrate with such services, will be rejected" is
   explicit, and their point 6 refuses apps that support the big AI platforms.

I am not going to write a request that answers "None" to that question. If the answer were ever
found out, their policy says the project gets "degraded to rejected state" — and I would rather
you never be in that position over an app store listing.

**Your Codeberg account is fine**, by the way. Nothing about it breaks any rule; you simply had
the wrong page open. Their new terms restrict *hosting LLM-generated projects on Codeberg*, not
having an account.

## The corrected links (what your screenshot was missing)

The tracker moved. `IzzyOnDroid/repo` is **archived** — its README says the issue tracker has
moved — and the app requests now live in the **`repodata`** repository:

* right page: <https://codeberg.org/IzzyOnDroid/repodata/issues/new/choose>
  (template: **App Inclusion Request**, title `[AppRequest] <App Name>`)
* what you had open: `codeberg.org/issues` — that is **your own** dashboard. Its "Create…" menu
  offers New repository / New migration / New organization, which is exactly what your second
  screenshot showed. It cannot create an issue: an issue always belongs to *somebody's*
  repository, never to your account root.

On a phone, type the URL above (or open `codeberg.org/IzzyOnDroid/repodata`, then **Issues** →
**New issue**). When it is right, the page header names `IzzyOnDroid/repodata`, not your own
name.

## F-Droid: the one public store where these apps have a real chance

F-Droid has **no formal AI policy yet** — an interim one is being discussed right now, and one of
their developers has written that AI-generated code must be **clearly disclosed**. So the honest
position today is: submit, disclose, and let humans decide. Nobody can promise the outcome, and
an "AI" warning label on CleanSweep's optional assistant is a realistic outcome (a label, not a
ban; labels are F-Droid's normal tool for this).

### If you want to go that way, these are the exact steps

0. **One minute for you, only you can do it:** fix the repository description on GitHub
   (Settings → General → Description). It currently says *"first build"*, which is what the
   GitHub page shows to any reviewer. Something like: *"Five free Android apps (GPL-3.0):
   CleanSweep phone cleaner, Ramesh Radio Tamil FM + news, Internet Radio, AppForge, PulseEQ —
   one Gradle project, one module each."* My token reaches the code but not the repository
   settings (403), otherwise I would have done it.
1. Make a free account at <https://gitlab.com> (F-Droid's own data is on GitLab).
2. Fork <https://gitlab.com/fdroid/fdroiddata>, upload the five files from
   `store/fdroid/metadata/`, open a merge request, and paste the text from
   `store/fdroid/README.md` section B — **including the new AI disclosure paragraph**, which is
   written out for you there.
3. *No-account alternative:* open one issue at <https://gitlab.com/fdroid/rfp/-/issues/new> with
   the text in `store/fdroid/RFP_ISSUE.md`. A volunteer does the packing. Slower, and the same
   disclosure applies.
4. Send me the screenshots if you want the listing to look right; F-Droid can show a listing
   without them but it looks bare. Since F-Droid signs with its own key, the family uninstalls
   the GitHub build **once** and reinstalls from the F-Droid client after that.

### What I would not do

File the IzzyOnDroid request for you anyway, with a "None" in the AI box, hoping nobody checks.
That is a lie in a required field of a form whose policy ends with the sentence about
transparency, and it is your Codeberg account on the line, not mine.

## If you would rather skip the review queue entirely

Both of these are free and need no new account:

1. **Stay on GitHub Releases.** <https://github.com/universalrp2003/InternetRadio/releases> —
   the family installs a new APK over the old one (same signature, so it updates normally), and
   every `git tag` I push builds and publishes them. This is what is working today.
2. **Your own F-Droid-compatible repo, published from GitHub Pages.** I can add a CI job that
   runs `fdroidserver` and publishes a signed index to GitHub Pages, so your family adds *your*
   repo URL in Droid-ify / Neo Store once and then gets updates through the store app —
   the same experience as a big store, without anyone reviewing the code but you. It is a real
   piece of work (index signing key to keep, a Pages branch to publish), it is not
   discoverability, and it is entirely your call. Say the word and I will build it.

## Where the artwork stands (done, 6 Oct)

Every store wants the same three things, and two of them no longer need anything from you:

| Piece | State |
|---|---|
| 512x512 icon | **Done** for all five apps, generated from the real launcher vectors by `tools/make_store_assets.py`. Internet Radio's stock Android play icon is gone: it has a launcher mark of its own now, with bitmap fallbacks for its older minimum version. |
| 1024x500 feature graphic | **Done** for all five, via `tools/make_feature_graphics.sh`, using each app's own background colour, accent colour and fastlane description. Both scripts run in CI, so a fresh checkout always has them. |
| Phone screenshots | **Waiting on you** - the nine screenshots are not on my disk, because attachments do not survive between messages. Re-attach them and `tools/make_screenshots.py` places them (1080x1920, numbered, right locale), after the private parts are cropped. |

`store/asset-preview.png` shows all of it in one image.

## So, your move

Tell me which of these you want and I will do my half:

* **F-Droid submission** — I polish the MR text, you paste it, I place the screenshots when you
  re-send them.
* **GitHub Releases only** — nothing to do, it already works; I keep tagging releases.
* **Own F-Droid repo on GitHub Pages** — I set it up in CI, you add the URL on the phones.
* **IzzyOnDroid anyway, told truthfully** — I will write it with the honest AI answers, but go in
  expecting a refusal under the policy quoted above, so that nobody wastes an evening on it.
