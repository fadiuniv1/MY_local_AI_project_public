# Publishing checklist (read before flipping the repo to public)

Both repositories are currently **private**. The current trees are clean, but Git
history is not automatically scrubbed by deleting files in a new commit.

## App repo (`MY_local_AI_project`)
- Current tree: no hard-coded Tailscale IP, no credentials, `.env` gitignored,
  MIT LICENSE, SECURITY.md, CONTRIBUTING.md, CI workflow, tests green.
- Old commits contain the Tailscale IP in `build.gradle.kts` and docs. That is an
  internal endpoint, not a credential, but if you want it gone from history:
  1. In a fresh clone, run `git filter-repo --sensitive-data-removal --replace-text`
     with a file containing `100.67.146.29==<tailscale-ip>`.
  2. Force-push all refs, then re-clone elsewhere.
- Enable GitHub secret scanning and push protection after publication.

## Server repo (`agent-bridge`)
- Current tree: `sessions.db`, logs, bytecode and `*.bak-*` are untracked and
  gitignored; hard-coded IPs replaced with `<tailscale-ip>`; LICENSE/SECURITY/
  CONTRIBUTING added.
- **Important:** previous commits still contain `sessions.db` (real chat data),
  `agent_bridge.log` and backup snapshots. Before public release, either:
  - create a fresh repository from the current clean tree and archive the old one
    (safest — see Trail of Bits open-sourcing guidance), or
  - in a fresh clone use `git filter-repo --sensitive-data-removal --invert-paths`
    for those paths, force-push, and re-clone.
- Cloud API keys were never stored in this repo; they are environment-only.

## Production hardening now in place
- Optional bridge auth: set `BRIDGE_API_TOKEN` in both the server environment and
  the app's `.env`; the app sends `X-Bridge-Token` on every API call.
- Session data: old turns are archived (`session_turns_archive`) instead of deleted;
  `GET /api/session/export` exports all stored records for a tab.
- Monitoring: `GET /api/db/integrity` reports SQLite integrity; the server has a
  GitHub Actions test workflow and a `RUNBOOK.md` for recovery/restore.

## Final checks
```bash
gitleaks detect --source . --redact
git log --all --format='%h' -- sessions.db agent_bridge.log
```
Then clone into a clean directory and follow the README verbatim.
