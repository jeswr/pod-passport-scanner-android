# Pod Passport Scanner — Android

> ⚠️ **Experimental.** This is a research/reference implementation, not a
> production product. APIs, the hand-off contract, and the UI may change without
> notice; it has not been security-audited or certified for handling real
> identity documents. Use it to explore passport-to-pod credential issuance on
> [Solid](https://solidproject.org), not as-is in production.

Android app that reads a passport's NFC chip (ICAO 9303 eMRTD) and hands the
chip bundle to the **Credential Issuer** web app, which performs passive
authentication and mints a verifiable credential into the user's Solid pod.
This is the Android counterpart of the iOS app
[`jeswr/pod-passport-scanner`](https://github.com/jeswr/pod-passport-scanner)
and targets the **same fixed hand-off contract**.

**Privacy:** chip data is sent **only** to the issuer endpoint encoded in the
QR code the user scans — nowhere else. No analytics, no persistence after the
flow completes.

## Flow

1. **Home** — explainer + privacy note.
2. **Scan QR** — CameraX + ML Kit barcode scanning reads the issuer's QR code
   (manual-entry fallback for the same three fields; also the default when
   camera permission is not granted).
3. **MRZ capture** — CameraX + ML Kit text recognition reads the printed
   machine-readable zone; document number / date of birth / expiry derive the
   BAC/PACE key (manual-entry fallback). These values never leave the device.
4. **NFC read** — [jMRTD](https://jmrtd.org) + SCUBA perform PACE with BAC
   fallback and read DG1, SOD and (when present on the chip) DG2, DG11, DG14.
5. **Review** — parsed MRZ, photo, the exact files + destination endpoint that
   will be sent.
6. **Upload** — `PUT` to the issuer endpoint, then "Return to your browser."

## Hand-off contract (fixed — the issuer's `emrtd` adapter implements the other side)

QR code payload (JSON, also enterable manually):

```json
{"v": 1, "endpoint": "<absolute uploadUrl>", "sessionId": "...", "secret": "..."}
```

Upload request:

```
PUT <endpoint>
Authorization: Bearer <secret>
Content-Type: application/json

{"format": "icao-9303-lds1",
 "lds": {"dg1": "<b64>", "dg2": "<b64, optional>", "dg11": "<b64, optional>",
         "dg14": "<b64, optional>", "sod": "<b64>"}}
```

- LDS values are **standard, padded base64**; optional data groups are
  **omitted, not null** (matches the issuer's `emrtdBundleSchema`).
- `204` → success.
- `401` → invalid secret (terminal; rescan QR).
- `410` → session expired (terminal; refresh issuer page).
- Network errors / other statuses → retried with exponential backoff
  (3 attempts; PUT is idempotent).

The bundled sample passport
(`app/src/main/assets/sample_passport/emrtd-bundle.json`) is a **byte-for-byte
copy** (md5 `8a2d114901564e54cbe7a3b013e6708d`) of the issuer-side canonical
test fixture `apps/issuer/test/fixtures/emrtd-bundle.json` (synthetic
"JANE DOE" eMRTD with real LDS TLV structure and a test-CSCA-signed SOD).

## Architecture

NFC and upload sit behind interfaces so the **full flow runs with no
hardware** (the Robolectric Compose flow test drives Home → Done with mocks):

| Interface | Real | Mock |
|---|---|---|
| `ChipReader` | `NfcChipReader` (jMRTD + SCUBA) | `MockChipReader` (bundled sample bundle) |
| `BundleUploader` | `HttpBundleUploader` (`HttpURLConnection`) | `MockBundleUploader` |

- On a device without NFC the mock chip reader is used; in DEBUG builds the
  Home screen has a **"Use sample passport"** toggle.
- The upload is a plain authenticated `PUT` via `HttpURLConnection` — it does
  **not** depend on `jeswr/solid-kotlin`, so it is never blocked on that SDK.
- Kotlin + Jetpack Compose (Material 3), Gradle KTS, JDK 17,
  `minSdk 26` / `compileSdk 35`.

## Build & test

```sh
# Debug APK
./gradlew assembleDebug

# Unit tests + the Robolectric Compose flow test (Home → … → Done, all mocks)
./gradlew testDebugUnitTest
```

Requires a JDK 17 and the Android SDK (platform 35, build-tools 35). Point
`local.properties` `sdk.dir` at your SDK, or set `ANDROID_HOME`.

## Run on a real phone (required for NFC)

NFC chip reading only works on a physical Android device with NFC. Steps:

1. Open the project in Android Studio (or `./gradlew installDebug` with a
   device attached) and run the app.
2. Grant the camera permission when prompted (QR + MRZ scanning).
3. On the NFC step, hold the **top half / back** of the phone (where the NFC
   antenna sits) flat against the closed passport's front cover; remove thick
   cases.

## CI

GitHub Actions (`.github/workflows/ci.yml`): Ubuntu runner, JDK 17, Android
SDK, then `assembleDebug` + `testDebugUnitTest` (unit + Robolectric Compose
flow test — no NFC/camera hardware needed).

## Releasing a signed APK

`.github/workflows/release.yml` builds a **signed release APK** and attaches it
to a GitHub Release on every version tag — anyone can then download and sideload
it (no app store needed). One-time setup:

1. **Generate an upload keystore** (keep it safe — re-signing with a different
   key means existing installs can't update):

   ```sh
   keytool -genkeypair -v -keystore release.keystore \
     -alias podpassport -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Add four repo secrets** (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | `base64 -i release.keystore` (paste the output) |
   | `ANDROID_KEYSTORE_PASSWORD` | the store password from step 1 |
   | `ANDROID_KEY_ALIAS` | `podpassport` |
   | `ANDROID_KEY_PASSWORD` | the key password from step 1 |

   With `gh`:

   ```sh
   R=jeswr/pod-passport-scanner-android
   base64 -i release.keystore | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$R"
   gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$R"   # prompts for the value
   gh secret set ANDROID_KEY_ALIAS --repo "$R" --body podpassport
   gh secret set ANDROID_KEY_PASSWORD --repo "$R"        # prompts for the value
   ```

3. **Cut a release:** `git tag v0.1.0 && git push origin v0.1.0`. The signed APK
   appears under the repo's Releases. (`versionCode` is set from the CI run
   number so each build installs over the previous one.) Local
   `./gradlew assembleRelease` without the keystore still produces the usual
   unsigned APK.

## Licences

- [jMRTD](https://jmrtd.org) — LGPL; [SCUBA](https://scuba.sourceforge.net) —
  LGPL; both pulled from `repo.jmrtd.org`. They depend on Bouncy Castle (MIT).
- ML Kit barcode + text recognition, CameraX, Jetpack Compose — Apache 2.0.
