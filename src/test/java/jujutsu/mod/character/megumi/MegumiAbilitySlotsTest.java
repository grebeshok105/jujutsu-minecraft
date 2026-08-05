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
	void routerOwnsExactlyTheSixApprovedInputPositions() throws Exception {
		String source = Files.readString(ROUTER);
		assertTrue(source.contains("case PRIMARY -> tryDivineDogs(player, notify);"),
				"PRIMARY must remain summon/recall");
		assertTrue(source.contains("case PRIMARY_SNEAK -> trySic(player, notify);"),
				"PRIMARY_SNEAK must remain Sic");
		assertTrue(source.contains("case SECONDARY -> MegumiShadowTrapRuntime.tryCast(player, notify);"),
				"SECONDARY must remain the shadow trap");
		assertTrue(source.contains("case SECONDARY_SNEAK -> MegumiShadowMoveRuntime.tryTap(player, notify);"),
				"SECONDARY_SNEAK must remain the tap shadow move");
		assertTrue(source.contains("case SECONDARY_SNEAK_HOLD -> MegumiShadowMoveRuntime.tryHoldStart(player, notify);"),
				"SECONDARY_SNEAK_HOLD must start the hold submerge");
		assertTrue(source.contains("case SECONDARY_SNEAK_RELEASE -> MegumiShadowMoveRuntime.tryRelease(player);"),
				"SECONDARY_SNEAK_RELEASE must end the hold submerge");
		assertTrue(source.contains("case ATTACK_CONTEXT, USE_CONTEXT -> false;"),
				"the two context slots must stay one explicit refusal arm");
		assertFalse(Pattern.compile("default\\s*->").matcher(source).find(),
				"a new input slot must fail compilation instead of inheriting a route");
	}
}
