package jujutsu.mod.cursedspirit.ability.effects;

import net.minecraft.server.level.ServerLevel;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Regen (Block 3, Step 8): a heal-over-time window plus its own retreat steering. Damage
 * never interrupts the HoT. Retreat is a brain query ({@code shouldRetreat}), executed by
 * the attack goal — never a second goal (MOVE flags would collide).
 */
public final class RegenRetreatEffect {
	private RegenRetreatEffect() {
	}

	public static boolean start(CursedSpiritEntity spirit, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		if (!(spirit.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.REGEN, now + params.durationTicks(), params,
				null, now)) {
			return false;
		}
		JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.anchored(CursedSpiritVfxIds.REGEN, spirit.position(), spirit.getId(),
						spirit.position(), 1, now, spirit.getRandom().nextLong()),
				CursePerception::perceives);
		return true;
	}

	/** HoT pulse; the window (and the retreat with it) ends on timeout. */
	public static void tick(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain brain, CursedSpiritAbilityBrain.EffectState state, long now) {
		long age = now - state.startedGameTime();
		if (age > 0 && age % CursedSpiritAbilityProfile.REGEN_PULSE_PERIOD_TICKS == 0) {
			spirit.heal((float) state.params().strength());
			JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
					CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
					VfxCues.anchored(CursedSpiritVfxIds.REGEN, spirit.position(), spirit.getId(),
							spirit.position(), 1, now, spirit.getRandom().nextLong()),
					CursePerception::perceives);
		}
	}
}
