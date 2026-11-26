# Changelog

All notable changes to the Android client. Server changes are in the
`agent-bridge` repository.

## 1.0.0 (2026-11-26)
- Async chat jobs with idempotent sends, live token streaming, SSE reconnect and
  WorkManager completion.
- Session continuity: server-backed per-tab goal/workspace/files, compact turn history,
  referential follow-ups, queue persistence and auto-resume.
- Chat queue with reorder/cancel, plan preview, review sub-agent, undo, per-tool
  permissions and notification preferences.
- File browser, download notifications, inline diffs, file chips and browser action.
- Publish-ready: `.env` config, MIT LICENSE, SECURITY.md, CONTRIBUTING.md, CI workflow,
  optional bridge-token auth, `PUBLISHING.md` and `NEEDS.md`.

## Server compatibility
Requires a bridge exposing `/api/chat/jobs`, `/api/session-state`, `/api/plan`,
`/api/review`, `/api/stats`, `/api/db/integrity` and optionally `/v1/chat/completions`.
