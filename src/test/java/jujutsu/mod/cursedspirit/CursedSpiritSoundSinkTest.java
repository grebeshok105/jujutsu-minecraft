package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Post-merge review F1: {@code makeSound} is not the only voice sink — footsteps, swim
 * splashes, muffled/amethyst steps and fall sounds all funnel into
 * {@code Entity.playSound(SoundEvent,float,float)} (javap-verified on 1.21.8), whose body
 * broadcasts to every player in range. The entity must override that funnel and route it
 * through the same per-perceiver packet loop as voices. Source-pinned: a headless JUnit
 * has no server level, so the pin guards the funnel's existence and its single seam.
 *
 * <p>Red-proof: delete the {@code playSound} override (steps broadcast to non-perceivers
 * again) or re-point either override at {@code level().playSound} and the pins go red.
 */
final class CursedSpiritSoundSinkTest {
	private static final Path ENTITY =
			Path.of("src/main/java/jujutsu/mod/cursedspirit/CursedSpiritEntity.java");

	@Test
	void playSoundFunnelIsOverridden() throws Exception {
		String src = Files.readString(ENTITY);
		assertTrue(src.contains("public void playSound(SoundEvent sound, float volume, float pitch)"),
				"the single Entity.playSound sink must be overridden");
	}

	@Test
	void serverPathRoutesThroughPerceiverLoop() throws Exception {
		String src = Files.readString(ENTITY);
		String playSound = src.substring(src.indexOf(
				"public void playSound(SoundEvent sound, float volume, float pitch)"));
		assertTrue(playSound.contains("sendSoundToPerceivers"),
				"the server path must address packets to perceivers only");
		assertFalse(playSound.contains("level().playSound"),
				"the broadcast sink must never be called on the spirit");
	}

	@Test
	void voicesShareTheSameSink() throws Exception {
		String src = Files.readString(ENTITY);
		String makeSound = src.substring(src.indexOf("public void makeSound(SoundEvent sound)"));
		assertTrue(makeSound.contains("playSound("),
				"makeSound must delegate to the shared playSound funnel, not duplicate the loop");
		// Exactly one packet-send loop may exist; a second copy is a drift hole.
		int first = src.indexOf("player.connection.send");
		assertTrue(first >= 0, "the perceiver send loop must exist");
		assertFalse(src.indexOf("player.connection.send", first + 1) >= 0,
				"only one send loop may exist — voices and mechanical sounds share it");
	}
}
