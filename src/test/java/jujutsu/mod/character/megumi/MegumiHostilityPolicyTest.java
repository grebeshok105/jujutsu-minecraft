package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * Who Max Elephant's presence may hurt (issue #79). The policy exists to keep two questions apart
 * that look like one: {@link MegumiShikigamiFriendlyFire#isProtected} answers "may this take
 * damage at all" and protects allies; the presence must shove allies while sparing them the damage.
 * The three hostility signals are the damage half, and the window that keeps a fresh aggressor
 * worth answering for is the only stateful part — both cheap to pin here.
 */
class MegumiHostilityPolicyTest {

	@Test
	void anySingleSignalIsEnough() {
		assertFalse(MegumiHostilityPolicy.isHostile(false, false, false),
				"a neutral, unaimed, non-aggressor body is not hostile");
		assertTrue(MegumiHostilityPolicy.isHostile(true, false, false),
				"a hostile archetype is enough on its own");
		assertTrue(MegumiHostilityPolicy.isHostile(false, true, false),
				"a body aiming at the owner is enough on its own");
		assertTrue(MegumiHostilityPolicy.isHostile(false, false, true),
				"a fresh aggressor is enough on its own, even without the archetype");
	}

	@Test
	void theAggressorWindowCloses() {
		int window = MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS;
		long hit = 1_000L;
		assertTrue(MegumiHostilityPolicy.aggressorFresh(hit, hit), "the hit tick itself is fresh");
		assertTrue(MegumiHostilityPolicy.aggressorFresh(hit + window, hit),
				"the last tick of the window is still fresh");
		assertFalse(MegumiHostilityPolicy.aggressorFresh(hit + window + 1, hit),
				"one tick past the window the aggressor is forgotten");
	}

	/**
	 * Issue #91 regression: vanilla stamps {@code lastHurtByMobTimestamp} with the victim's own
	 * {@code tickCount}. A player who (re)joins a long-running world starts a fresh tickCount, so
	 * the hit stamp and the world clock live on different timelines — comparing them expires every
	 * hit instantly and the presence never answers an aggressor.
	 */
	@Test
	void aFreshHitAfterRejoinReadsFreshOnTheOwnersClock() {
		long hitStamp = 5L;                 // owner.tickCount when the hit landed, soon after rejoin
		long ownerNow = hitStamp + 10;      // a few ticks later, inside the window
		long worldAge = 1_000_000L;         // the world has ticked far longer than the player exists
		assertTrue(MegumiHostilityPolicy.aggressorFresh(ownerNow, hitStamp),
				"measured on the owner's own clock the aggressor is still fresh");
		assertFalse(MegumiHostilityPolicy.aggressorFresh(worldAge, hitStamp),
				"the bug exactly: read against the world clock, the same hit is silently stale");
	}

	/**
	 * Issue #91 wiring pin: the live-entity overload must own the clock itself (the owner's
	 * {@code tickCount}), so no caller can pass a level clock back in. A clock parameter on this
	 * overload is what let the bug in.
	 */
	@Test
	void theLiveOverloadTakesNoClockParameter() {
		assertNotNull(findLiveIsHostile(),
				"the entity-reading overload is isHostile(LivingEntity, LivingEntity)");
		for (Method method : MegumiHostilityPolicy.class.getDeclaredMethods()) {
			if (method.getName().equals("isHostile")
					&& method.getParameterCount() > 0
					&& method.getParameterTypes()[0] == LivingEntity.class) {
				assertEquals(2, method.getParameterCount(),
						"a clock argument on the live overload is how gameTime crept in — "
								+ "the policy must read owner.tickCount itself");
			}
		}
	}

	private static Method findLiveIsHostile() {
		return Arrays.stream(MegumiHostilityPolicy.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("isHostile")
						&& method.getParameterCount() == 2
						&& method.getParameterTypes()[0] == LivingEntity.class
						&& method.getParameterTypes()[1] == LivingEntity.class)
				.findFirst().orElse(null);
	}

	@Test
	void neverHurtMeansNeverFresh() {
		int window = MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS;
		// A body that never hurt the owner reports timestamp 0; a running world is far past the
		// window, so the freshness signal must stay silent — the "who" test fences it anyway.
		assertFalse(MegumiHostilityPolicy.aggressorFresh(window + 1L, 0L),
				"a never-hurt timestamp never reads as a fresh aggressor");
	}
}
