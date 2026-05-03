# AMap Companion signing

This project currently uses a local debug keystore so future builds can upgrade
the installed `com.autonavi.amapclone.companion` package without uninstalling it.

## Keystore

- File: `debug.keystore`
- Store password: `android`
- Key alias: `androiddebugkey`
- Key password: `android`
- Owner / issuer: `CN=Android Debug, O=Android, C=US`
- Serial number: `67f4190353dc9823`
- Valid from: `2026-05-03 19:02:32 CST`
- Valid until: `2053-09-18 19:02:32 CST`

## Certificate fingerprints

- SHA-256: `B6:DE:4C:5D:97:91:DB:21:B3:6E:BA:65:F2:23:F8:99:37:1F:D5:D6:3E:E0:CA:24:75:47:7D:D2:69:F5:79:53`
- SHA-1: `DA:53:81:BC:2A:32:54:18:E8:AC:82:D3:F7:69:4D:E4:18:45:1B:02`
- MD5: `C3:19:F9:54:AC:9B:B0:77:49:91:CB:24:9F:45:A7:8D`

## Signing command

Use the same keystore when rebuilding:

```powershell
& 'C:\Users\zuoqirun\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat' sign `
  --ks debug.keystore `
  --ks-pass pass:android `
  --key-pass pass:android `
  --out amap_companion_signed.apk `
  build\amap_companion_aligned.apk
```

Verify:

```powershell
& 'C:\Users\zuoqirun\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat' verify --print-certs amap_companion_signed.apk
```

Expected APK signer SHA-256:

```text
b6de4c5d9791db21b36eba65f223f899371fd5d63ee0ca2475477dd269f57953
```

## Device install note

On 2026-05-03, the previously installed package had a different signature, so it
was uninstalled before installing this build. Future agents should keep using
`debug.keystore` above to avoid another signature mismatch.
