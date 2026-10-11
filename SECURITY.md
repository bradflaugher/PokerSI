# Security

PokerSI is distributed as a signed APK from this repository's GitHub
Releases and on Google Play. The Play build is the `pokersi.aab` from the
same CI run.

## Reporting a vulnerability

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/bradflaugher/PokerSI/security/advisories/new)
rather than opening a public issue.

A crafted model file that crashes or misbehaves in llama.cpp itself is
best reported upstream to [llama.cpp](https://github.com/ggml-org/llama.cpp/security)
as well; tell us too, so we can pick up the fix.

## Verifying a release

Each release APK is built by GitHub Actions from the `main` branch and
published alongside a `pokersi.apk.sha256` checksum. Verify a download with:

```sh
sha256sum -c pokersi.apk.sha256
```

Each APK and bundle also carries a signed build provenance attestation,
which proves it was built by this repository's CI from a specific commit:

```sh
gh attestation verify pokersi.apk --repo bradflaugher/PokerSI
```

## Design notes

- **Network: model downloads only.** The app holds `INTERNET` for one
  purpose: downloading a model the player picked from the built-in catalog,
  over HTTPS from Hugging Face. The catalog (file names, URLs, sizes) is
  compiled into the app, not fetched. No analytics, ads, accounts, crash
  SDKs or remote configuration; nothing about the player or their games is
  sent anywhere. Downloads run in WorkManager, which is why the app also
  holds `POST_NOTIFICATIONS` (asked at runtime, for the progress
  notification) and the foreground-service permissions a long download
  needs.
- **Model files are untrusted input.** A GGUF file is parsed by llama.cpp's
  native C/C++ loader, whether it came from the catalog or from a folder the
  player picked. Native inference runs **in the app's own process**, so a
  malicious or corrupt model that triggers a memory-safety bug in llama.cpp
  runs with the app's privileges: its private storage (games, settings,
  downloaded models) and the folders the player granted. It has no other
  permission to abuse beyond `INTERNET`. We keep the llama.cpp submodule
  current (Dependabot watches it); only load GGUF files you trust.
- **Model output is untrusted input too.** Each reply is constrained by a
  GBNF grammar to a small JSON object, then parsed and clamped to a legal
  poker action (`LegalActions.coerce`). Anything else, or no answer in
  time, hands the decision to House Bot. The model's chat line is shown as
  plain text, never interpreted. Opponents only ever see what their seat
  may know.
- **Your own models.** A folder of GGUF files is opened through the Storage
  Access Framework: the app gets read access to that folder only (no
  storage permission) and loads files by file descriptor without copying
  them.
- Cloud backups are off: `android:allowBackup="false"`.
- The only exported component is the launcher activity.
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, signing secrets only reach builds of `main`, Dependabot keeps
  dependencies, action pins and the llama.cpp submodule current, and CodeQL
  scans the Kotlin sources, the workflows and the Python scripts. The C/C++
  is almost all llama.cpp and is not scanned here.
