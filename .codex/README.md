# Project Codex Home

This directory is the project-local `CODEX_HOME` for Codex sessions that should
share state with this repository.

Start Codex from the repository root with:

```bash
CODEX_HOME="$PWD/.codex" codex
```

Resume sessions stored in this project-local home:

```bash
CODEX_HOME="$PWD/.codex" codex resume --all
```

Use `.codex/shared/` for handoff notes that are safe to commit or copy across
machines. Raw session transcripts, logs, auth, packages, and SQLite state are
ignored because they can contain sensitive data or machine-local paths.
