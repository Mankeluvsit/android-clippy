# Clipboard Drive Sync

Android clipboard manager that watches clipboard changes, stores a local history, and synchronises the latest entries to the user's Google Drive `AppData` folder. A companion web viewer (static HTML) reads the same JSON document so you can inspect and copy clipboard items from any browser.

## Repo Structure

- `android-app/` – Jetpack Compose Android client with WorkManager-based Drive sync stubs.
- `web-viewer/` – Static web page that uses Google Identity Services to fetch `clipboard.json` from Drive.
- `docs/architecture.md` – High-level design, data model and flows.

## Android App Highlights

- Foreground `ClipboardWatcherService` captures `ClipData` changes and writes to a DataStore-backed repository.
- `ClipboardSyncWorker` merges local history with Drive contents and uploads JSON payloads (stubbed networking hooks in `DriveTransport`).
- `DriveAuthManager` handles Google Sign-In with the `drive.appdata` scope; `DriveSyncManager` orchestrates up/down sync.
- Compose UI provides history view, Drive sign-in/out, manual sync trigger, and watcher toggles.

### TODO hooks in Drive integration

The stubs in `DriveTransport` intentionally leave HTTP requests unimplemented to keep secrets out of the repo. Replace the `TODO` blocks with Retrofit/OkHttp calls to:

- `GET https://www.googleapis.com/drive/v3/files` (list `appDataFolder` contents)
- `POST https://www.googleapis.com/drive/v3/files` (create JSON file)
- `PATCH https://www.googleapis.com/upload/drive/v3/files/{id}?uploadType=media` (upload JSON)
- `GET https://www.googleapis.com/drive/v3/files/{id}?alt=media` (download JSON)

## Google Cloud Setup

1. **Create a Google Cloud project** (or reuse an existing one).
2. **Enable APIs**
   - Google Drive API
   - Google People API (optional but recommended for profile info)
3. **Configure OAuth Consent Screen**
   - Publishing status: Internal (testing) or External.
   - Add `https://www.googleapis.com/auth/drive.appdata` to scopes.
4. **Create OAuth credentials**
   - **Android client ID**
     - Package name: `com.example.clipboardsync` (update if you change the package).
     - SHA-1 fingerprint: from your debug/release keystore (`keytool -list -v -keystore ~/.android/debug.keystore`).
   - **Web client ID**
     - Use for both the Android ID token request and the web viewer.
5. **Populate client IDs**
   - Android: Edit `android-app/app/build.gradle.kts` `buildConfigField("GOOGLE_WEB_CLIENT_ID", "...")` with the Web client ID.
   - Web viewer: Update `CLIENT_ID` constant in `web-viewer/index.html`.
6. **(Optional) Restrict OAuth scopes**: keep only `drive.appdata` to limit access to Drive.

## Running the Android App

1. Open `android-app` in Android Studio (Giraffe or newer).
2. Sync Gradle; install necessary SDK components.
3. Replace placeholder client IDs and implement Drive API calls in `DriveTransport`.
4. Build & run on a device (API 26+ recommended; background clipboard access is limited on Android 10+).
5. Grant notification permission when prompted to keep the foreground service active.
6. Use the UI to sign in with Google, enable clipboard watching, and trigger manual syncs.

## Web Viewer

1. Serve `web-viewer/index.html` locally (e.g. `npx http-server web-viewer`).
2. Replace the `CLIENT_ID` placeholder with your Google OAuth Web client ID.
3. Sign in, grant access to Drive AppData, and press **Refresh** to fetch clipboard history.
4. Use the **Copy** action beside any entry to put its content on the browser clipboard.

## Security Considerations

- App stores clipboard data locally using `DataStore`; replace with encrypted storage for production.
- Drive scope is restricted to `AppData` so neither client sees the user's general Drive documents.
- Consider redaction rules for sensitive clipboard content before syncing.
- Always keep OAuth credentials and tokens out of source control.

## Next Steps

- Finish Drive HTTP implementation and add automated tests.
- Support binary payloads (images) via base64 encoding.
- Add edit/delete actions in both Android UI and web viewer.
- Trigger near-real-time updates with FCM or Drive push notifications.