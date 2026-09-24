package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
		assertTrue(source.contains("case PRIMARY -> MegumiShikigamiRuntime.tryPrimary(player, notify)"),
				"PRIMARY must route to the shikigami selector (dogs included)");
		assertTrue(source.contains("case PRIMARY_SNEAK -> MegumiShikigamiRuntime.trySic(player, notify)"),
				"PRIMARY_SNEAK must route to the shikigami Sic (dogs included)");
		assertTrue(source.contains("case SECONDARY -> MegumiShadowTrapRuntime.tryCast(player, notify)"),
				"SECONDARY must remain the shadow trap");
		assertTrue(source.contains("case SECONDARY_SNEAK -> MegumiShadowMoveRuntime.tryTap(player, notify)"),
				"SECONDARY_SNEAK must remain the tap shadow move");
		assertTrue(source.contains("case SECONDARY_SNEAK_HOLD -> MegumiShadowMoveRuntime.tryHoldStart(player, notify)"),
				"SECONDARY_SNEAK_HOLD must start the hold submerge");
		assertTrue(source.contains("case SECONDARY_SNEAK_RELEASE -> MegumiShadowMoveRuntime.tryRelease(player)"),
				"SECONDARY_SNEAK_RELEASE must end the hold submerge");
		assertTrue(source.contains("case TERTIARY_SNEAK -> MegumiShikigamiRuntime.tryCycle(player, notify)"),
				"TERTIARY_SNEAK must cycle the shikigami selection");
		assertTrue(source.contains("case ATTACK_CONTEXT, USE_CONTEXT -> AbilityResult.UNHANDLED_FAILURE;"),
				"the two context slots must stay one explicit refusal arm");
		assertTrue(source.contains("case TERTIARY -> MegumiShadowDropRuntime.tryCast(player, notify)"),
				"TERTIARY must route to the shadow drop runtime");
		// Issue #108: one key, two edges. The press brings a partial out; the release is the only thing
		// that ends a tongue, so it must reach the runtime on its own arm rather than sharing the press's.
		assertTrue(source.contains("case PARTIAL -> MegumiPartialRuntime.tryPartial(player, notify)"),
				"PARTIAL must route to the partial runtime");
		assertTrue(source.contains("case PARTIAL_RELEASE -> MegumiPartialRuntime.tryPartialRelease(player)"),
				"PARTIAL_RELEASE must detach the tongue through the partial runtime");
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

	/**
	 * R21: the partial key answers to the selection, and only two selections bring anything out —
	 * Nue's wings and Toad's tongue. Everything else refuses, which is what makes the key's promise
	 * ("press it and something happens") true only where something exists.
	 */
	@Test
	void onlyNueAndToadManifestPartially() {
		assertEquals(MegumiPartialProfile.PartialKind.WINGS,
				MegumiPartialProfile.PartialKind.forSelection(MegumiShikigami.NUE),
				"NUE must bring out the wings");
		assertEquals(MegumiPartialProfile.PartialKind.TONGUE,
				MegumiPartialProfile.PartialKind.forSelection(MegumiShikigami.TOAD),
				"TOAD must bring out the tongue");
		assertNull(MegumiPartialProfile.PartialKind.forSelection(MegumiShikigami.DOGS),
				"the dogs answer the technique key; the partial key has nothing for them");
		// R37's tripwire is the table below, not this list: the loop asserts the projection over EVERY
		// constant, so a sixth shikigami fails here until its row is written.
		Map<MegumiShikigami, MegumiPartialProfile.PartialKind> expected = new HashMap<>();
		expected.put(MegumiShikigami.DOGS, null);
		expected.put(MegumiShikigami.NUE, MegumiPartialProfile.PartialKind.WINGS);
		expected.put(MegumiShikigami.TOAD, MegumiPartialProfile.PartialKind.TONGUE);
		expected.put(MegumiShikigami.RABBITS, null);
		expected.put(MegumiShikigami.ELEPHANT, null);
		expected.put(MegumiShikigami.SERPENT, null);
		expected.put(MegumiShikigami.DEER, null);
		expected.put(MegumiShikigami.OX, null);
		expected.put(MegumiShikigami.TIGER, null);
		assertEquals(MegumiShikigami.values().length, expected.size(),
				"a shikigami was added without deciding whether it manifests partially (R37)");
		for (Map.Entry<MegumiShikigami, MegumiPartialProfile.PartialKind> row : expected.entrySet()) {
			assertEquals(row.getValue(), MegumiPartialProfile.PartialKind.forSelection(row.getKey()),
					"partial table row: " + row.getKey());
		}
	}

	/**
	 * R37's other half: the table is a projection of the enum, not a chain of comparisons, so a
	 * shikigami that gained a partial by accident (a constant added to the enum and a type that happens
	 * to match) cannot slip through — {@code type()} and the selection are the same value by
	 * construction.
	 */
	@Test
	void everyPartialKindOwnsExactlyOneShikigami() {
		assertEquals(2, MegumiPartialProfile.PartialKind.values().length,
				"a third partial manifestation needs a key story before it needs a constant");
		Set<MegumiShikigami> owned = new HashSet<>();
		for (MegumiPartialProfile.PartialKind kind : MegumiPartialProfile.PartialKind.values()) {
			assertTrue(owned.add(kind.type()), "two partial kinds claim the same shikigami: " + kind.type());
			assertEquals(kind, MegumiPartialProfile.PartialKind.forSelection(kind.type()),
					"forSelection must be the inverse of type() for " + kind);
		}
	}
}
