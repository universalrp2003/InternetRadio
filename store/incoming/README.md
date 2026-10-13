# Incoming store assets

Keep this directory free of user data. Do not upload original phone screenshots, audio, diagnostics, API keys or app data here: this repository is public, and removing a file later does not erase it from Git history.

Store screenshots must be genuine app captures, reviewed and sanitized on the device before they are committed under the relevant module's `fastlane/metadata/android/<locale>/images/phoneScreenshots/` directory. Remove account details, notifications, installed-app lists, public/private IP addresses, router information, SIM/operator details and personal files. Include only assets explicitly approved for public release.

For approved images, use `tools/make_screenshots.py` as documented in [`../README.md`](../README.md). This folder is intentionally empty otherwise.
