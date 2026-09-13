package jujutsu.mod.cursedspirit.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.JujutsuCharacters;

/**
 * Step 2: the vessel→flags translator. The three sorcerer vessels perceive; {@code NONE} does
 * not — and any future vessel that forgets the override inherits {@code NONE} (fail-closed).
 *
 * <p>Red-proof: change any arm to the other constant (or delete an override) and the matching
 * test goes red. In particular, {@code None → PERCEIVER} would silently kill issue #80.
 */
final class CursedSpiritVesselPerceptionTest {
	@Test
	void nobaraPerceives() {
		assertEquals(PerceptionFlags.PERCEIVER,
				JujutsuCharacters.definition(JujutsuCharacter.NOBARA).cursePerception());
	}

	@Test
	void todoPerceives() {
		assertEquals(PerceptionFlags.PERCEIVER,
				JujutsuCharacters.definition(JujutsuCharacter.TODO).cursePerception());
	}

	@Test
	void megumiPerceives() {
		assertEquals(PerceptionFlags.PERCEIVER,
				JujutsuCharacters.definition(JujutsuCharacter.MEGUMI).cursePerception());
	}

	@Test
	void noneDoesNotPerceive() {
		assertEquals(PerceptionFlags.NONE,
				JujutsuCharacters.definition(JujutsuCharacter.NONE).cursePerception());
	}

	@Test
	void futureVesselDefaultsToNone() {
		CharacterDefinition bare = new CharacterDefinition() {
			@Override
			public JujutsuCharacter id() {
				return JujutsuCharacter.NONE;
			}

			@Override
			public AbilityResult tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
				return AbilityResult.UNHANDLED_FAILURE;
			}
		};
		assertEquals(PerceptionFlags.NONE, bare.cursePerception());
	}
}
