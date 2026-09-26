# Security Policy

## Reporting a vulnerability

Please report security issues privately via
[GitHub private vulnerability reporting](https://github.com/maturapoj/TokenFlow/security/advisories/new)
rather than opening a public issue.

## How the API key is handled

- The endpoint and API key are entered in the app's Settings. The key is encrypted with
  AES-256-GCM using a key held in the Android Keystore, and stored in DataStore.
- Release builds contain no endpoint or key. Debug builds may preload one from
  `local.properties` (gitignored) for development; don't share debug APKs built that way.
- The app only allows HTTPS, except plain HTTP to `localhost`, `127.0.0.1` and `10.0.2.2` for
  local model servers. Android backup is disabled.
- CI runs [gitleaks](https://github.com/gitleaks/gitleaks) over the full git history on every push.

If a key is ever committed, rotate it immediately. Deleting it in a later commit is not enough,
because it stays in the git history.
