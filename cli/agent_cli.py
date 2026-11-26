#!/usr/bin/env python3
"""MY local AI terminal client — original code, MIT licensed.

A small Claude-Code-style terminal front end for the Agent Bridge HTTP API.
Uses only the Python standard library. Start it from the project root:

    python cli/agent_cli.py

Type a prompt to send it to the bridge, or use a slash command.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from uuid import uuid4

RESET = "\033[0m"
CYAN = "\033[36m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RED = "\033[31m"
DIM = "\033[2m"


def load_env() -> dict:
    values = dict(os.environ)
    env_file = Path(__file__).resolve().parent.parent / ".env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values.setdefault(key.strip(), value.strip())
    return values


ENV = load_env()
BASE = ENV.get("REMOTE_API_BASE_URL", "http://localhost:8081").rstrip("/")
SESSION = ENV.get("CLI_SESSION_ID", "cli-" + uuid4().hex[:8])
MODEL = ENV.get("CLI_MODEL", "qwen3.5:4b")


def cprint(text: str, color: str = "") -> None:
    print((color + text + RESET) if color else text, flush=True)


def request(method: str, path: str, body: dict | None = None, headers: dict | None = None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8", "replace")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:400]
        raise RuntimeError("HTTP %s: %s" % (exc.code, detail)) from exc


def ask(prompt: str) -> None:
    key = "cli-" + uuid4().hex
    ack = request("POST", "/api/chat/jobs",
                  {"session_id": SESSION, "prompt": prompt, "tool_mode": "direct", "model": MODEL},
                  {"Idempotency-Key": key})
    rid = ack["request_id"]
    cprint("job %s accepted" % rid[:8], DIM)
    cursor = 0
    terminal = {"completed", "delegated", "failed", "cancelled", "interrupted"}
    last_status = ""
    while True:
        status = request("GET", "/api/chat/jobs/" + rid)
        if status["status"] != last_status:
            last_status = status["status"]
        page = request("GET", "/api/chat/jobs/%s/events?after_id=%d&limit=200" % (rid, cursor))
        for event in page.get("events", []):
            cursor = max(cursor, int(event.get("event_id", 0)))
            kind = event.get("event_type")
            message = str(event.get("message") or "")
            if kind == "token":
                print(message, end="", flush=True)
            elif kind == "tool_started":
                cprint("\n  ⚙ " + message, YELLOW)
            elif kind == "tool_result":
                cprint("  ✓ " + message, GREEN)
            elif kind in {"failed", "cancelled", "interrupted"}:
                cprint("\n  ! " + message, RED)
        if last_status in terminal:
            print()
            break
        time.sleep(0.6)


def show_help() -> None:
    cprint("Commands:", CYAN)
    for line in (
        "/help, /quit",
        "/model <name>   set model (local name or cloud:fast / cloud:strong)",
        "/session <id>   switch chat tab",
        "/models         list models from the bridge",
        "/plan <text>    preview a plan (no PC changes)",
        "/review         review files touched in this session",
        "/files [path]   list a remote directory",
        "/read <path>    read a bounded text preview",
        "/run <command>  run a command on the PC",
        "anything else   send as a chat prompt",
    ):
        print("  " + line)


def main() -> None:
    global SESSION, MODEL
    cprint("MY local AI terminal · " + BASE, CYAN)
    cprint("session %s · model %s · /help for commands" % (SESSION, MODEL), DIM)
    while True:
        try:
            line = input("agent> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not line:
            continue
        if line in ("/quit", "/exit"):
            return
        if line == "/help":
            show_help()
            continue
        if line.startswith("/model "):
            MODEL = line.split(maxsplit=1)[1].strip()
            cprint("model = " + MODEL, CYAN)
            continue
        if line.startswith("/session "):
            SESSION = line.split(maxsplit=1)[1].strip()
            cprint("session = " + SESSION, CYAN)
            continue
        if line == "/models":
            catalog = request("GET", "/api/tags")
            for item in catalog.get("models", []):
                print("  " + str(item.get("name") or item.get("model")))
            continue
        if line.startswith("/plan "):
            plan = request("POST", "/api/plan",
                           {"prompt": line.split(maxsplit=1)[1], "session_id": SESSION})
            cprint("workspace: " + str(plan.get("workspace") or "—"), DIM)
            print(plan.get("plan", ""))
            continue
        if line == "/review":
            review = request("POST", "/api/review", {"session_id": SESSION})
            cprint("files: " + ", ".join(review.get("files_reviewed", [])), DIM)
            print(review.get("findings", ""))
            continue
        if line.startswith("/files"):
            path = line.split(maxsplit=1)
            path = path[1] if len(path) > 1 else "/"
            result = request("GET", "/api/files?path=" + urllib.parse.quote(path))
            for entry in result.get("entries", []):
                print("  %s  %s" % (entry.get("kind", "?"), entry.get("path", "")))
            continue
        if line.startswith("/read "):
            path = line.split(maxsplit=1)[1]
            result = request("GET", "/api/files/content?path=" + urllib.parse.quote(path))
            print(result.get("content", ""))
            continue
        if line.startswith("/run "):
            result = request("POST", "/api/command", {"command": line.split(maxsplit=1)[1]})
            print(result.get("stdout") or "")
            if result.get("stderr"):
                cprint(result["stderr"], RED)
            continue
        try:
            ask(line)
        except Exception as exc:
            cprint("error: " + str(exc), RED)


if __name__ == "__main__":
    main()
