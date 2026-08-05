package jujutsu.mod.client.character.megumi;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.client.render.megumi.MegumiPlayerGeoAnimatable;
import jujutsu.mod.vfx.VfxCue;

/** Resolves a server-confirmed Megumi cue to the player model instance that should animate. */
public final class MegumiAnimationHooks {
	private MegumiAnimationHooks() {}

	public static void triggerDivineDogs(VfxCue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		Entity caster = client.level.getEntity(cue.anchorEntityId());
		if (caster != null) {
			MegumiPlayerGeoAnimatable.INSTANCE.triggerSummon(caster);
		}
	}

	public static void triggerShadowDive(VfxCue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		Entity caster = client.level.getEntity(cue.anchorEntityId());
		if (caster != null) {
			MegumiPlayerGeoAnimatable.INSTANCE.triggerShadowDive(caster);
		}
	}

	public static void triggerShadowEmerge(VfxCue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		Entity caster = client.level.getEntity(cue.anchorEntityId());
		if (caster != null) {
			MegumiPlayerGeoAnimatable.INSTANCE.triggerShadowEmerge(caster);
		}
	}
}
