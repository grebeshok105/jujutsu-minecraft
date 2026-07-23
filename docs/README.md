# Current Documentation

This directory intentionally contains only current operational documentation. Historical research, reviews, handoffs, plans, specs, mockups, and visual targets were removed to keep the repository focused on the live product.

## Source-of-truth order

1. Current code and passing tests define implemented behavior.
2. Root AGENTS.md defines durable product direction, architecture rules, and workflow.
3. Root SESSION.md defines the active branch and latest verified handoff.
4. Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md indexes current architecture.
5. KNOWN_ISSUES.md is the live risk and debt register.

If two documents disagree, use the higher source in this list and update the lower one.

## Files in this directory

- BUILDING_IN_SANDBOX.md — normal and restricted-container build guidance.
- KNOWN_ISSUES.md — live risk and debt register.
- PROVENANCE.md — ProjectJJK placeholder permission and replacement policy.
- THIRD_PARTY_NOTICES.md — retained third-party notices that apply to current runtime assets.

## Maintenance

Run the documentation validator after changing code-derived facts or docs:

```bash
python3 tools/audit_docs.py
```

The validator checks that only current docs remain, validates local links, rejects stale historical references, and compares current Codebase Codex metrics with the source tree. CI runs the same audit before the Java 21 build.
