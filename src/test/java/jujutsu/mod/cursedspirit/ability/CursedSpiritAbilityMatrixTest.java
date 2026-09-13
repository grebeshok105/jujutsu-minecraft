package jujutsu.mod.cursedspirit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Classification matrix (C3 structural guard): every id is explicitly placed by all three
 * predicates, movement owners always occupy the clip, passives never do, and the acid glob
 * sits in the perception tag (R24 extension).
 *
 * <p>Red-proofs: drop an id from any expected set below → red; add a ninth id without a
 * switch arm → compile error in all three predicates at once (no {@code default}).
 */
final class CursedSpiritAbilityMatrixTest {
	private static final Set<CursedSpiritAbilityId> MOVERS = EnumSet.of(
			CursedSpiritAbilityId.DASH, CursedSpiritAbilityId.GROUND_SLAM,
			CursedSpiritAbilityId.GRAB_RUNNER);
	private static final Set<CursedSpiritAbilityId> CLIP = EnumSet.of(
			CursedSpiritAbilityId.DASH, CursedSpiritAbilityId.GROUND_SLAM,
			CursedSpiritAbilityId.ACID_SPIT, CursedSpiritAbilityId.GRAB_RUNNER,
			CursedSpiritAbilityId.FEAR);
	private static final Set<CursedSpiritAbilityId> PASSIVE = EnumSet.of(
			CursedSpiritAbilityId.ARMOR, CursedSpiritAbilityId.BERSERK);

	@Test
	void everyAbilityIsClassified() {
		assertTrue(CursedSpiritAbilityId.everyAbilityIsClassified());
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			assertEquals(MOVERS.contains(id), id.takesMovement(), id::id);
			assertEquals(CLIP.contains(id), id.occupiesAttackClip(), id::id);
			assertEquals(PASSIVE.contains(id), id.passive(), id::id);
		}
	}

	@Test
	void movementOwnersAlwaysOccupyTheClip() {
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			if (id.takesMovement()) {
				assertTrue(id.occupiesAttackClip(), id.id() + " moves but plays no clip");
			}
		}
	}

	@Test
	void passivesNeverOccupyTheClip() {
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			if (id.passive()) {
				assertFalse(id.occupiesAttackClip(), id.id() + " is a state, not a clip");
			}
		}
	}

	@Test
	void idsRoundTripThroughPersistenceNames() {
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			assertEquals(id, CursedSpiritAbilityId.byId(id.id()).orElseThrow());
		}
		assertTrue(CursedSpiritAbilityId.byId("teleport").isEmpty());
	}

	@Test
	void perceptionTagHoldsThreeSpiritsAndTheAcidGlob() throws Exception {
		Path tag = Path.of(
				"src/main/resources/data/jujutsumod/tags/entity_type/curse_perception_subject.json");
		Set<String> values = new HashSet<>();
		JsonParser.parseString(Files.readString(tag)).getAsJsonObject()
				.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
		assertTrue(values.contains("jujutsumod:lesser_cursed_spirit"), "lesser: " + values);
		assertTrue(values.contains("jujutsumod:cursed_spirit"), "common: " + values);
		assertTrue(values.contains("jujutsumod:greater_cursed_spirit"), "greater: " + values);
		assertTrue(values.contains("jujutsumod:cursed_acid_spit"), "acid glob: " + values);
	}
}
