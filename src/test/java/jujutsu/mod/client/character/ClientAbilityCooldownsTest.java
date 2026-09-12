package jujutsu.mod.client.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;

final class ClientAbilityCooldownsTest {
	@Test
	void deadlineMathClampsAtZeroAndIntegerMax() {
		assertEquals(0, ClientAbilityCooldowns.remainingTicks(90L, 100L));
		assertEquals(240, ClientAbilityCooldowns.remainingTicks(340L, 100L));
		assertEquals(600, ClientAbilityCooldowns.remainingTicks(700L, 100L));
		assertEquals(Integer.MAX_VALUE, ClientAbilityCooldowns.remainingTicks(Long.MAX_VALUE, 0L));
	}
	/**
	 * The mirrored map is keyed by (vessel, slot) and the switch must drop the leaving vessel's
	 * entries, or a stale deadline would keep suppressing input for an ability the server already
	 * accepts (issue #84). Driven through the private map the same way the server-side test does —
	 * start() needs a live client level, which a unit test cannot build.
	 */
	@Test
	void clearForCharacterDropsOnlyTheLeavingVesselsMirroredDeadlines() throws Exception {
		Map<Object, Long> readyAt = mirroredReadyAt();
		Object megumiPrimary = mirroredKey(JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY);
		Object todoPrimary = mirroredKey(JujutsuCharacter.TODO, CharacterAbility.PRIMARY);
		assertTrue(megumiPrimary.equals(mirroredKey(JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY)),
				"key equality is what the map lookup relies on");
		assertFalse(megumiPrimary.equals(todoPrimary), "vessels must not collide");
		readyAt.clear();
		readyAt.put(megumiPrimary, 100L);
		readyAt.put(mirroredKey(JujutsuCharacter.MEGUMI, CharacterAbility.SECONDARY), 200L);
		readyAt.put(todoPrimary, 300L);

		ClientAbilityCooldowns.clearForCharacter(JujutsuCharacter.MEGUMI);

		assertEquals(1, readyAt.size(), "both MEGUMI slots must go, TODO stays");
		assertTrue(readyAt.containsKey(todoPrimary));
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
