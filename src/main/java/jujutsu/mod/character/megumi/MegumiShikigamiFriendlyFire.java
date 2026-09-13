package jujutsu.mod.character.megumi;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who an area-of-effect shikigami effect must never hit: the owner, allies, and every shikigami
 * body the owner controls (of either the new pack or the Divine Dog pack). Sic/pull targeting uses
 * {@link MegumiSummonRuntime#isEligibleTarget} instead; this gate is for AoE (jet, bump) only.
 */
public final class MegumiShikigamiFriendlyFire {
	private MegumiShikigamiFriendlyFire() {}

	public static boolean isProtected(LivingEntity owner, Entity candidate) {
		if (candidate == null || owner == null || candidate == owner) {
			return true;
		}
		if (candidate instanceof Player player && player.isSpectator()) {
			return true;
		}
		if (owner.isAlliedTo(candidate)) {
			return true;
		}
		if (candidate instanceof MegumiShikigamiEntity body && owner.getUUID().equals(body.ownerUuid())) {
			return true;
		}
		return candidate instanceof MegumiDivineDogEntity dog && owner.getUUID().equals(dog.ownerUuid());
	}

	/**
	 * The narrow "our side" gate (issue #79): the owner and the bodies they control — nothing else.
	 * Unlike {@link #isProtected}, allies and neutrals are <em>not</em> excluded, because the
	 * presence's job is to shove them aside (without damage). Mixing the two gates is exactly how an
	 * ally ends up immune to an area effect that should clear it.
	 */
	public static boolean isOwnSideOnly(LivingEntity owner, Entity candidate) {
		if (candidate == null || owner == null || candidate == owner) {
			return true;
		}
		if (candidate instanceof MegumiShikigamiEntity body && owner.getUUID().equals(body.ownerUuid())) {
			return true;
		}
		return candidate instanceof MegumiDivineDogEntity dog && owner.getUUID().equals(dog.ownerUuid());
	}
}