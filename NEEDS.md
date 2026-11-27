# Inputs still needed from the user (2026-11-27)

Everything code-side is implemented, tested, published and verified. The items below
are the only ones that need you; all earlier decisions are recorded as done.

## 1. Screenshots (only real remaining input)
Chat images are not exposed to the agent as files. Save the screenshots into
`docs/screenshots/` with the names in `docs/screenshots/README.md`:

- `chat.png` — chat thread with tool cards and final answer
- `queue.png` — queue panel (⏳ N)
- `empty-state.png` — quick actions
- `settings.png` — connection & model settings
- `ubuntu-menu.png` — Ubuntu PC menu
- `feature-graphic.png` — optional 1280×640 banner

Then they can be embedded in the README. Tracked as app issue #1.

## 2. Optional: back up the signing keystore
The release APK was signed with `Temp\opencode\my-local-ai.jks` (gitignored config in
`keystore.properties`). If that temp directory is cleaned, the exact published
signature cannot be reproduced. Copy both files somewhere safe if you care about
future signed updates using the same identity.

## 3. Optional: rename the public repos
Both old repos are archived, so the original names are effectively free. Renaming
`MY_local_AI_project_public` → `MY_local_AI_project` and `agent-bridge-public` →
`agent-bridge` would require updating local remotes and docs. Not required.

## 4. Optional: 2FA on the publishing GitHub account
Account-level setting, not repo-level. Confirm in GitHub Settings → Security.

## Already decided and done (no action needed)

- **Fresh repos (Option A):** published with clean history; old repos private + archived.
- **Free cloud model:** Groq key configured in a root-only systemd drop-in on the
  Ubuntu host; `cloud:fast` / `cloud:strong` verified. Never committed or pasted in chat.
- **Signed APK:** built, signature verified with `apksigner`, attached to `v1.0.0`.
- **Releases / secret scanning / push protection / branch protection / private
  vulnerability reporting:** all enabled and verified via the GitHub API.
- **Repository hygiene:** issue templates, PR template and `SECURITY.md` reporting link.
- **Hebrew/Arabic UI:** complete; local model answers get an RTL marker, cloud models
  give fully fluent replies. Further local-model improvement tracked as app issue #4.

## Reference: optional cloud configuration (host only)

```bash
sudo systemctl edit agent-bridge
# [Service]
# Environment=CLOUD_BASE_URL=https://api.groq.com/openai/v1
# Environment=CLOUD_API_KEY=<key stored only here, mode 0600>
# Environment=CLOUD_FAST_MODEL=openai/gpt-oss-20b
# Environment=CLOUD_STRONG_MODEL=openai/gpt-oss-120b
sudo systemctl daemon-reload && sudo systemctl restart agent-bridge
curl -s http://<tailscale-ip>:8081/api/cloud/status
```

Without a key the bridge is fully local via Ollama. Free alternatives: Gemini AI Studio
(flash models, prompts may be used for training) and OpenRouter `:free` (50 req/day).
