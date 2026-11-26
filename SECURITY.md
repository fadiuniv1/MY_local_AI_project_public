# Security policy

## Reporting
Please open a private issue or contact the maintainer directly rather than posting
exploits publicly. Include reproduction steps and affected versions.

## What this app does
This Android client drives a self-hosted Ubuntu "Agent Bridge" over Tailscale. The
bridge can read/write files, run shell commands and control the desktop. Treat the
bridge URL as a credential-bearing endpoint: only expose it on a trusted tailnet.

## Secrets and configuration
- `.env` is gitignored and holds the bridge URL and optional cloud-model keys.
  Copy `.env.example` to `.env` locally. **Never commit `.env` or API keys.**
- The app defaults to `http://localhost:8081/` when no `.env` is present; configure
  your real Tailscale URL in Settings or via `.env`.
- No passwords, tokens or API keys are stored in the repository.

## Hardening checklist
- Bind the bridge to the Tailscale IP, never `0.0.0.0`.
- Keep `confirm=true` requirements for privileged writes/deletes/package removal.
- Use per-tool allow-list in Settings to disable capabilities you do not need.
- Keep the bridge behind Tailscale; never port-forward it to the public internet.
- Prefer zero-retention models for private code when using optional cloud models.

## License / third-party
MIT-licensed original code. OpenCode (MIT), Codex CLI (Apache-2.0) and Claude Code
(proprietary) are separate products; this project does not copy their source.
