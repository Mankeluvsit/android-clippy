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
# ☁️ Cloud Clipboard Manager

A powerful clipboard manager that syncs your clipboard across Android devices using Google Drive, with a beautiful web interface to view and manage your clipboard history.

## 🌟 Features

- **📱 Android App**: Monitors your clipboard in real-time and syncs to Google Drive
- **🌐 Web Viewer**: Beautiful, responsive web interface to view clipboard history
- **☁️ Cloud Sync**: All clipboard content stored securely in your Google Drive
- **🔄 Real-time Updates**: Automatic syncing across all your devices
- **🔍 Search**: Quickly find clipboard items with search functionality
- **📋 One-Click Copy**: Copy any item back to your clipboard instantly
- **🎨 Modern UI**: Clean, Material Design inspired interface

## 📋 Prerequisites

- Android Studio (for building the Android app)
- A Google Cloud Platform account
- Node.js and npm (optional, for serving the web app locally)
- Android device running Android 7.0 (API 24) or higher

## 🚀 Setup Instructions

### Part 1: Google Cloud Console Setup

1. **Create a Google Cloud Project**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select an existing one
   - Note your project ID

2. **Enable Google Drive API**
   - In the Cloud Console, navigate to "APIs & Services" > "Library"
   - Search for "Google Drive API"
   - Click "Enable"

3. **Create OAuth 2.0 Credentials**

   **For Android:**
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android"
   - Enter package name: `com.cloudclipboard`
   - Get your SHA-1 certificate fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Enter the SHA-1 fingerprint and create

   **For Web:**
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Web application"
   - Add authorized JavaScript origins:
     - `http://localhost:8000` (for local testing)
     - Your production domain (if deploying)
   - Add authorized redirect URIs:
     - `http://localhost:8000`
     - Your production domain
   - Save the Client ID

4. **Create API Key**
   - Click "Create Credentials" > "API key"
   - Restrict the key to "Google Drive API" for security
   - Save the API key

### Part 2: Android App Setup

1. **Clone and Open Project**
   ```bash
   cd android
   ```
   Open the `android` folder in Android Studio

2. **Configure Google Services**
   - Download `google-services.json` from Firebase Console (optional but recommended)
   - Place it in `android/app/` directory

3. **Update Build Configuration**
   The app is already configured to use Google Drive API. No code changes needed!

4. **Build and Install**
   - Connect your Android device or start an emulator
   - In Android Studio: Build > Make Project
   - Run > Run 'app'

5. **Using the Android App**
   - Open the app
   - Tap "Sign in with Google"
   - Grant necessary permissions
   - Tap "Start Monitoring"
   - The app will now monitor your clipboard and sync to Google Drive
   - Copy any text on your device to see it sync!

### Part 3: Web App Setup

1. **Configure API Credentials**
   ```bash
   cd web
   ```
   
2. **Update Configuration**
   - Open `app.js`
   - Replace `YOUR_CLIENT_ID` with your Web OAuth Client ID
   - Replace `YOUR_API_KEY` with your API key

   ```javascript
   const CLIENT_ID = 'your-client-id.apps.googleusercontent.com';
   const API_KEY = 'your-api-key';
   ```

3. **Serve the Web App Locally**
   
   Using Python:
   ```bash
   python3 -m http.server 8000
   ```
   
   Or using Node.js (http-server):
   ```bash
   npm install -g http-server
   http-server -p 8000
   ```

4. **Access the Web App**
   - Open your browser and go to `http://localhost:8000`
   - Click "Sign in with Google"
   - Grant permissions
   - View your synced clipboard items!

### Part 4: Deploy Web App (Optional)

#### Deploy to GitHub Pages

