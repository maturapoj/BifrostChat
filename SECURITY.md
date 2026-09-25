# Security Policy

## Reporting a vulnerability

Please report security issues privately via
[GitHub private vulnerability reporting](https://github.com/maturapoj/BifrostChat/security/advisories/new)
rather than opening a public issue.

## Handling of the API key

- The key is read from `local.properties`, which is gitignored and never committed.
- It is compiled into `BuildConfig`, so it can be extracted from any APK built with it.
  Treat debug builds as secret and do not distribute them. A production app should
  proxy requests through its own backend instead of shipping a key.
- The app only allows HTTPS (`network_security_config.xml`) and disables Android backup.
- CI runs [gitleaks](https://github.com/gitleaks/gitleaks) over the full git history on every push.

If a key is ever committed, rotate it immediately. Deleting it in a later commit is not enough,
because it stays in the git history.
