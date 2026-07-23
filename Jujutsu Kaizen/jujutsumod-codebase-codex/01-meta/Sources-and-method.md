# Sources and Method

Status: CURRENT

Primary evidence is the current checkout: src/main, src/client, resources, tests, build.gradle, and fabric.mod.json. AGENTS.md and SESSION.md define policy and active context.

The repository intentionally retains only current operational documentation. ProjectJJK placeholder policy lives in docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md. External vaults and MCP services are optional enrichments, never silent sources of truth.

Validation commands:

- ./gradlew build --no-daemon --rerun-tasks
- python3 tools/audit_docs.py
- git diff --check
- runClient for rendering/gameplay claims
