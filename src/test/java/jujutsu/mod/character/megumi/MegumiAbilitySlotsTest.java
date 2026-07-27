package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MegumiAbilitySlotsTest {
	private static final Path ROUTER = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiAbilityRouter.java");

	@Test
	void routerOwnsExactlyTheTwoApprovedInputPositions() throws Exception {
		String source = Files.readString(ROUTER);
		assertTrue(source.contains("case PRIMARY -> tryDivineDogs(player, notify);"),
				"PRIMARY must remain summon/recall");
		assertTrue(source.contains("case PRIMARY_SNEAK -> trySic(player, notify);"),
				"PRIMARY_SNEAK must remain Sic");
		assertTrue(source.contains("case SECONDARY, SECONDARY_SNEAK, ATTACK_CONTEXT, USE_CONTEXT -> false;"),
				"the other four slots must stay one explicit refusal arm");
		assertFalse(Pattern.compile("default\\s*->").matcher(source).find(),
				"a new input slot must fail compilation instead of inheriting a route");
	}
}
