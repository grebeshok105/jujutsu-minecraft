package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Issue #80 voice gate: the pure audience rule behind {@code makeSound}. Loading the
 * entity class needs the vanilla bootstrap (data accessors), hence the setup below.
 */
final class CursedSpiritVoiceAudienceTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void perceiverInRangeHears() {
		assertTrue(CursedSpiritEntity.voiceReaches(true, 25.0, 100.0));
	}

	@Test
	void nonPerceiverInRangeHearsNothing() {
		assertFalse(CursedSpiritEntity.voiceReaches(false, 25.0, 100.0));
	}

	@Test
	void perceiverBeyondRangeHearsNothing() {
		assertFalse(CursedSpiritEntity.voiceReaches(true, 121.0, 100.0));
	}

	@Test
	void rangeEdgeStillHears() {
		assertTrue(CursedSpiritEntity.voiceReaches(true, 100.0, 100.0));
	}
}
