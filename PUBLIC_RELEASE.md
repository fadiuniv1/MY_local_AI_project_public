# Releases — v1.0.0 (published)

Both clean public repos are live and have a `v1.0.0` release:

- Android client: `https://github.com/fadiuniv1/MY_local_AI_project_public/releases/tag/v1.0.0`
  - Asset: **`MY-local-AI-1.0.0-signed.apk`** (signed with a local self-signed key)
- Agent Bridge: `https://github.com/fadiuniv1/agent-bridge-public/releases/tag/v1.0.0`
  - Asset: **`agent-bridge-standalone.sh`** (single self-extracting installer)

`SHA256SUMS.txt` assets on each release let you verify downloads:
```bash
sha256sum -c SHA256SUMS.txt
```

## Install the Android app
Download the signed APK, allow "Install unknown apps" for your file manager/browser,
then open it. Set the bridge URL in Settings (default: `http://<tailscale-ip>:8081`).
No Google Play account is required.

## Install the bridge on a fresh Ubuntu host
```bash
curl -L -o agent-bridge-install.sh \
  https://github.com/fadiuniv1/agent-bridge-public/releases/download/v1.0.0/agent-bridge-standalone.sh
chmod +x agent-bridge-install.sh
./agent-bridge-install.sh
```
The script extracts embedded files, installs dependencies and Ollama (optional), sets
up the restricted sudoers rule, creates and enables the systemd service at boot, runs
all 90 tests, and prints health URLs.

Optional environment overrides: `BIND_IP`, `PORT`, `BRIDGE_API_TOKEN`, `CLOUD_BASE_URL`,
`CLOUD_API_KEY`, `CLOUD_FAST_MODEL`, `CLOUD_STRONG_MODEL`, `INSTALL_OLLAMA=0`,
`PULL_MODEL`, `SERVICE_NAME`.

## Repository security (already applied)
Both public repos have secret scanning + push protection enabled, branch protection on
`main`, green CI, and no credentials in tracked files. The two old private repos are
archived so their history is never published.

## Remaining optional work
- Save the shared phone screenshots into `docs/screenshots/` so they can be embedded
  in the README (chat images are not exposed to the agent as files).
- Rename the public repos to the original names if you want (old names are archived).
