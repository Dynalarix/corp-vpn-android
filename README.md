# Corp VPN for Android

Corporate VPN client. Employees import the personal `vpn://` keys they get from the
company portal; the app lists the servers, measures ping and connects to the best one
over WireGuard or AmneziaWG.

## Layout

- `core/` – plain Kotlin, no Android: decoding `vpn://` keys and WireGuard configs,
  grouping keys by server. Unit-tested with `./gradlew :core:test`.
- `app/` – the Android app (Jetpack Compose, dark theme).
- `third_party/amneziawg-android/` – git submodule with Amnezia's AmneziaWG library
  (Apache-2.0). Its `tunnel` module runs both WireGuard and AmneziaWG tunnels and is
  built from source (Go + NDK), because it isn't published as a package.

## Building

Clone with submodules: `git clone --recurse-submodules ...` (or
`git submodule update --init --recursive` in an existing clone).

Needs JDK 17, the Android SDK and NDK 26.1.10909125 (Android Studio installs these).
`./gradlew :app:assembleDebug` builds a debug APK (app id `com.corpvpn.app.debug`).
The first build also downloads a Go toolchain for the tunnel library.

On a machine without the Android SDK, the `core` module builds on its own:
`./gradlew -p core test`.

Every push and pull request is built by GitHub Actions; the debug APK is attached to
each run as an artifact.

## Status

1. Import keys – done
2. Server list with ping and auto-pick – done
3. Connect over WireGuard / AmneziaWG – done
