# Dropi

A simple, friendly blood sugar tracker for Android. Log a reading in three taps, see where it
sits on the range gauge, and keep an eye on today's average and time in range. Readings stay on
the phone.

## Install

Open the [latest release](https://github.com/whoakemosabe/dropi/releases/latest) on your phone
and tap **dropi.apk**. After that, Dropi tells you when there's an update: the banner appears on
its own, **Update** shows what's new, and it downloads and installs from there.

## How it's built

- Kotlin + Jetpack Compose (Material 3), AGP 9 with built-in Kotlin, minSdk 26, targetSdk 36
- Plain SQLite for readings (`dropi.db`), SharedPreferences for settings
- Stored in mmol/L; mg/dL is only for display and entry (1 mmol/L = 18.0182 mg/dL)
- Ranges follow the ADA / international consensus: low < 3.9, urgent low < 3.0, time in range
  3.9–10.0, very high > 13.9 mmol/L; fasting and before-meal target 4.4–7.2, after meals < 10.0
- Updates come from this repo's GitHub Releases, the same flow as F1 Pro

## CI

Every push to `main` runs the unit tests, builds a signed release APK and publishes it as
`v1.0.<run>`. Other branches are build checks. The workflow needs two repository secrets:

- `KEYSTORE_B64` — the release keystore (`dropi-release.p12`), base64-encoded
- `KEYSTORE_PASSWORD` — its password (store and key)

Keep the keystore safe: every update must be signed with the same key to install over the
previous version.

## What's new

`whatsnew.md` holds the people-facing highlights for the next release. They become the
release's **Highlights** and the app's "What's new" window.

## Not a medical device

Dropi is a log. It doesn't diagnose or suggest doses. Follow the plan agreed with your care team.
