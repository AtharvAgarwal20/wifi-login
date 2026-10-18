# BITS Wi-Fi Login

<img src="demo/icon.png" width="30%">

Android app that keeps you logged in to the BITS campus Wi-Fi, so you never have to open the
captive portal again. A fork of [sparshg/wifi-login](https://github.com/sparshg/wifi-login),
rebuilt for the FortiGate portal BITS Pilani now uses.

## Download

Get the latest APK from **[Releases](https://github.com/AtharvAgarwal20/wifi-login/releases/latest)** (Android 8.0+).

If you have the original app from the Play Store, uninstall it: both would try to log in.

## Setup

1. Pick your campus and enter your Wi-Fi username and password. They stay on the phone and are
   excluded from backups.
2. Start the **Auto-Login Service**.
3. Disable battery optimization for the app, so it can also check while the screen is off.
4. Optional: add the **Login** Quick Settings tile to check on demand.

## How it works

- **Pilani (FortiGate):** the app asks `connectivitycheck.gstatic.com` over Wi-Fi. If the portal
  answers instead, it opens the login page (which carries a one-time token), signs in over HTTPS to
  `fw.bits-pilani.ac.in` with its certificate verified, and confirms you're online.
- **Goa (Sophos):** signs in through `campnet.bits-goa.ac.in`, the same way the campus web page does.
- **Hyderabad:** handled if its portal is a FortiGate or the old Sophos one (not yet confirmed).
- **When:** as soon as Wi-Fi connects, when Android flags a captive portal, and when the screen turns
  on; otherwise once a minute while the screen is on and every 15 minutes while it's off.
- **Keepalive:** BITS Pilani sessions end 12 hours after login. With *Session Keepalive* on (the
  default), the app refreshes the session every 4 hours like the portal's own keepalive page does
  (about 6 small requests a day); turn it off in the app if you prefer.
- **Safety:** credentials only go to BITS hosts with valid certificates. If the portal turns the
  password down twice, the app stops and tells you (FortiGate locks accounts after 3 failures);
  it resumes when you change the credentials.

## Troubleshooting

- **VPN** (e.g. Cloudflare WARP / 1.1.1.1): exclude this app from the VPN, or pause it while logging in.
- **Private DNS:** set it to *Automatic*. A custom provider can't resolve the campus firewall.
- **"Wi-Fi login rejected":** fix your username or password in the app.

## Building

JDK 17+ and Android SDK 36: `./gradlew assembleRelease`. Release signing reads a local
`keystore.properties` that isn't in the repository.

## License

GPL-3.0. Original app by [Sparsh Goenka](https://github.com/sparshg).
