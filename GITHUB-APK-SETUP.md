# Mercer Messenger — GitHub APK build

This repository is prepared so GitHub Actions can build an Android APK without requiring Android Studio on your computer.

## 1. Upload to GitHub

Create a repository and upload the project **contents**, keeping `android/` and `.github/workflows/build-apk.yml` exactly where they are.

## 2. Set the web-app URL

The APK opens the hosted Mercer Messenger web app because Firebase Authentication and web push require a real HTTPS origin.

Recommended GitHub repository variable:

- Name: `WEB_APP_URL`
- Value: your real HTTPS Mercer Messenger URL, for example `https://your-domain.example/`

If the variable is not set, the build defaults to `https://mercer-messenger.web.app/`.

## 3. Build

Open **Actions → Build Mercer Messenger APK → Run workflow**.

Every successful build creates an artifact named `Mercer-Messenger-10.5.6-debug` and, on the `main` branch, a GitHub Release containing `app-debug.apk`.

## 4. Fingerprint / biometric

The Android APK contains a native Android BiometricPrompt. When the user chooses **Use fingerprint / device credentials**, Android handles fingerprint/face/device-PIN verification. The app never receives or stores the fingerprint itself.

The web version keeps its WebAuthn fallback.

## 5. Push notifications / FCM

For native FCM, create an **Android app** in the same Firebase project with package name:

`com.mercer.messenger`

Download its real `google-services.json`. Do **not** commit that file to a public repository.

Create a GitHub Actions secret:

- Name: `GOOGLE_SERVICES_JSON_B64`
- Value: base64-encoded contents of `google-services.json`

Linux/macOS:

```bash
base64 -w 0 google-services.json
```

macOS alternative:

```bash
base64 google-services.json | tr -d '\n'
```

The workflow injects the secret only during the build. If the secret is absent, the APK still builds; native FCM is simply not configured.

### Important

The existing Mercer web app's OneSignal/Web Push setup remains in the web layer. Native FCM is an additional Android channel. To target native FCM tokens from your backend, store the device token for the signed-in Firebase user and send through Firebase Admin/FCM.

## 6. GitHub failure prevention

- No Gradle wrapper is required; GitHub Actions installs Gradle 8.9.
- Android SDK platform/build tools are installed by the workflow.
- Missing `google-services.json` does **not** fail the build.
- `WEB_APP_URL` is configurable as a repository variable.
