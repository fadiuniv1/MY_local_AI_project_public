# What I need from you to go further (2026-11-26)

Everything code-side is implemented, tested and pushed. These are the only items
that require your decision or input.

## 1. Make the GitHub repos public? (IMPORTANT — needs your decision)
Both repos are currently **private**. Their current trees are clean, but **old commits
still contain internal data**:
- `agent-bridge`: 3 commits touched `sessions.db` (real chat data) and logs.
- `MY_local_AI_project`: old commits contain the Tailscale IP and IDE files.

Deleting from HEAD does not remove history. Choose one:
- **(a) Fresh repository** from the current clean tree (safest — archive old repos).
- **(b)** I run `git filter-repo` in a fresh clone and force-push (rewrites hashes,
  coordinate any collaborators).
- **(c)** Keep them private — nothing else is required.

Tell me a/b/c. I will not rewrite shared history without your go-ahead.

## 2. Fast free cloud model — DONE (Groq key supplied and configured)
`cloud:fast` and `cloud:strong` are active on the bridge and verified live. No action needed.
Original notes below kept for reference:
All free tiers require a **free API key** — no reliable anonymous endpoint exists.
Recommended: Groq (no card, 30 req/min, 1k req/day, 8k tokens/min):
1. Get a key at https://console.groq.com/keys
2. On the Ubuntu host:
   ```bash
   sudo systemctl edit agent-bridge
   # [Service]
   # Environment=CLOUD_BASE_URL=https://api.groq.com/openai/v1
   # Environment=CLOUD_API_KEY=YOUR_KEY
   # Environment=CLOUD_FAST_MODEL=openai/gpt-oss-20b
   # Environment=CLOUD_STRONG_MODEL=openai/gpt-oss-120b
   sudo systemctl daemon-reload && sudo systemctl restart agent-bridge
   curl -s http://<tailscale-ip>:8081/api/cloud/status
   ```
Alternatives: Gemini AI Studio (free Flash models; prompts may be used for training),
OpenRouter `:free` models (50 req/day). Without this, local Ollama remains fully functional.

## 3. Screenshots — you shared them in chat, but I need the files
I can see the screenshots but chat images are not exposed to me as files. Please save
them into `docs/screenshots/` with the names listed in `docs/screenshots/README.md`
(`chat.png`, `queue.png`, `empty-state.png`, `settings.png`, `ubuntu-menu.png`). Then I
can embed them in the README. No code images are needed — the launcher icon exists.
Original note below:
The app already has a custom launcher icon — no code images needed. For a nicer GitHub
page, send me:
- 1–2 phone screenshots of the chat (tool cards + final answer),
- 1 screenshot of the queue panel (⏳ N → tap),
- optional 1280×640 feature graphic.
I cannot capture the phone screen myself; I'll embed what you send.

## 4. Release signing (optional, for distribution outside Android Studio)
Debug APK works today. For a signed release I need a keystore path + alias/password,
or permission to generate one locally (it stays gitignored).

## 5. Hebrew/Arabic quality
The app UI is fully translated with RTL layout. The local 1.5B model often answers in
English; the bridge adds a localized marker (`תשובה:` / `الإجابة:`) as a fallback.
Fully fluent RTL answers need the free cloud key from item 2.

## Verification status
- App: 34/34 tests (3× runs), APK builds, git clean after this cleanup commit.
- Server: 79/79 tests (3× runs), smoke 11/11 (2× runs), service enabled at boot.
- Both repos pushed; no credentials in current trees.
