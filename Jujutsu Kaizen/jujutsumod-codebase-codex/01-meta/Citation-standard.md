# Citation Standard

Status: CURRENT

## Evidence labels

- VERIFIED — directly supported by current code, tests, or a reproduced command.
- INFERRED — reasonable interpretation that still needs runtime or upstream confirmation.
- UNKNOWN — not established; never implement it as fact.
- HISTORICAL — true only for the dated artifact/commit that records it.

## Preferred citation

Use repo-relative path plus a stable symbol, for example:

`src/main/java/jujutsu/mod/character/CharacterSelectionManager.java — select`

Add a commit SHA for point-in-time claims. Add line numbers only when useful in a review; line-only citations rot quickly.

## Conflict rule

Current code and passing tests beat prose. Root AGENTS.md owns durable decisions. SESSION.md owns the active handoff. Historical docs preserve context but never override current behavior.
