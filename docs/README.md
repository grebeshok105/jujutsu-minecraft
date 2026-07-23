# Documentation Map

This directory contains current operational documentation and a dated engineering archive. A document can be detailed and useful without describing the current main branch.

## Source-of-truth order

1. Current code and passing tests define implemented behavior.
2. Root AGENTS.md defines durable product direction, architecture rules, and workflow.
3. Root SESSION.md defines the active branch and latest verified handoff.
4. Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md indexes current architecture.
5. docs/specs/2026-07-19-known-issues-and-tech-debt.md is the live risk register despite its dated filename.

If two documents disagree, use the higher source in this list and update the lower one.

## Current operational documents

- BUILDING_IN_SANDBOX.md — restricted-container JDK and Gradle notes, plus the normal build command.
- specs/2026-07-19-known-issues-and-tech-debt.md — current risk and debt register.
- ../README.md — product overview and contributor entry point.
- ../AGENTS.md — non-negotiable engineering rules.
- ../SESSION.md — latest implementation handoff.
- ../Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md — current codebase map.

## Historical records

The following directories preserve dated context and are deliberately not rewritten into current behavior claims:

- research/ and research/sources/ — investigations, external references, and implementation studies.
- reviews/ — point-in-time audits; findings may already be fixed or reclassified.
- session-handoffs/ — closed-session snapshots.
- superpowers/plans/ and superpowers/specs/ — accepted or superseded design/implementation records.
- gui/ and visual-targets/ — visual targets and retired mockups.
- vfx/ — dated technique notes.

Every historical Markdown file carries a HISTORICAL REFERENCE banner. Historical HTML files carry data-document-status="historical" on their html element.

## Maintenance

Run the documentation validator after changing code-derived facts or docs:

```bash
python3 tools/audit_docs.py
```

The validator checks the hierarchy, historical markers, selected stale terms, local links, and current code-derived counts in the Codex MOC. Run it locally before every documentation PR; adding it to GitHub Actions requires workflow-write permission for the connected GitHub integration.
