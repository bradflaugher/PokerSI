# Maintaining PokerSI

How to keep this repository current and its supply chain locked down.
Nothing here needs special tools beyond `gh` and the Android SDK (with the
NDK and CMake for the native build).

## The weekly routine

Dependabot opens update PRs every week, and only once a release is at least
3 days old (a hijacked release has time to be pulled first):

- **One PR for every GitHub Action bump.** Actions are pinned to commit
  SHAs, and Dependabot moves the SHA and the `# vX.Y.Z` comment together.
- **One PR for minor and patch library bumps** (AndroidX in its own group).
- **One PR per major version**, so its release notes get a read first.
- **llama.cpp submodule bumps**, when the fork branch it tracks moves (see
  below).

Merge a PR (squash) once its checks are green. The `Main` ruleset requires
`build`, `Unit tests`, `Android lint`, `Analyze (java-kotlin)`, `Analyze (actions)`, `Analyze (python)` to pass, so a red PR can't be merged by accident. Dependabot
security updates skip the cooldown and can arrive any day; treat those first.

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

Every push to `main` builds a signed APK and Play bundle in
`.github/workflows/build-release.yml`. The job attests their build provenance and
replaces the single GitHub release. The `versionCode` is the run number.
The same run's `Google Play` job uploads the bundle and `mapping.txt` to
**Closed testing - Alpha**, with
`fastlane/metadata/android/en-US/changelogs/default.txt` as the release
notes, and syncs the store listing from `fastlane/metadata/android`: text,
icon, feature graphic and screenshots (images Play already has are skipped
by checksum). The `store listing` check on every pull request
(`.github/scripts/check_store_listing.py`) keeps the text, image sizes and
formats, and screenshot counts inside Play's limits. The job runs fastlane `supply`
(pinned in `Gemfile.lock`) on the runner's own Ruby, signed in as the Google
Cloud service account
`github-play-publisher@play-publishing-511400.iam.gserviceaccount.com`,
whose JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the
`google-play` environment, which only `main` can deploy to.

To ship a tested build to production, run **Actions › Promote to
production** (`play-promote.yml`) with action `promote`. It promotes the
release on Closed testing - Alpha as is, release notes included; a rollout
below 1 starts a staged rollout. To widen or finish a staged rollout, run it
again with action `update rollout` and a larger fraction (1 finishes it);
it refuses unless production really has a staged rollout in progress, so
it can never publish a draft.
Play refuses until the app has production access.

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
policy Play links to is <https://bradflaugher.com/privacy/pokersi/>, in the
[bradflaugher.com](https://github.com/bradflaugher/bradflaugher.com) repository
(`privacy/pokersi/index.html`); keep it in step with what the app does
(network use, permissions, storage).

## Play Console settings that live outside the repository

Set when the app was created (October 2026); check or recreate them here:

| Setting | Value |
|---|---|
| App | Free game, category Card; contact brad@bradflaugher.com and the GitHub repository |
| Privacy policy URL | `https://www.bradflaugher.com/privacy/pokersi/` (the same page; the `www` form because Play cached a 404 for the bare URL from before the page existed) |
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
| Actions permissions | GitHub-owned actions plus `android-actions/setup-android`, `gradle/actions/*` and `reactivecircus/android-emulator-runner`; full-SHA pinning required |
| Google Play API access | Service account `github-play-publisher@play-publishing-511400.iam.gserviceaccount.com` (Google Cloud project `play-publishing-511400`), invited in Play Console › Users and permissions with account-wide: view app information, release to production, release to testing tracks, manage store presence. Its JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play` environment |
| Environment `google-play` | Deployable from `main` only; holds `PLAY_SERVICE_ACCOUNT_JSON`; the `Google Play` job and `play-promote.yml` run in it |
| Default `GITHUB_TOKEN` | Read-only; jobs ask for more themselves |
| Fork PR workflows | Approval required for every outside contributor |
| Dependabot | Alerts and security updates on; version updates from `.github/dependabot.yml` |
| Code scanning | CodeQL from `.github/workflows/codeql.yml` (not default setup: Kotlin needs a traced build) |
| Secret scanning | On, with push protection |
| Private vulnerability reporting | On (see `SECURITY.md`) |

Possible next step: move the four signing secrets from repository secrets
into a `release` environment that only `main` can deploy to, and add
`environment: release` to the build job. The workflow already keeps them
away from PRs and other branches; the environment would enforce that in
GitHub itself. Secrets can't be read back, so this means pasting them in
again.
