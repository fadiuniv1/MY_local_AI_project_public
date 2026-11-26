# Contributing

## Setup
1. Copy `.env.example` to `.env` and set your bridge URLs.
2. Build: `.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1`.
3. Test: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1`.

Low-RAM hosts: stop stale `java` processes first; retry once if the JVM OOMs.

## Rules
- Never commit `.env`, API keys, keystores or real Tailscale addresses.
- Keep bridge changes backward compatible; older bridges must still work with new apps.
- Add a unit test for parser/state changes and keep the suite green.
- Do not copy code from OpenCode, Codex CLI or Claude Code. Interoperate via their
  documented public interfaces only, and keep their license notices where required.

## Roadmap
See `enhancement_roadmap.md` in the project notes and the server README/roadmap.
