# Sky Dark

A personal Android weather app built around a minute-by-minute "next hour" rain forecast, a
color-coded 24-hour timeline, and a tap-to-expand week view. No ads, no accounts, no tracking.

Forecast data comes from [Pirate Weather](https://pirateweather.net) (free API key required,
entered in the app's Settings). City search uses Open-Meteo's free geocoding API.
The Radar tab shows the past 2 hours of radar from RainViewer (personal-use API) over OpenFreeMap base maps (OpenMapTiles, OpenStreetMap data), drawn with MapLibre GL JS (bundled in assets, BSD-3 license).

## Getting the app
Every push to `main` builds a signed APK with GitHub Actions and publishes it under
**Releases**. Download the newest `SkyDark-N.apk` there and open it on your phone to install.

The build needs one repository secret, `SIGNING_PASSWORD` (Settings > Secrets and variables >
Actions). Keep the same value forever: it protects the signing key, and Android only installs
updates signed with the same key.

Built by ChickenMyBobbers.
