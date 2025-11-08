# 🚀 Quick Setup Guide

## Step-by-Step Setup (30 minutes)

### ⚡ Step 1: Google Cloud Console (10 min)

1. Go to https://console.cloud.google.com/
2. Create a new project
3. Enable **Google Drive API**
4. Create credentials:
   - **Android OAuth Client**: 
     - Package: `com.cloudclipboard`
     - Get SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   - **Web OAuth Client**:
     - Origin: `http://localhost:8000`
   - **API Key**: Restrict to Google Drive API

### 📱 Step 2: Android App (10 min)

```bash
cd android
# Open in Android Studio
# Build > Make Project
# Run > Run 'app'
```

1. Open app on your device
2. Sign in with Google
3. Tap "Start Monitoring"
4. Copy some text - it will sync automatically!

### 🌐 Step 3: Web App (5 min)

```bash
cd web
# Edit app.js - add your credentials:
# - CLIENT_ID (from Web OAuth Client)
# - API_KEY (from API Key)

# Serve locally:
python3 -m http.server 8000
# OR
npx http-server -p 8000
```

Open http://localhost:8000 and sign in!

### ✅ Test It!

1. Copy text on Android → Should appear in web viewer
2. Click "Refresh" on web to see latest items
3. Click "Copy" on web to copy items back

## 🔥 Common Issues

| Problem | Solution |
|---------|----------|
| Android sign-in fails | Check SHA-1 fingerprint in Google Console |
| Web auth fails | Verify Client ID and authorized origins |
| No items showing | Use same Google account on both platforms |
| App crashes | Enable Google Drive API in Cloud Console |

## 📞 Need Help?

Check the full README.md for detailed troubleshooting and advanced configuration.

---

**You're all set! Start syncing your clipboard across devices! 🎉**