1. **Create GitHub Repository**
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/yourusername/cloud-clipboard.git
   git push -u origin main
   ```

2. **Enable GitHub Pages**
   - Go to repository Settings > Pages
   - Select source: main branch / docs folder (or root)
   - Copy the provided URL

3. **Update OAuth Credentials**
   - Add your GitHub Pages URL to authorized JavaScript origins in Google Cloud Console

#### Deploy to Netlify/Vercel

1. **Netlify**
   ```bash
   # Install Netlify CLI
   npm install -g netlify-cli
   
   # Deploy
   cd web
   netlify deploy --prod
   ```

2. **Vercel**
   ```bash
   # Install Vercel CLI
   npm install -g vercel
   
   # Deploy
   cd web
   vercel --prod
   ```

## 📱 Usage

### Android App

1. **Start Monitoring**: Open app and enable clipboard monitoring
2. **Copy Text**: Copy any text on your device
3. **Auto-Sync**: Content automatically syncs to Google Drive
4. **Background Service**: Works even when app is closed
5. **View History**: See recent clipboard items in the app

### Web Viewer

1. **Sign In**: Use the same Google account as your Android device
2. **View Items**: See all synced clipboard content
3. **Search**: Filter items using the search box
4. **Copy**: Click "Copy" to copy any item to your clipboard
5. **Auto-Refresh**: Page refreshes every 30 seconds automatically

## 🔒 Privacy & Security

- All data is stored in **your personal Google Drive**
- Only you have access to your clipboard data
- OAuth 2.0 authentication ensures secure access
- Data is stored in a private folder: `CloudClipboard/clipboard_data.json`
- App only requests minimum necessary permissions

## 🏗️ Project Structure

```
cloud-clipboard/
├── android/                    # Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/cloudclipboard/
│   │   │   │   ├── MainActivity.kt           # Main activity
│   │   │   │   ├── ClipboardMonitorService.kt # Background service
│   │   │   │   ├── GoogleDriveHelper.kt      # Drive API integration
│   │   │   │   ├── ClipboardAdapter.kt       # RecyclerView adapter
│   │   │   │   └── ClipboardItem.kt          # Data model
│   │   │   ├── res/                          # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
└── web/                        # Web viewer application
    ├── index.html              # Main HTML
    ├── styles.css              # Styling
    ├── app.js                  # JavaScript logic
    └── config.example.js       # Configuration template
```

## 🛠️ Technical Details

### Android App
- **Language**: Kotlin
- **Min SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Key Libraries**:
  - Google Drive API v3
  - Google Play Services Auth
  - AndroidX Libraries
  - Kotlin Coroutines

### Web App
- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **APIs**:
  - Google Drive API v3
  - Google Sign-In
- **Features**:
  - Responsive design
  - Real-time updates
  - Material Design inspired UI

## 🐛 Troubleshooting

### Android App

**Problem**: App crashes on sign-in
- **Solution**: Verify OAuth credentials are correctly configured in Google Cloud Console
- Check SHA-1 fingerprint matches your keystore

**Problem**: Clipboard not syncing
- **Solution**: Ensure app has necessary permissions
- Check internet connectivity
- Verify Google Drive API is enabled

### Web App

**Problem**: "Authentication failed" error
- **Solution**: Check Client ID and API Key in `app.js`
- Verify authorized JavaScript origins in Google Cloud Console

**Problem**: No clipboard items showing
- **Solution**: Ensure you're signed in with the same Google account on both Android and web
- Check that Android app has synced at least one item
- Click "Refresh" button to manually update

## 📝 Future Enhancements

- [ ] iOS app support
- [ ] End-to-end encryption
- [ ] Rich text and image support
- [ ] Clipboard categories/folders
- [ ] Cross-device notifications
- [ ] Browser extensions (Chrome, Firefox)
- [ ] Desktop apps (Windows, macOS, Linux)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is open source and available under the MIT License.

## 💡 Tips

- Keep the Android app running in the background for continuous syncing
- Use search feature on web to quickly find old clipboard items
- Limit is set to 100 most recent items to manage storage
- Data is stored as JSON in your Google Drive for easy access

## 🆘 Support

If you encounter any issues or have questions:
1. Check the Troubleshooting section
2. Review Google Cloud Console configuration
3. Verify all permissions are granted
4. Check device internet connectivity

## ⭐ Acknowledgments

- Google Drive API for cloud storage
- Material Design for UI inspiration
- Android community for best practices

---

**Made with ❤️ for productivity enthusiasts**
