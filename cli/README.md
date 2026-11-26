# MY local AI — terminal client

An original, MIT-licensed terminal front end for the Agent Bridge API. It is
inspired by Claude Code / OpenCode / Codex CLI workflows but contains **no code
from those projects**; it only uses their public interface ideas.

```bash
python cli/agent_cli.py
```

Configuration comes from the gitignored `.env`:
- `REMOTE_API_BASE_URL` — bridge base URL (default `http://localhost:8081`)
- `CLI_SESSION_ID` — optional fixed session id (default: new per run)
- `CLI_MODEL` — model name (default `qwen3.5:4b`; `cloud:fast` if configured on server)

Commands: `/help`, `/model <name>`, `/session <id>`, `/models`, `/plan <text>`,
`/review`, `/files [path]`, `/read <path>`, `/run <command>`, `/quit`.
Plain text is sent as a chat prompt and token events stream back live.
