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
	void routerOwnsExactlyTheSevenApprovedInputPositions() throws Exception {
		String source = Files.readString(ROUTER);
		// Each arm binds one slot to one runtime; the boolean runtime result is mapped to the
		// tri-state contract at the router (`true -> SUCCESS`, `false -> UNHANDLED_FAILURE`, the
		// refusal arm answering UNHANDLED_FAILURE directly).
		assertTrue(source.contains("case PRIMARY -> tryDivineDogs(player, notify)"),
				"PRIMARY must remain summon/recall");
		assertTrue(source.contains("case PRIMARY_SNEAK -> trySic(player, notify)"),
				"PRIMARY_SNEAK must remain Sic");
		assertTrue(source.contains("case SECONDARY -> MegumiShadowTrapRuntime.tryCast(player, notify)"),
				"SECONDARY must remain the shadow trap");
		assertTrue(source.contains("case SECONDARY_SNEAK -> MegumiShadowMoveRuntime.tryTap(player, notify)"),
				"SECONDARY_SNEAK must remain the tap shadow move");
		assertTrue(source.contains("case SECONDARY_SNEAK_HOLD -> MegumiShadowMoveRuntime.tryHoldStart(player, notify)"),
				"SECONDARY_SNEAK_HOLD must start the hold submerge");
		assertTrue(source.contains("case SECONDARY_SNEAK_RELEASE -> MegumiShadowMoveRuntime.tryRelease(player)"),
				"SECONDARY_SNEAK_RELEASE must end the hold submerge");
		assertTrue(source.contains("case ATTACK_CONTEXT, USE_CONTEXT, TERTIARY_SNEAK -> AbilityResult.UNHANDLED_FAILURE;"),
				"the context slots and the sneaking third key must stay one explicit refusal arm");
		assertTrue(source.contains("case TERTIARY -> MegumiShadowDropRuntime.tryCast(player, notify)"),
				"TERTIARY must route to the shadow drop runtime");
		// Every runtime arm must carry the boolean -> AbilityResult mapping, and the early-return
		// shadow-move gate must map too, or a silent false could escape as a bare UNHANDLED_FAILURE
		// without the mapping shape the contract pins.
		assertTrue(source.contains("? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE"),
				"every boolean runtime result must be mapped through the tri-state contract");
		assertTrue(source.contains("MegumiShadowMoveRuntime.handleWhileActive(player, ability, notify)\n\t\t\t\t\t? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE"),
				"the shadow-move gate must map its boolean result through the tri-state contract too");
		assertFalse(Pattern.compile("default\\s*->").matcher(source).find(),
				"a new input slot must fail compilation instead of inheriting a route");
	}
}
