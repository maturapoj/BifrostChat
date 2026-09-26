# Contributing to TokenFlow

Thanks for helping. Bug reports, fixes and small focused features are welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Keep pull requests focused: one change per PR is easier to review.

## Development setup

Requirements: Android SDK (compileSdk 36) and JDK 17+.

```sh
cp local.properties.example local.properties   # set sdk.dir
./gradlew installDebug
```

Set an endpoint in the app's **Settings**, or, for debug builds only, preload one in
`local.properties` with `tokenflow.baseUrl` and `tokenflow.apiKey`. Never commit a real key:
`local.properties` is gitignored and CI scans every push with gitleaks.

## Checks

```sh
./gradlew testDebugUnitTest            # fast, no device
./gradlew connectedDebugAndroidTest    # needs a device or emulator
```

CI runs both, plus the secret scan, on every push and pull request.

## Code conventions

- **Layers:** `domain` is plain Kotlin (no Android, OkHttp or serialization imports); `data`
  implements domain interfaces; `presentation` talks to use cases and repository interfaces only.
- **Where logic goes:** rules go in a use case (`ChatUseCase`, `SessionUseCase`); a call that just
  passes through to a repository can call the repository interface directly.
- **Errors:** cross layers as `ChatError`, never as transport exceptions. Add a string (English and
  Thai) for any new kind.
- **UI:** screens have a stateless `…Content(state, onIntent)` so they can be previewed and tested.
  User-visible text goes in `strings.xml` (`values` and `values-th`).
- **Tests:** add a test that fails without your change. Use virtual time (`runTest`,
  `testScheduler.timeSource`) rather than real delays.
- **Database:** changing an entity means bumping the Room version, committing the new schema from
  `app/schemas/`, and adding a migration test.

## Releases (maintainers)

```sh
git tag v1.0.0 && git push origin v1.0.0
```

A tag like `v1.2.3` builds a signed, minified APK and publishes it as a GitHub Release with
generated notes. The version comes from the tag: `versionName` is `1.2.3` and `versionCode` is
`10203` (major × 10000 + minor × 100 + patch), so a higher version always installs over a lower
one. Minor and patch go up to 99; other tag shapes are rejected. Local builds are `0.0.0-dev`.

The workflow needs these repository secrets:

| Secret | Value |
| --- | --- |
| `TOKENFLOW_KEYSTORE_BASE64` | `base64 -i release.keystore` |
| `TOKENFLOW_KEYSTORE_PASSWORD` | keystore password |
| `TOKENFLOW_KEY_ALIAS` | key alias |
| `TOKENFLOW_KEY_PASSWORD` | key password |

Create the keystore once with `keytool -genkeypair -v -keystore release.keystore -keyalg RSA
-keysize 4096 -validity 10000 -alias tokenflow` and keep it out of the repository. Back it up
with its passwords somewhere safe (e.g. a password manager): if it is lost, users can't update
over an installed version and have to reinstall, losing their chats.
