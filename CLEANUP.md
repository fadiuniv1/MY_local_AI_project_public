# GitHub cleanup before making the repos public (detailed)

Both repositories are currently **private**. Current trees contain no credentials, but
**old commits still contain internal data**. Read this whole file before flipping
visibility. Nothing here has been executed automatically — no shared history rewritten.

## What is sensitive in history

| Repo | Old commits contain | Risk |
| :--- | :--- | :--- |
| `agent-bridge` | `sessions.db` (real chat data), `agent_bridge.log`, `agent_bridge.log.1`, `*.bak-*` | Internal paths/prompts, host details |
| `MY_local_AI_project` | Tailscale IP in `build.gradle.kts`/docs, `.idea/` metadata, Kotlin crash logs | Internal network endpoint, machine paths |

Current HEAD scans are clean (`git grep` for IPs/keys/passwords returns nothing). But
deleting a file in a new commit does **not** remove it from earlier commits.

---

## Option A — Fresh repository (safest, recommended)

Use this if you want zero chance of old data leaking through forks/caches.

### 1. Create a clean local copy
```bash
# Linux/macOS
git clone --depth 1 <repo-url> clean-repo
cd clean-repo
# or, from an existing checkout, export just the current tree:
# rsync -a --exclude .git ./ /tmp/clean-repo/
```

### 2. Verify the clean tree before publishing
```bash
gitleaks detect --source . --redact
git grep -n -I -E '100\.67\.146\.29|gsk_|password|api[_-]?key|BEGIN (RSA|OPENSSH|PRIVATE)' -- . ':!.env.example'
```
Both must return **no matches**. Also confirm `.env`, `sessions.db`, `*.log`,
`*.bak-*`, `__pycache__/` are not tracked:
```bash
git ls-files | grep -E '(\.env$|sessions\.db|\.log$|\.bak-|__pycache__)'
```

### 3. Publish
- Create a new empty GitHub repo (or delete/recreate the old one after archiving).
- `git remote add origin <new-url>` then `git push -u origin master` (app) / `main` (server).
- Archive the old private repo (do not delete until the new one is verified).

---

## Option B — Rewrite history with git-filter-repo (keeps history)

Use this if you want to preserve commit history. This rewrites every commit hash.

```bash
# Install git-filter-repo (GitHub's recommended tool)
pip install git-filter-repo

# Work in a FRESH clone, not your active checkout
git clone <repo-url> cleanup-review
cd cleanup-review

# Server: remove DB/logs/backups from every commit
git filter-repo --sensitive-data-removal --invert-paths \
  --path sessions.db --path agent_bridge.log --path agent_bridge.log.1 \
  --path-glob '*.bak-*'

# App: replace the hard-coded IP everywhere
echo 'YOUR_TAILSCALE_IP==<tailscale-ip>' > /tmp/replacements.txt
git filter-repo --sensitive-data-removal --replace-text /tmp/replacements.txt

# Force push all refs
git push --force --mirror origin
```

Then:
- Tell anyone who cloned to re-clone (or rebase, never merge, onto new history).
- Ask GitHub Support to remove cached views and old PR references if needed.
- Enable **secret scanning** and **push protection** in repo settings.

---

## Files you can delete (unnecessary for a public release)

**App repo** — already removed `r.md` (stale plain-text README copy). Keep:
`.env.example`, LICENSE, SECURITY.md, CONTRIBUTING.md, CHANGELOG.md, NEEDS.md,
PUBLISHING.md, CLEANUP.md, CI workflow, all source/tests/resources, CLI.

**Server repo** — safe to remove if you don't use them:
- `claude-local` — convenience wrapper that points a local Claude CLI at Ollama.
- `benchmark_routes.py` — optional performance script (keep if useful).
- `*.bak-*` — already untracked; delete from disk when no longer needed.
- `roadmap.md`, `AGENTS.md` — historical handoff docs; keep for context or archive.

Never remove: `server.py`, `watchdog.py`, `tests/`, `install.sh`, `requirements.txt`,
`.env.example`, `README.md`, `SECURITY.md`, `LICENSE`, `RUNBOOK.md`.

---

## Final checks before flipping public
```bash
git status --short                 # must be empty
git log --all --oneline -- sessions.db | wc -l   # must be 0 after cleanup
gitleaks detect --source . --redact
```
Then clone into a fresh directory and follow the README install steps verbatim.
