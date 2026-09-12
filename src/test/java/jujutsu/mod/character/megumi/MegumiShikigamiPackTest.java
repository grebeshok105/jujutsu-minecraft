package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class MegumiShikigamiPackTest {
	private static final UUID ANCHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID FOREIGN = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
	private static final long TOKEN = 77L;
	private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
	private static final ResourceKey<Level> NETHER = Level.NETHER;

	private static MegumiShikigamiPack pack() {
		return new MegumiShikigamiPack(MegumiShikigami.NUE, OVERWORLD, ANCHOR,
				List.of(ANCHOR, SECOND), TOKEN, 1200L);
	}

	@Test
	void aBodyOfThePackWithTheSameTokenAndDimensionIsContained() {
		assertTrue(pack().contains(ANCHOR, TOKEN, OVERWORLD));
		assertTrue(pack().contains(SECOND, TOKEN, OVERWORLD));
	}

	@Test
	void aForeignBodyOrTokenOrDimensionIsNotContained() {
		assertFalse(pack().contains(FOREIGN, TOKEN, OVERWORLD));
		assertFalse(pack().contains(ANCHOR, TOKEN + 1, OVERWORLD), "a stale token must not match");
		assertFalse(pack().contains(ANCHOR, TOKEN, NETHER), "a body from another dimension must not match");
	}
}
