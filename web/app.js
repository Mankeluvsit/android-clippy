// Google Drive API configuration
const CLIENT_ID = 'YOUR_CLIENT_ID.apps.googleusercontent.com';
const API_KEY = 'YOUR_API_KEY';
const DISCOVERY_DOCS = ['https://www.googleapis.com/discovery/v1/apis/drive/v3/rest'];
const SCOPES = 'https://www.googleapis.com/auth/drive.file';

let tokenClient;
let accessToken = null;
let clipboardItems = [];
let filteredItems = [];

// Initialize Google API
function gapiLoaded() {
    gapi.load('client', initializeGapiClient);
}

async function initializeGapiClient() {
    await gapi.client.init({
        apiKey: API_KEY,
        discoveryDocs: DISCOVERY_DOCS,
    });
}

function gisLoaded() {
    tokenClient = google.accounts.oauth2.initTokenClient({
        client_id: CLIENT_ID,
        scope: SCOPES,
        callback: handleAuthCallback,
    });
}

function handleAuthCallback(response) {
    if (response.error !== undefined) {
        showToast('Authentication failed');
        return;
    }
    accessToken = response.access_token;
    showSignedInState();
    loadClipboardData();
}

// UI Functions
function showSignedInState() {
    document.getElementById('signInButton').style.display = 'none';
    document.getElementById('userInfo').style.display = 'flex';
    document.getElementById('controls').style.display = 'flex';
    
    // Get user info from Google
    gapi.client.request({
        path: 'https://www.googleapis.com/oauth2/v1/userinfo',
        params: { access_token: accessToken }
    }).then(response => {
        const user = response.result;
        document.getElementById('userName').textContent = user.name;
        document.getElementById('userPhoto').src = user.picture;
    });
}

function showSignedOutState() {
    document.getElementById('signInButton').style.display = 'block';
    document.getElementById('userInfo').style.display = 'none';
    document.getElementById('controls').style.display = 'none';
    document.getElementById('clipboardList').innerHTML = '';
    accessToken = null;
}

function showLoading(show) {
    document.getElementById('loading').style.display = show ? 'block' : 'none';
}

function showEmptyState(show) {
    document.getElementById('emptyState').style.display = show ? 'block' : 'none';
}

function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.add('show');
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Clipboard Functions
async function loadClipboardData() {
    showLoading(true);
    showEmptyState(false);
    
    try {
        // Find the CloudClipboard folder
        const folderResponse = await gapi.client.drive.files.list({
            q: "name='CloudClipboard' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            fields: 'files(id, name)',
            spaces: 'drive'
        });

        if (folderResponse.result.files.length === 0) {
            showEmptyState(true);
            showLoading(false);
            return;
        }

        const folderId = folderResponse.result.files[0].id;

        // Find the clipboard data file
        const fileResponse = await gapi.client.drive.files.list({
            q: `name='clipboard_data.json' and '${folderId}' in parents and trashed=false`,
            fields: 'files(id, name)',
            spaces: 'drive'
        });

        if (fileResponse.result.files.length === 0) {
            showEmptyState(true);
            showLoading(false);
            return;
        }

        const fileId = fileResponse.result.files[0].id;

        // Download the file content
        const contentResponse = await gapi.client.drive.files.get({
            fileId: fileId,
            alt: 'media'
        });

        clipboardItems = JSON.parse(contentResponse.body);
        filteredItems = [...clipboardItems];
        displayClipboardItems();
        
    } catch (error) {
        console.error('Error loading clipboard data:', error);
        showToast('Failed to load clipboard data');
        showEmptyState(true);
    } finally {
        showLoading(false);
    }
}

function displayClipboardItems() {
    const listElement = document.getElementById('clipboardList');
    
    if (filteredItems.length === 0) {
        showEmptyState(true);
        listElement.innerHTML = '';
        return;
    }

    showEmptyState(false);
    document.getElementById('itemCount').textContent = `${filteredItems.length} item${filteredItems.length !== 1 ? 's' : ''}`;

    listElement.innerHTML = filteredItems.map((item, index) => `
        <div class="clipboard-item" data-index="${index}">
            <div class="clipboard-header">
                <div class="clipboard-meta">
                    <span class="device-tag">${escapeHtml(item.deviceId)}</span>
                    <span>${formatTimeAgo(item.timestamp)}</span>
                </div>
            </div>
            <div class="clipboard-content">${escapeHtml(item.content)}</div>
            <div class="clipboard-actions">
                <button class="btn-action btn-copy" onclick="copyToClipboard('${escapeHtml(item.content).replace(/'/g, "\\'")}')">
                    📋 Copy
                </button>
            </div>
        </div>
    `).join('');
}

function filterClipboardItems(searchTerm) {
    if (!searchTerm) {
        filteredItems = [...clipboardItems];
    } else {
        const term = searchTerm.toLowerCase();
        filteredItems = clipboardItems.filter(item =>
            item.content.toLowerCase().includes(term) ||
            item.deviceId.toLowerCase().includes(term)
        );
    }
    displayClipboardItems();
}

function copyToClipboard(text) {
    // Unescape HTML entities for copying
    const textarea = document.createElement('textarea');
    textarea.innerHTML = text;
    const cleanText = textarea.value;
    
    navigator.clipboard.writeText(cleanText).then(() => {
        showToast('Copied to clipboard!');
    }).catch(err => {
        console.error('Failed to copy:', err);
        showToast('Failed to copy to clipboard');
    });
}

// Utility Functions
function formatTimeAgo(timestamp) {
    const diff = Date.now() - timestamp;
    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days} day${days > 1 ? 's' : ''} ago`;
    if (hours > 0) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    if (minutes > 0) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
    return 'Just now';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    // Sign in button
    document.getElementById('signInButton').addEventListener('click', () => {
        if (!tokenClient) {
            showToast('Google Sign-In not loaded yet');
            return;
        }
        tokenClient.requestAccessToken({ prompt: 'consent' });
    });

    // Sign out button
    document.getElementById('signOutButton').addEventListener('click', () => {
        if (accessToken) {
            google.accounts.oauth2.revoke(accessToken);
        }
        showSignedOutState();
        showToast('Signed out successfully');
    });

    // Refresh button
    document.getElementById('refreshButton').addEventListener('click', () => {
        loadClipboardData();
        showToast('Refreshing...');
    });

    // Search input
    document.getElementById('searchInput').addEventListener('input', (e) => {
        filterClipboardItems(e.target.value);
    });

    // Initialize GAPI
    gapiLoaded();
    gisLoaded();
});

// Auto-refresh every 30 seconds
setInterval(() => {
    if (accessToken) {
        loadClipboardData();
    }
}, 30000);
