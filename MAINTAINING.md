# Maintaining PokerSI

How to keep this repository current and its supply chain locked down.
Nothing here needs special tools beyond `gh` and the Android SDK (with the
NDK and CMake for the native build).

## The quarterly routine

Dependabot follows one policy: patch vulnerabilities fast, refresh everything
else rarely.

- **Security updates** open a PR as soon as an advisory affects the app, any
  day of the year. Treat those first.
- **Version updates** arrive as one combined PR per quarter: Gradle plugins
  and libraries, the Gradle wrapper, the llama.cpp submodule, fastlane and
  the Actions pins together. Only releases at least 30 days old are proposed,
  so a broken or hijacked release has usually been pulled first. Majors come
  in the same PR; read their release notes before merging. Actions are pinned
  to commit SHAs, and Dependabot moves the SHA and the `# vX.Y.Z` comment
  together.

Merge a PR (squash) once its checks are green. The `Main` ruleset requires
`build`, `Unit tests`, `Android lint`, `Analyze (java-kotlin)`, `Analyze (actions)`, `Analyze (python)` to pass, so a red PR can't be merged by accident.

## Toolchain bumps by hand

Dependabot covers the Gradle plugins, the libraries, the Gradle wrapper and
the submodule commit. For anything else:

- **Gradle wrapper** (if Dependabot hasn't): take the checksum from
  <https://gradle.org/release-checksums/> and run
  `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sha>`.
  Keep `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.
- **New Android API level**: change `compileSdk` and `targetSdk` in
  `app/build.gradle.kts`, and the `platforms;android-NN.0 build-tools;NN.0.0`
  packages in `build-release.yml`, `ci.yml`, `codeql.yml` under `.github/workflows/`.
- **NDK or CMake**: change `ndkVersion` and the `cmake` `version` in
  `app/build.gradle.kts`, and the `ndk;…` and `cmake;…` packages in
  `build-release.yml` (the only workflow that builds native code). The NDK
  version is also part of the ccache key there, so a new NDK starts a fresh
  cache.
- **JDK**: keep the `java-version` in every workflow in step with the
  Java/Kotlin target in `app/build.gradle.kts`.
- **A new GitHub Action**: pin it to a full commit SHA with a version comment.
  The repository only runs GitHub's own actions plus the ones on its allow
  list (see below); add the new one there first.

## llama.cpp

`app/src/main/cpp/llama.cpp` is a submodule of
[andriydruk/llama.cpp-android](https://github.com/andriydruk/llama.cpp-android),
the fork LM Playground uses: upstream llama.cpp plus `fd:N` file-descriptor
paths, so models picked through the Storage Access Framework load without a
copy. The fork keeps one branch per upstream build (`android-bNNNNN`), and
`.gitmodules` names the one we track. Dependabot proposes new commits on
that branch. Moving to a newer upstream build is by hand:

```sh
git submodule set-branch --branch android-bNNNNN app/src/main/cpp/llama.cpp
git submodule update --remote app/src/main/cpp/llama.cpp
./gradlew test assembleDebug     # then play a hand on a phone with a model loaded
```

Check `pokersi-jni.cpp` still compiles against the new `llama.h` (the API
moves), and that a model downloads, loads and plays. The CI build fails if
the APK comes out without `libpokersi.so`.

The native build is cached with ccache in `build-release.yml`, keyed on the
NDK version, the submodule commit and the JNI sources. ccache hashes file
contents, so an older cache can only make a build faster, never different.
To start clean, delete the `Linux-ccache-…` entries under **Actions ›
Caches**.

## Releasing

Everything ships from GitHub: merging to `main` is the release.

### Shipping an update

1. Make the change on a branch and open a pull request to `main`.
2. In the same pull request, update the release notes in
   `fastlane/metadata/android/en-US/changelogs/default.txt` (at most 500
   characters). Testers, and later Play users, see this text. A
   `changelogs/<versionCode>.txt` overrides it for one build.
3. CI runs the build, tests, lint, CodeQL and the `store listing` check, and
   Codex reviews the pull request. Squash-merge once it is green.
4. The push to `main` builds and signs the APK and Play bundle, replaces the
   GitHub release (the sideload APK), uploads the bundle to Google Play's
   **Closed testing - Alpha** track with the release notes, and syncs the
   store listing. The version code is the workflow run number, so it is never
   bumped by hand.
5. Play reviews the update by itself (managed publishing is off); follow it in
   Play Console › the app › **Publishing overview**.

### Changing the store listing

The listing lives in `fastlane/metadata/android/en-US/`: `title.txt` (at
most 30 characters), `short_description.txt` (80), `full_description.txt`
(4000) and `images/` (icon, feature graphic, screenshots). For the images,
re-render them with `tools/screenshots/shoot.py` (`--no-render` to redo only the captions; see the README), then commit them. Store screenshots are promo shots: a headline and
subline outside the framed capture, never bare captures. Only images that
changed are uploaded (Play's copies are compared by checksum), and each change
goes through Play's review.

### Going to production

Production needs Play's production access first: for a personal developer
account, 12 testers opted in to the closed test for 14 days, then apply in
Play Console. After that, run **Actions › Promote to production**
(`play-promote.yml`):

- action `promote`, rollout `1`: the completed release on Closed testing -
  Alpha goes to everyone in production, as is, release notes included. A
  rollout below 1 (say `0.2`) starts a staged rollout instead.
- action `update rollout`: widens a staged rollout (a larger fraction) or
  finishes it (`1`).

Both check the release's status first: `promote` only takes a completed alpha
release and `update rollout` only an in-progress production rollout, so a
draft is never published. Before production access is granted, Play refuses
and the run fails.

### When something fails

- **`Google Play` job fails:** nothing reached Play. Its log says why (often a
  listing rule); fix it, or rerun the job from the Actions page.
- **`store listing` fails on a pull request:** the error names the file and
  the Play rule it breaks. Run the check locally with
  `python3 .github/scripts/check_store_listing.py` (needs `python3-pil` or
  `pip install pillow`).
- **Never rename `.github/workflows/build-release.yml`:** the run number, and with
  it the version code, would restart, and Play rejects any version code that
  isn't higher than the last.

### How it works

The `Google Play` job in `.github/workflows/build-release.yml` runs fastlane `supply`
(pinned in `Gemfile.lock`, on the latest stable Ruby) after `build`. It
uploads the bundle and `mapping.txt`, then syncs the listing text, icon,
feature graphic and screenshots from `fastlane/metadata/android`. It signs in
as the Google Cloud service account
`github-play-publisher@play-publishing-511400.iam.gserviceaccount.com`, whose
JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play`
environment (which only `main` can deploy to). `play-promote.yml` uses the
same environment, and `.github/scripts/play_release.rb` does its status
checks. The `release` job attests the APK's and bundle's build provenance and
replaces the single GitHub release.

The bundle carries the native symbol table, so Play
symbolicates native crashes without a separate upload.

Until the four signing secrets exist, `main` builds unsigned, warns, and
publishes nothing. Every unsigned build (pull requests too) uploads a
`pokersi-preview.apk` in its run artifact, signed with a throwaway key, so a
change can be installed and tried; uninstall it before installing a real
signed build. To set the secrets up once:

```sh
keytool -genkeypair -v -keystore pokersi-release.jks -alias pokersi \
  -keyalg RSA -keysize 4096 -validity 10000
gh secret set POKERSI_KEYSTORE_BASE64 < <(base64 -w0 pokersi-release.jks)
gh secret set POKERSI_STORE_PASSWORD
gh secret set POKERSI_KEY_ALIAS        # pokersi
gh secret set POKERSI_KEY_PASSWORD
```

Keep the keystore and its passwords somewhere safe outside the repository:
with Play App Signing it is the upload key, and without it a sideloaded
PokerSI can never be updated in place.

## Store listing

`fastlane/metadata/android/en-US/` holds the listing text (`title.txt` at
most 30 characters, `short_description.txt` at most 80,
`full_description.txt` at most 4000) and the release notes in
`changelogs/default.txt`. The images in `images/` come from
`tools/screenshots/`; rerun it after a visible UI change. The privacy
policy Play and the app link to is <https://bradflaugher.github.io/PokerSI/privacy/>
(`site/privacy/index.html`); keep it in step with what the app does
(network use, permissions, storage).

## Website

`site/` is the project site, <https://bradflaugher.github.io/PokerSI/>:
hand-written HTML and CSS, no build step, no scripts and nothing loaded
from another origin (the fonts are the app's own, as WOFF2). Preview it
with `python3 -m http.server -d site`. `.github/workflows/pages.yml` checks
it on pull requests (`.github/scripts/check-site.py`: links resolve,
nothing external) and deploys it on every push to `main` that touches it;
Pages is set to "GitHub Actions" in Settings › Pages. Its screenshots are
smaller WebP copies of `docs/screenshots/`; after `shoot.py`, refresh them
with

```sh
for f in site/img/*.webp; do n=$(basename "$f" .webp); [ -f "docs/screenshots/$n.png" ] &&
  magick "docs/screenshots/$n.png" -resize 540x960 -quality 82 "$f"; done
```

## Play Console settings that live outside the repository

Set when the app was created (October 2026); check or recreate them here:

| Setting | Value |
|---|---|
| App | Free game, category Card; contact brad@bradflaugher.com and the GitHub repository |
| Privacy policy URL | `https://bradflaugher.github.io/PokerSI/privacy/` |
| Closed testing - Alpha | Testers: the Google Group `testers-community@googlegroups.com` (Testers Community); feedback to brad@bradflaugher.com |
| Countries | All, except the markets where Play bans simulated gambling: Algeria, Iran, Jordan, Libya, Oman, Qatar, Saudi Arabia, South Korea, Tunisia, the United Arab Emirates and Yemen (Palestine and Syria aren't offered). South Korea would need a GRAC rating first |
| Content rating | Game with simulated gambling (playable casino games, a strong focus, no cash payouts); 18+ in most regions, ESRB Teen |
| Target audience | 18 and over |
| Data safety | No data collected or shared. The app sends nothing about the player; the only network use is a model download the player starts, which Play exempts as a user-initiated transfer the user expects. Hugging Face's own logging of that request (IP address) is covered in the privacy policy. Revisit this if PokerSI ever sends anything itself |
| Foreground service | `FOREGROUND_SERVICE_DATA_SYNC` declared as "network processing: other" (model downloads), with an unlisted demo video: <https://www.youtube.com/watch?v=CCr1sQhnZLw> |
| Store listing images | Not labelled as AI-generated: they're rendered by the app's own code |

## Security settings that live outside the repository

These are GitHub settings, not files, so they are listed here to check or
recreate them:

| Setting | Value |
|---|---|
| Ruleset `Main` (default branch) | No deletion or force push, linear history, changes through a squash-merged PR, required checks: `build`, `Unit tests`, `Android lint`, `Analyze (java-kotlin)`, `Analyze (actions)`, `Analyze (python)`, `store listing` |
| Merge buttons | Squash only, PR title as the commit title, branches deleted after merge |
| Actions permissions | GitHub-owned actions plus `android-actions/setup-android`, `gradle/actions/*`, `reactivecircus/android-emulator-runner` and `ruby/setup-ruby`; full-SHA pinning required |
| Google Play API access | Service account `github-play-publisher@play-publishing-511400.iam.gserviceaccount.com` (Google Cloud project `play-publishing-511400`), invited in Play Console › Users and permissions with account-wide: view app information, release to production, release to testing tracks, manage store presence. Its JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play` environment |
| Environment `google-play` | Deployable from `main` only; holds `PLAY_SERVICE_ACCOUNT_JSON`; the `Google Play` job and `play-promote.yml` run in it |
| Default `GITHUB_TOKEN` | Read-only; jobs ask for more themselves |
| Fork PR workflows | Approval required for every outside contributor |
| Dependabot | Alerts and security updates on; version updates from `.github/dependabot.yml` |
| Dependency graph | Gradle dependencies submitted by `.github/workflows/dependency-graph.yml` on every push to `main` (GitHub can't read Gradle builds itself; without it there are no alerts for the Android dependencies) |
| Code scanning | CodeQL from `.github/workflows/codeql.yml` (not default setup: Kotlin needs a traced build) |
| Secret scanning | On, with push protection |
| Private vulnerability reporting | On (see `SECURITY.md`) |

Possible next step: move the four signing secrets from repository secrets
into a `release` environment that only `main` can deploy to, and add
`environment: release` to the build job. The workflow already keeps them
away from PRs and other branches; the environment would enforce that in
GitHub itself. Secrets can't be read back, so this means pasting them in
again.
