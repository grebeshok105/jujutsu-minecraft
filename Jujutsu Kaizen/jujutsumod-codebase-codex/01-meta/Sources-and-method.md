# Sources and Method

Status: CURRENT

Primary evidence is the current checkout: src/main, src/client, resources, tests, build.gradle, and fabric.mod.json. AGENTS.md and SESSION.md define policy and active context.

Dated docs under docs/research, docs/reviews, docs/session-handoffs, and docs/superpowers are historical evidence. ProjectJJK research/provenance lives under docs/research/projectjjk. External vaults and MCP services are optional enrichments, never silent sources of truth.

Validation commands:

- ./gradlew build --no-daemon --rerun-tasks
- python3 tools/audit_docs.py
- git diff --check
- runClient for rendering/gameplay claims
