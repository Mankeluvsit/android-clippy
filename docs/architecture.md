# Cloud Clipboard Architecture Notes

## Google Drive Data Layout

- Folder name: `Cloud Clipboard`
- Primary data file: `clipboard.json`
- JSON schema matches the `ClipboardPayload` Kotlin data class (ordered items array, version flag).
- The Android and web clients both write the same file using OAuth tokens scoped to `DriveScopes.DRIVE_FILE`.

## Sync Flow

1. Client downloads the latest `clipboard.json` along with its `version` metadata.
2. Local pending clipboard items (created while offline) are merged into the payload.
3. Payload is trimmed to the configured limit (100 by default).
4. Client uploads the merged payload with an `If-Match` header bound to the previous version.
5. On success, local items are marked as synced and the cache is reconciled.
6. If a 412 conflict occurs, the client refetches the latest payload and retries.

## Components

- `ClipboardForegroundService`: Foreground service wrapping `ClipboardMonitor`, responsible for capturing clipboard changes and queuing sync work.
- `ClipboardRepository`: Single source of truth coordinating Room and Drive.
- `ClipboardSyncWorker`: WorkManager task that runs sync logic (on-demand and periodic every 15 minutes).
- Web app uses Google Identity Services, the Drive REST API, and the same JSON schema to keep parity with the Android client.

## Security Considerations

- OAuth consent screen must describe clipboard access clearly.
- Optional client-side encryption can be added by encrypting `ClipboardPayload` before upload; all structure stays the same.
- Drive file permissions remain private to the signed-in Google account.
