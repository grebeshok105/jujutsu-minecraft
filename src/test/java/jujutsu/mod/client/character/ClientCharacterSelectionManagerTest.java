package jujutsu.mod.client.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.minecraft.client.resources.PlayerSkin;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager.Selection;

final class ClientCharacterSelectionManagerTest {
	private static final UUID LOCAL = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();

	/**
	 * Issue #95: a selection sync for ANOTHER player must not wipe this client's mirrored
	 * cooldowns — the client would think an ability is ready while the server still refuses it.
	 * Driven through the package-private seam; apply() itself needs a live Minecraft instance.
	 */
	@Test
	void vesselChangeForAnotherPlayerLeavesMirroredCooldownsAlone() throws Exception {
		Map<Object, Long> readyAt = seededReadyAt();

		ClientCharacterSelectionManager.forgetPreviousVessel(OTHER, LOCAL,
				new Selection(JujutsuCharacter.MEGUMI, PlayerSkin.Model.WIDE), JujutsuCharacter.TODO);

		assertEquals(2, readyAt.size(), "foreign selection sync must not touch READY_AT");
	}

	@Test
	void vesselChangeForLocalPlayerDropsLeavingVesselsDeadlines() throws Exception {
		Map<Object, Long> readyAt = seededReadyAt();

		ClientCharacterSelectionManager.forgetPreviousVessel(LOCAL, LOCAL,
				new Selection(JujutsuCharacter.MEGUMI, PlayerSkin.Model.WIDE), JujutsuCharacter.TODO);

		assertEquals(1, readyAt.size(), "leaving vessel's entries must go, other vessels stay");
		assertTrue(readyAt.containsKey(mirroredKey(JujutsuCharacter.TODO, CharacterAbility.PRIMARY)));
	}

	/** No local player (null id) must also clear nothing — same desync risk during login races. */
	@Test
	void vesselChangeWithNoLocalPlayerClearsNothing() throws Exception {
		Map<Object, Long> readyAt = seededReadyAt();

		ClientCharacterSelectionManager.forgetPreviousVessel(LOCAL, null,
				new Selection(JujutsuCharacter.MEGUMI, PlayerSkin.Model.WIDE), JujutsuCharacter.TODO);

		assertEquals(2, readyAt.size());
	}

	private static Map<Object, Long> seededReadyAt() throws Exception {
		Map<Object, Long> readyAt = mirroredReadyAt();
		readyAt.clear();
		readyAt.put(mirroredKey(JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY), 100L);
		readyAt.put(mirroredKey(JujutsuCharacter.TODO, CharacterAbility.PRIMARY), 300L);
		return readyAt;
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Long> mirroredReadyAt() throws Exception {
		Field field = ClientAbilityCooldowns.class.getDeclaredField("READY_AT");
		assertTrue(field.trySetAccessible());
		return (Map<Object, Long>) field.get(null);
	}

	private static Object mirroredKey(JujutsuCharacter character, CharacterAbility ability) throws Exception {
		java.lang.reflect.Constructor<?> constructor = Class
				.forName("jujutsu.mod.client.character.ClientAbilityCooldowns$Key")
				.getDeclaredConstructor(JujutsuCharacter.class, CharacterAbility.class);
		assertTrue(constructor.trySetAccessible());
		return constructor.newInstance(character, ability);
	}
}
