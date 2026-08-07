package jujutsu.mod.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * clearAllForPlayer is a plain sweep over the private READY_AT map. start() cannot populate it here:
 * it needs a live ServerPlayer, which unit tests cannot construct. Driving the map directly through
 * the same Key shape start() writes keeps the observable contract — only the target player's
 * (player, vessel, slot) keys disappear, across every vessel — under test.
 */
class CharacterAbilityCooldownsClearAllTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void clearAllForPlayerDropsExactlyTheTargetPlayersKeysAcrossVessels() throws Exception {
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		Map<Object, Long> readyAt = readyAt();
		readyAt.put(key(alice, JujutsuCharacter.TODO, CharacterAbility.PRIMARY), 100L);
		readyAt.put(key(alice, JujutsuCharacter.TODO, CharacterAbility.SECONDARY), 200L);
		readyAt.put(key(alice, JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY), 300L);
		readyAt.put(key(alice, JujutsuCharacter.NOBARA, CharacterAbility.PRIMARY), 400L);
		readyAt.put(key(bob, JujutsuCharacter.TODO, CharacterAbility.PRIMARY), 500L);
		readyAt.put(key(bob, JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY), 600L);

		CharacterAbilityCooldowns.clearAllForPlayer(alice);

		assertEquals(2, readyAt.size(), "only the other player's keys may survive");
		assertTrue(readyAt.containsKey(key(bob, JujutsuCharacter.TODO, CharacterAbility.PRIMARY)));
		assertTrue(readyAt.containsKey(key(bob, JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY)));
	}

	@Test
	void clearAllForPlayerWithNoKeysIsANoOp() throws Exception {
		Map<Object, Long> readyAt = readyAt();
		readyAt.clear();
		CharacterAbilityCooldowns.clearAllForPlayer(UUID.randomUUID());
		assertTrue(readyAt.isEmpty());
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Long> readyAt() throws Exception {
		Field field = CharacterAbilityCooldowns.class.getDeclaredField("READY_AT");
		assertTrue(field.trySetAccessible());
		return (Map<Object, Long>) field.get(null);
	}

	private static Object key(UUID playerId, JujutsuCharacter character, CharacterAbility ability) throws Exception {
		Constructor<?> constructor = Class
				.forName("jujutsu.mod.character.CharacterAbilityCooldowns$Key")
				.getDeclaredConstructor(UUID.class, JujutsuCharacter.class, CharacterAbility.class);
		assertTrue(constructor.trySetAccessible());
		return constructor.newInstance(playerId, character, ability);
	}
}
