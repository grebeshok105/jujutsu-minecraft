package jujutsu.mod.client.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ClientAbilityCooldownsTest {
	@Test
	void deadlineMathClampsAtZeroAndIntegerMax() {
		assertEquals(0, ClientAbilityCooldowns.remainingTicks(90L, 100L));
		assertEquals(240, ClientAbilityCooldowns.remainingTicks(340L, 100L));
		assertEquals(600, ClientAbilityCooldowns.remainingTicks(700L, 100L));
		assertEquals(Integer.MAX_VALUE, ClientAbilityCooldowns.remainingTicks(Long.MAX_VALUE, 0L));
	}
}
