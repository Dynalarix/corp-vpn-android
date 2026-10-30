# Corp VPN for Android

Corporate VPN client. Employees import the personal `vpn://` keys they get from the
company portal; the app lists the servers, measures ping and connects to the best one
over WireGuard or AmneziaWG.

## Layout

- `core/` – plain Kotlin, no Android: decoding `vpn://` keys and WireGuard configs,
  grouping keys by server. Unit-tested with `./gradlew :core:test`.
- `app/` – the Android app (Jetpack Compose, dark theme).

## Building

Needs JDK 17 and the Android SDK (Android Studio sets this up). `./gradlew :app:assembleDebug`
builds a debug APK. Without an Android SDK, only `:core` is built.

Every push and pull request is built by GitHub Actions; the debug APK is attached to
each run as an artifact.

## Status

1. Import keys – done
2. Server list with ping and auto-pick – next
3. Connect over WireGuard / AmneziaWG
