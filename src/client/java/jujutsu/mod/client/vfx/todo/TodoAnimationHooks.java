package jujutsu.mod.client.vfx.todo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.todo.TodoPlayerGeoAnimatable;
import jujutsu.mod.vfx.VfxCue;

/** Triggers Todo's GeckoLib clap animation from VFX Core cues. */
public final class TodoAnimationHooks {
	public static final ResourceLocation BOOGIE_WOOGIE = JujutsuMod.id("ability.boogie_woogie");

	private TodoAnimationHooks() {}

	public static void triggerBoogieWoogie(VfxCue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity entity = null;
		if (cue.anchorEntityId() != VfxCue.NO_ANCHOR) {
			entity = client.level.getEntity(cue.anchorEntityId());
		}
		if (entity == null && client.player != null) {
			// Fallback: local caster when cue is nearby (legacy NO_ANCHOR broadcasts).
			double distance = client.player.position().distanceToSqr(cue.origin());
			if (distance <= 4.0) {
				entity = client.player;
			}
		}
		if (entity != null) {
			TodoPlayerGeoAnimatable.INSTANCE.triggerAction(entity, TodoPlayerGeoAnimatable.BOOGIE_WOOGIE_ANIM);
		}
	}
}
