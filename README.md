# AMap Companion

AMap Companion is a lightweight Android overlay for AMap Auto broadcast data.
It listens for navigation/cruise broadcasts and displays turn hints, traffic
light countdowns, lane information, ETA, road alerts, and protocol details in a
movable floating window.

## Features

- Movable floating overlay with tap-to-open behavior.
- User-selectable target package for protocol requests.
- Navigation and cruise status display.
- Dedicated lane information panel using AMapAuto `KEY_TYPE=13012` lane icon IDs.
- Capsule-shaped traffic-light countdowns.
- ETA, destination name when broadcast by AMap, speed, road alerts, camera,
  road type, and limited location/status details.

## Build

This project is intentionally small and does not use Gradle. Use the bundled
Android SDK tools on this Windows machine:

```powershell
.\build.ps1
```

The script produces `amap_companion_signed.apk` using `debug.keystore`.

## Signing

See `SIGNING.md`. Keep using `debug.keystore` to allow upgrade installs over
the current device build.
