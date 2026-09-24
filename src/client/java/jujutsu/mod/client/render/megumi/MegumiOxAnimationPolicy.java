package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;

/** Pure Piercing Ox clip choice: presentation phase, committed action, then travel. */
public final class MegumiOxAnimationPolicy {
	private MegumiOxAnimationPolicy() {}

	public static Clip choose(MegumiShikigamiPresentationPolicy.Phase phase, boolean moving, int actionTicks) {
		if (phase != MegumiShikigamiPresentationPolicy.Phase.ACTIVE) {
			return Clip.IDLE;
		}
		if (actionTicks > MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS
				+ MegumiShikigamiProfile.OX_RECOVERY_TICKS) {
			return Clip.WINDUP;
		}
		if (actionTicks > MegumiShikigamiProfile.OX_RECOVERY_TICKS + 1 + MegumiOxEntity.IMPACT_CLIP_TICKS) {
			return Clip.CHARGE;
		}
		if (actionTicks > MegumiShikigamiProfile.OX_RECOVERY_TICKS) {
			return Clip.IMPACT;
		}
		if (actionTicks > 0) {
			return Clip.RECOVER;
		}
		return moving ? Clip.WALK : Clip.IDLE;
	}

	public enum Clip {
		IDLE,
		WALK,
		WINDUP,
		CHARGE,
		IMPACT,
		RECOVER
	}

}
