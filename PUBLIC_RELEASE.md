# Making the fresh repos public (Option A — final steps)

Two brand-new repositories were created from the **current clean trees only** (no old
history), pushed with a single initial commit and tagged `v1.0.0`:

- `https://github.com/fadiuniv1/MY_local_AI_project_public` (Android client)
- `https://github.com/fadiuniv1/agent-bridge-public` (server + standalone installer)

Both are still **private**. Do not flip visibility until the old repos are archived.

## 1. Archive the old repos (GitHub web UI)
- `MY_local_AI_project` → Settings → Archive this repository (rename to
  `MY_local_AI_project_archive` first if you want clarity).
- `agent-bridge` → Settings → Archive this repository (rename to
  `agent-bridge-archive` first).
Archived repos stay private and read-only; the sensitive history is not published.

## 2. Rename the fresh repos to the original names (optional)
Once the old names are free:
- `MY_local_AI_project_public` → rename to `MY_local_AI_project`
- `agent-bridge-public` → rename to `agent-bridge`
Local remotes then need `git remote set-url origin <new-url>`.

If you prefer, just keep the `-public` names and flip those to public.

## 3. Flip to public
Settings → Danger Zone → Change repository visibility → Public. Then:
- Enable **secret scanning** and **push protection** (Settings → Code security).
- Add branch protection on `main` (require checks — CI already runs unit tests).

## 4. Publish the server release asset
From a machine with `gh` installed:
```bash
git clone https://github.com/fadiuniv1/agent-bridge-public.git
cd agent-bridge-public
gh release create v1.0.0 agent-bridge-standalone.sh \
  --title "Agent Bridge 1.0.0 — standalone installer" \
  --notes "Single-file installer: extracts and installs the bridge, enables it at boot."
```
Or GitHub web UI → Releases → Draft new release → tag `v1.0.0` → attach
`agent-bridge-standalone.sh`.

## 5. Screenshots (still needed from you)
Save the shared phone screenshots into `docs/screenshots/` with the names listed in
`docs/screenshots/README.md`. I cannot access chat images as files; once they are in the
repo I can embed them in the README.

## 6. Play Store / signing
Not needed now — debug APK from Android Studio is sufficient. No signing keystore required.

## Verification after step 3
```bash
git clone <public-url> /tmp/verify
cd /tmp/verify
git log --oneline            # one commit only
git grep -n -I -E '100\.67\.146\.29|gsk_|password|BEGIN PRIVATE' -- .
```
Second command must return nothing. Then follow README install/build steps verbatim.
