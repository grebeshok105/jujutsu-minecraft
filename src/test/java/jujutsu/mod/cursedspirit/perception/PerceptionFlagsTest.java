package jujutsu.mod.cursedspirit.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Step 2 (contract C1): the two flag constants mean what every gate assumes — {@code NONE} denies
 * both fields, {@code PERCEIVER} grants both. A transposition here silently inverts issue #80.
 */
final class PerceptionFlagsTest {
	@Test
	void noneDeniesBothFields() {
		assertEquals(new PerceptionFlags(false, false), PerceptionFlags.NONE);
	}

	@Test
	void perceiverGrantsBothFields() {
		assertEquals(new PerceptionFlags(true, true), PerceptionFlags.PERCEIVER);
	}

	@Test
	void constantsAreDistinct() {
		assertNotEquals(PerceptionFlags.NONE, PerceptionFlags.PERCEIVER);
	}
}
