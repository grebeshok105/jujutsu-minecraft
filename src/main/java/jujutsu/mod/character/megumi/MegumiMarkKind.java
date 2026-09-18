package jujutsu.mod.character.megumi;

/**
 * Where a body's current mark came from (issue #107): the owner's own sic command, the pack's
 * retaliation pass, or the coordinator's autonomous assignment. Precedence is MANUAL &gt;
 * RETALIATION &gt; AUTONOMOUS — a stronger source may replace a weaker mark, never the reverse.
 */
enum MegumiMarkKind {
	MANUAL,
	RETALIATION,
	AUTONOMOUS
}
