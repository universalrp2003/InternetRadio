#!/usr/bin/env python3
"""Check one GitHub release asset against the exact APK bytes that CI verified.

Usage: verify-release-asset.py <releases.json> <owner/repo> <tag> <asset name> <local apk>
                               <expected size> <expected sha256> <expect draft: true|false>

<releases.json> is the output of `gh api --paginate repos/<repo>/releases?per_page=100`. The same
checks as the Oor Arivippu release: one release for the tag, the expected draft state, not a
prerelease, exactly one uploaded asset with the expected size and digest, and canonical URLs once
public.
"""
import hashlib
import json
import pathlib
import sys


def main() -> None:
    response, repo, tag, name, apk_path, size, digest, draft = sys.argv[1:]
    text = pathlib.Path(response).read_text(encoding="utf-8")
    decoder = json.JSONDecoder()
    pages, index = [], 0
    while index < len(text):
        while index < len(text) and text[index].isspace():
            index += 1
        if index >= len(text):
            break
        page, index = decoder.raw_decode(text, index)
        pages.append(page)
    matches = [release for page in pages for release in page if release.get("tag_name") == tag]
    assert len(matches) == 1, f"expected one release for {repo}/{tag}, found {len(matches)}"
    release = matches[0]
    expect_draft = draft == "true"
    assert release.get("draft") is expect_draft, f"unexpected draft state for {repo}/{tag}"
    assert release.get("prerelease") is False, f"{repo}/{tag} is marked prerelease"
    local_digest = "sha256:" + hashlib.sha256(pathlib.Path(apk_path).read_bytes()).hexdigest()
    assert local_digest == "sha256:" + digest, "the local APK changed during publication"
    assets = [asset for asset in release.get("assets", []) if asset.get("name") == name]
    assert len(assets) == 1, f"expected exactly one {name} asset at {repo}/{tag}"
    asset = assets[0]
    assert asset.get("state") == "uploaded", f"asset upload is incomplete at {repo}/{tag}"
    assert str(asset.get("size")) == size, f"asset size mismatch at {repo}/{tag}"
    assert asset.get("digest") == local_digest, f"asset digest mismatch at {repo}/{tag}: {asset.get('digest')}"
    download = asset.get("browser_download_url", "")
    assert download.startswith(f"https://github.com/{repo}/releases/download/") and download.endswith("/" + name)
    if not expect_draft:
        assert release.get("html_url") == f"https://github.com/{repo}/releases/tag/{tag}", (
            f"non-canonical release page for {repo}/{tag}"
        )
        assert download == f"https://github.com/{repo}/releases/download/{tag}/{name}", (
            f"non-canonical asset URL for {repo}/{tag}"
        )
    state = "DRAFT" if expect_draft else "PUBLIC"
    print(f"VERIFIED {state} {repo}/{tag}: {name}, {size} bytes, {local_digest}")


if __name__ == "__main__":
    main()
