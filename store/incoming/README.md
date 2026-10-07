# Drop phone screenshots here — this is the route that works

## Why this folder exists

Photos attached to the chat are **not reaching the machine that builds these apps in this
session** (three batches have been sent, none arrived on disk — the folder the chat claims to
save them to does not exist at all). Anything uploaded to this repository, though, is reachable
immediately: the build machine pulls files out of GitHub through the API, which was verified
with a 1.9 MB image before this note was written.

So: upload here from the phone, and the agent takes it from there.

## ⚠️ Read this before uploading

**This repository is public, and anything committed to it stays in its history forever.**
GitHub's own interface does not let a file be truly deleted afterwards.

Upload **only** screenshots with nothing personal in them. From the current set, that means the
CleanSweep home screens, the Quick cleaning actions screen, Voice & daily watch, the AI analysis
settings, and the Ramesh Radio screens (stations, equalizer, clear sound, local audio).

**Do not upload:**

| Screenshot | What it exposes |
|---|---|
| Wi-Fi & network | your phone's IP, the router's address, the DNS servers, the devices found on your network |
| Mobile & data / signal | your **public** IP address, your city, your ISP, your SIM |
| Security check | the list of apps installed on your phone |

Those three are **not needed for the store listing** — leave them out and nothing is lost. If you
want one of them shown publicly, crop the private part first on the phone (Gallery → Edit →
Crop, keep 9:16-ish shape), then upload the cropped copy.

## How to upload, on the phone

1. Open <https://github.com/universalrp2003/InternetRadio> in Brave and switch to **desktop
   site** (⋮ → *Request desktop site*) — upload buttons do not appear in the mobile layout.
2. Navigate to **store → incoming**.
3. Tap **Add file → Upload files**, pick the screenshots (several at once is fine).
4. Leave the file names exactly as they are; the agent works out which app each one is and the
   order to show them in.
5. Tap **Commit changes**, then tell the agent it is done.

## What happens next

The agent pulls the files with the GitHub API, opens each one, places it into
`<module>/fastlane/metadata/android/<locale>/images/phoneScreenshots/` as `1.png`, `2.png`, ...,
paints a bar over anything that must not be public, and **deletes the files from this folder in
the same commit**. Every store that reads fastlane (F-Droid included) then shows them.

This folder stays empty otherwise, on purpose: if it has files in it, they are waiting to be
placed.
