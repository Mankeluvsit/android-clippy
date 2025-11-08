# Cloud Clipboard (Android + Web)

A cross-platform clipboard manager that stores snippets in Google Drive. The Android companion app captures text copied on-device and syncs it to Drive, while a lightweight web viewer lets you browse and copy those snippets from any browser session.

## System Architecture

- **Storage**: A single JSON file (`clipboard.json`) kept in a dedicated `Cloud Clipboard` folder in the user's Google Drive. The file holds an ordered list of clipboard entries (`text`, `mimeType`, `createdAt`, `deviceName`).
- **Android App**: Listens to clipboard updates while in the foreground, persists new items locally, and periodically syncs the Drive file (create if missing, append new entries, trim history).
- **Web Viewer**: Static site using Google Identity Services + Drive REST API to read (and optionally download/copy) the shared clipboard file from Drive.
- **Conflict Handling**: Last-write-wins with optimistic locking. Both clients download the latest JSON, merge local changes, and upload with revision IDs to avoid silent overwrites.
- **Security**: All requests go directly from client apps to Google APIs using OAuth tokens scoped to `DriveScopes.DRIVE_FILE`. Data never touches third-party servers.

```
┌────────────────────────┐          ┌──────────────────────────┐
│        Android          │          │          Web             │
│  Clipboard Listener     │          │   Google Identity Auth   │
│  Local Cache (Room)     │◀─────────┤  Drive REST API Client   │
│  Drive Sync Worker      │          │  Clipboard Table UI      │
└──────────▲──────────────┘          └──────────▲───────────────┘
           │                                     │
           │  OAuth 2.0 + Drive REST API         │
           ▼                                     ▼
                ┌────────────────────────────┐
                │   Google Drive (User)      │
                │   Folder: Cloud Clipboard  │
                │   File: clipboard.json     │
                └────────────────────────────┘
```

## Key Features

- Auto-capture plain-text clipboard entries on Android (while the app or its foreground service is active).
- View, search, and copy the synchronized clipboard history on Android and the web.
- Drive-backed history with configurable item limit (default 100).
- Manual "Sync now" control and pull-to-refresh on both clients.
- Optional encryption-at-rest toggle (AES-256 with user passphrase) handled client-side.

## Getting Started

### 1. Google Cloud Project Setup

1. Create (or reuse) a Google Cloud project.
2. Enable the **Google Drive API**.
3. Create OAuth client IDs:
   - **Android**: package name `com.example.cloudclipboard` (adjust as needed) + SHA-1 signing cert.
   - **Web**: origin matching where you will host the viewer (e.g., `http://localhost:5173` or your Netlify domain).
4. Download the Android `client_secret.json` and place it in `android/app/src/main/res/raw/client_secret.json`.
5. Note the web `client_id` for the viewer configuration.

### 2. Android App (Kotlin + Jetpack Compose)

- Minimum SDK 26 / Target SDK 35.
- Core dependencies:
  - `androidx.activity:activity-compose`
  - `androidx.lifecycle:lifecycle-runtime-ktx`
  - `androidx.room:room-ktx`
  - `com.google.android.gms:play-services-auth`
  - `com.google.api-client:google-api-client-android`
  - `com.google.http-client:google-http-client-gson`
  - `kotlinx-serialization-json`

#### Module Highlights

- `ClipboardMonitor`: Wraps `ClipboardManager` into a cold `Flow` emitting new text clips.
- `ClipboardDao` + `ClipboardDatabase`: Local cache with Room; stores pending sync state.
- `DriveServiceHelper`: Builds a Drive client from the signed-in account and exposes `ensureFolder`, `pullEntries`, `pushEntries`.
- `ClipboardSyncWorker`: Uses `WorkManager` for periodic and on-demand syncs (handles optimistic merging).
- `MainActivity`: Compose UI showing history, sign-in button, sync controls, settings.

The full skeleton lives in `android/` (see folder for Gradle config, manifest, and Kotlin sources).

### 3. Web Viewer (Vite + Vanilla TS)

- Static bundle served from any HTTPS host.
- Uses Google Identity Services for implicit OAuth flow and `gapi.client.drive` to fetch the clipboard file.
- Provides searchable table, copy-to-clipboard buttons, and "Refresh" action.
- Optional toggle to decrypt entries when encryption is enabled (prompts user for passphrase).
- Setup:
  1. Copy `web/.env.example` to `web/.env` and fill in your web OAuth client ID + API key.
  2. `cd web && npm install`.
  3. `npm run dev` for local testing (http://localhost:5173) or `npm run build` for deployment.

Source lives in `web/` with a simple Vite setup (`npm install && npm run dev`).

### 4. Data Model

```json
{
  "version": 1,
  "items": [
    {
      "id": "uuid-v4",
      "text": "Example snippet",
      "mimeType": "text/plain",
      "createdAt": "2024-11-08T12:34:56Z",
      "deviceName": "Pixel 8 Pro",
      "encrypted": false
    }
    // ...
  ]
}
```

Clients merge by concatenating any locally queued items whose `createdAt` is newer than the latest item fetched, then trimming to the configured max.

### 5. Background & Permissions Caveats

- Android 10+ restricts clipboard access for background apps. Run a foreground service with an ongoing notification to watch the clipboard reliably.
- Encryption is optional because Google Drive already encrypts data at rest, but you might need client-side encryption for extra privacy.
- No backend server is required, but you must handle OAuth client IDs securely (web viewer must be served over HTTPS).

### 6. Next Steps

- Import the Android module into Android Studio, add your `google-services.json`, and run on device.
- Configure your OAuth consent screen (external testing or internal).
- Update placeholders for package name, client IDs, and copy limit to match your needs.
- Deploy the `web/` bundle to a static host (Firebase Hosting, GitHub Pages with custom domain + HTTPS, etc.).

---

See `docs/` for deeper dive topics (sync flow diagrams, encryption key handling, testing strategies).