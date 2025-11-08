interface ClipboardItem {
  id: string;
  text: string;
  mimeType: string;
  createdAt: string;
  deviceName: string;
  encrypted: boolean;
}

interface ClipboardPayload {
  version: number;
  items: ClipboardItem[];
}

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string;
const API_KEY = import.meta.env.VITE_GOOGLE_API_KEY as string;

const FOLDER_NAME = "Cloud Clipboard";
const FILE_NAME = "clipboard.json";

if (!CLIENT_ID || !API_KEY) {
  throw new Error("Missing VITE_GOOGLE_CLIENT_ID or VITE_GOOGLE_API_KEY environment variables.");
}

let tokenClient: google.accounts.oauth2.TokenClient | null = null;
let accessToken: string | null = null;
let gapiInitialized = false;

const app = document.getElementById("app")!;
app.innerHTML = `
  <main class="container">
    <header>
      <h1>Cloud Clipboard Viewer</h1>
      <p class="subtitle">
        Sign in with the same Google account you use on Android to read your synced clipboard history.
      </p>
    </header>
    <section class="controls">
      <button id="authorize">Sign in with Google</button>
      <button id="signout" disabled>Sign out</button>
      <button id="refresh" disabled>Refresh</button>
    </section>
    <section id="status" class="status">Not signed in.</section>
    <section>
      <table id="clipboard-table" class="clipboard-table">
        <thead>
          <tr>
            <th>Snippet</th>
            <th>Device</th>
            <th>Timestamp</th>
            <th></th>
          </tr>
        </thead>
        <tbody id="clipboard-body">
          <tr><td colspan="4" class="placeholder">No data yet.</td></tr>
        </tbody>
      </table>
    </section>
  </main>
`;

const authorizeButton = document.getElementById("authorize") as HTMLButtonElement;
const signOutButton = document.getElementById("signout") as HTMLButtonElement;
const refreshButton = document.getElementById("refresh") as HTMLButtonElement;
const statusEl = document.getElementById("status") as HTMLElement;
const tableBody = document.getElementById("clipboard-body") as HTMLTableSectionElement;

function setStatus(message: string, isError = false) {
  statusEl.textContent = message;
  statusEl.classList.toggle("error", isError);
}

async function ensureGapi() {
  if (gapiInitialized) return;

  await new Promise<void>((resolve, reject) => {
    gapi.load("client", {
      callback: async () => {
        try {
          await gapi.client.init({
            apiKey: API_KEY,
            discoveryDocs: ["https://www.googleapis.com/discovery/v1/apis/drive/v3/rest"]
          });
          gapiInitialized = true;
          resolve();
        } catch (error) {
          reject(error);
        }
      },
      onerror: () => reject(new Error("Failed to load Google API client"))
    });
  });
}

function initTokenClient() {
  if (tokenClient) return;
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: "https://www.googleapis.com/auth/drive.file",
    callback: async (response) => {
      if (response.error) {
        setStatus(`Authorization error: ${response.error}`, true);
        return;
      }
      accessToken = response.access_token;
      gapi.client.setToken({ access_token: accessToken });
      authorizeButton.disabled = true;
      signOutButton.disabled = false;
      refreshButton.disabled = false;
      setStatus("Authorized. Loading clipboard...");
      await loadClipboard();
    }
  });
}

authorizeButton.addEventListener("click", async () => {
  try {
    await ensureGapi();
    initTokenClient();
    tokenClient!.requestAccessToken({ prompt: accessToken ? "" : "consent" });
  } catch (error) {
    console.error(error);
    setStatus(`Sign-in failed: ${(error as Error).message}`, true);
  }
});

signOutButton.addEventListener("click", () => {
  if (!accessToken) return;
  google.accounts.oauth2.revoke(accessToken, () => {
    accessToken = null;
    gapi.client.setToken(null);
    authorizeButton.disabled = false;
    signOutButton.disabled = true;
    refreshButton.disabled = true;
    tableBody.innerHTML = `<tr><td colspan="4" class="placeholder">Signed out.</td></tr>`;
    setStatus("Signed out.");
  });
});

refreshButton.addEventListener("click", async () => {
  if (!accessToken) return;
  await loadClipboard();
});

