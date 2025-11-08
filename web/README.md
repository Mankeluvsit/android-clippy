# Cloud Clipboard Web Viewer

## Prerequisites

- Google Cloud project with the **Drive API** enabled.
- OAuth 2.0 Web client (Authorized JavaScript origin must match where you serve this app).
- Google API key (required for gapi client initialization).

## Setup

```bash
cp .env.example .env   # add your client ID + API key
npm install
npm run dev            # opens http://localhost:5173
```

During development you must add `http://localhost:5173` as an authorized origin for the OAuth client.

## Deploying

```bash
npm run build
# deploy the contents of dist/ to your static hosting provider
```

Remember to update OAuth authorized origins to match your production hostname (must be HTTPS).
