## System Overview

An Android clipboard manager captures clipboard events locally, persists entries to encrypted SharedPreferences, and synchronizes them to the user's Google Drive `AppData` folder. A single JSON file (`clipboard.json`) stores an append-only array of clipboard entry records. A lightweight web viewer reads the same file via the Google Drive REST API to present the clipboard history in a browser.

## Components

- **Android App**
  - `ClipboardWatcher`: foreground service that registers a `ClipboardManager.OnPrimaryClipChangedListener`. Each new item is normalized into a `ClipboardEntry`.
  - `LocalRepository`: Room database + EncryptedSharedPreferences for cached entries and metadata (e.g., last sync time, Drive file ID).
  - `DriveSyncManager`: wraps Google Sign-In, acquires OAuth token with `https://www.googleapis.com/auth/drive.appdata` scope, and handles CRUD requests against Drive via `Drive` REST API (`retrofit2` client).
  - `SyncWorker`: WorkManager background job triggered on new clipboard events, network availability, or periodic schedule to push local changes and pull remote updates.
  - `ComposeUI`: Jetpack Compose screens for viewing clipboard history, enabling/disabling auto-sync, manual refresh, etc.

- **Google Drive Storage**
  - `AppData` special folder stores `clipboard.json`.
  - File structure:
    ```json
    {
      "version": 1,
      "updatedAt": "2025-11-08T10:00:00Z",
      "entries": [
        {
          "id": "uuid",
          "content": "string or base64 data",
          "type": "text/plain",
          "createdAt": "ISO-8601",
          "source": "android|web",
          "device": "Pixel 7",
          "syncedAt": "ISO-8601"
        }
      ]
    }
    ```

- **Web Viewer**
  - Static single-page app using Google Identity Services for web to obtain OAuth token with the same `drive.appdata` scope.
  - Fetches `clipboard.json` via Drive REST API, renders entries, supports manual refresh and deletion.

## Data Flow

1. **Capture**
   - Foreground service listens for clipboard changes.
   - New `ClipData` is sanitized (text only initially) and stored locally.
2. **Sync Up**
   - `SyncWorker` reads unsynced entries and merges them into the in-memory representation of `clipboard.json`.
   - Drive API `files.update` uploads JSON blob (with exponential backoff on errors).
3. **Sync Down**
   - On app start or periodic refresh, `SyncWorker` downloads `clipboard.json`, merges new remote entries into local cache.
4. **Conflict Resolution**
   - File content merges performed client-side; latest `updatedAt` wins.
   - Each entry is de-duplicated by UUID.
5. **Web Viewing**
   - User authenticates in browser, the app loads the JSON file and displays the entries.
   - Optional actions: delete entry (updates JSON and uploads back), copy to clipboard.

## Security & Privacy

- Use EncryptedSharedPreferences / SQLCipher for local cache.
- Limit Drive scope to `AppData` to prevent access to entire Drive.
- Provide opt-in toggle for clipboard monitoring to respect privacy.
- Consider optionally redacting sensitive entries (password detection heuristics).

## Offline Support

- Clipboard events always stored locally first.
- Sync worker queues work until network available.
- Local UI surfaces pending sync status per entry.

## Extensibility

- Support images by base64 encoding and `type` metadata.
- Add Chrome extension viewer using same Drive file.
- Introduce push notifications via FCM for near real-time web updates.