async function loadClipboard() {
  try {
    setStatus("Fetching clipboard...");
    const fileId = await findClipboardFile();
    if (!fileId) {
      tableBody.innerHTML = `<tr><td colspan="4" class="placeholder">
        Could not locate ${FILE_NAME} in your Drive. Run the Android app first.
      </td></tr>`;
      setStatus("Clipboard file not found.", true);
      return;
    }

    const response = await gapi.client.drive.files.get({
      fileId,
      alt: "media"
    });

    const payload = JSON.parse(response.body) as ClipboardPayload;
    renderClipboard(payload.items);
    setStatus(`Loaded ${payload.items.length} clip${payload.items.length === 1 ? "" : "s"}.`);
  } catch (error) {
    console.error(error);
    setStatus(`Failed to load clipboard: ${(error as Error).message}`, true);
  }
}

async function findClipboardFile(): Promise<string | null> {
  const folderResponse = await gapi.client.drive.files.list({
    q: `name = '${FOLDER_NAME}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`,
    spaces: "drive",
    fields: "files(id, name)",
    pageSize: 1
  });
  const folder = folderResponse.result.files?.[0];
  if (!folder) {
    return null;
  }

  const fileResponse = await gapi.client.drive.files.list({
    q: `name = '${FILE_NAME}' and '${folder.id}' in parents and trashed = false`,
    spaces: "drive",
    fields: "files(id, name)",
    pageSize: 1
  });

  const file = fileResponse.result.files?.[0];
  return file?.id ?? null;
}

function renderClipboard(items: ClipboardItem[]) {
  if (!items.length) {
    tableBody.innerHTML = `<tr><td colspan="4" class="placeholder">Clipboard is empty.</td></tr>`;
    return;
  }

  tableBody.innerHTML = "";
  const formatter = new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });

  for (const item of items) {
    const row = document.createElement("tr");

    const snippetCell = document.createElement("td");
    snippetCell.textContent = truncate(item.text, 120);
    row.appendChild(snippetCell);

    const deviceCell = document.createElement("td");
    deviceCell.textContent = item.deviceName ?? "Unknown device";
    row.appendChild(deviceCell);

    const timeCell = document.createElement("td");
    const date = new Date(item.createdAt);
    timeCell.textContent = Number.isNaN(date.getTime()) ? item.createdAt : formatter.format(date);
    row.appendChild(timeCell);

    const actionsCell = document.createElement("td");
    const copyButton = document.createElement("button");
    copyButton.textContent = "Copy";
    copyButton.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(item.text);
        setStatus("Copied to clipboard.");
      } catch (error) {
        console.error(error);
        setStatus("Browser blocked clipboard copy.", true);
      }
    });
    actionsCell.appendChild(copyButton);
    row.appendChild(actionsCell);

    tableBody.appendChild(row);
  }
}

function truncate(value: string, limit: number): string {
  return value.length > limit ? `${value.slice(0, limit - 1)}…` : value;
}

// Basic styling
const style = document.createElement("style");
style.textContent = `
  :root { font-family: Inter, system-ui, sans-serif; color: #1f2933; background: #f5f7fa; }
  body { margin: 0; padding: 0; }
  .container { max-width: 960px; margin: 0 auto; padding: 24px; }
  header { margin-bottom: 16px; }
  .subtitle { color: #52606d; }
  .controls { display: flex; gap: 12px; margin-bottom: 12px; }
  button { padding: 8px 16px; border-radius: 6px; border: none; background: #2563eb; color: white; cursor: pointer; }
  button[disabled] { background: #cbd2d9; color: #52606d; cursor: not-allowed; }
  .status { margin-bottom: 12px; padding: 8px 12px; border-radius: 6px; background: #e3f2fd; color: #0b5394; }
  .status.error { background: #fdecec; color: #a61b1b; }
  .clipboard-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: rgba(15, 23, 42, 0.08) 0px 10px 30px -10px; }
  .clipboard-table th, .clipboard-table td { padding: 12px 16px; border-bottom: 1px solid #e4e7eb; text-align: left; }
  .clipboard-table th { background: #f8fafc; font-weight: 600; color: #243b53; }
  .clipboard-table tr:last-child td { border-bottom: none; }
  .clipboard-table button { background: #0ea5e9; }
  .placeholder { text-align: center; color: #9aa5b1; padding: 32px 0; }
`;
document.head.appendChild(style);
