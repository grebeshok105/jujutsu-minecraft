package jujutsu.mod.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * clear() drops an active stagger outright, unlike isStaggered(), which only lazily forgets an
 * expired one. The (apply -> clear -> isStaggered false) shape is the reset contract the fixture
 * tool needs, so it gets its own coverage next to the shared stagger state.
 */
class CombatStaggerClearTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void clearRemovesAnActiveStagger() {
		UUID entity = UUID.randomUUID();
		CombatStagger.GLOBAL.apply(entity, 1000L, 100);
		assertTrue(CombatStagger.GLOBAL.isStaggered(entity, 1000L));
		CombatStagger.GLOBAL.clear(entity);
		assertFalse(CombatStagger.GLOBAL.isStaggered(entity, 1000L), "cleared stagger must not remain active");
	}

	@Test
	void clearOnAnAbsentOrAlreadyClearedStaggerIsHarmless() {
		UUID neverApplied = UUID.randomUUID();
		CombatStagger.GLOBAL.clear(neverApplied);
		assertFalse(CombatStagger.GLOBAL.isStaggered(neverApplied, 1L));

		UUID expired = UUID.randomUUID();
		CombatStagger.GLOBAL.apply(expired, 1000L, 10);
		CombatStagger.GLOBAL.clear(expired);
		CombatStagger.GLOBAL.clear(expired);
		assertFalse(CombatStagger.GLOBAL.isStaggered(expired, 5000L));
	}
}
