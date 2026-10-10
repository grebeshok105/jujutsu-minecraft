package jujutsu.mod.client.vfx.todo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.todo.TodoPlayerGeoAnimatable;
import jujutsu.mod.vfx.VfxCue;

/** Triggers Todo's GeckoLib ability animations from VFX Core cues. */
public final class TodoAnimationHooks {
	public static final ResourceLocation BOOGIE_WOOGIE = JujutsuMod.id("ability.boogie_woogie");
	public static final ResourceLocation FAKE_CLAP = JujutsuMod.id("ability.fake_clap");
	public static final ResourceLocation STONE_THROW = JujutsuMod.id("ability.stone_throw");
	public static final ResourceLocation MOMENTUM_STRIKE = JujutsuMod.id("ability.momentum_strike");
	public static final ResourceLocation PEAK = JujutsuMod.id("ability.peak");
	public static final ResourceLocation REVISED = JujutsuMod.id("ability.revised");

	private TodoAnimationHooks() {}

	public static void triggerBoogieWoogie(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.BOOGIE_WOOGIE_ANIM);
	}

	public static void triggerFakeClap(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.FAKE_CLAP_ANIM);
	}

	public static void triggerStoneThrow(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.STONE_THROW_ANIM);
	}

	public static void triggerMomentumStrike(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.MOMENTUM_STRIKE_ANIM);
	}

	public static void triggerPeak(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.PEAK_ANIM);
	}

	public static void triggerRevised(VfxCue cue) {
		trigger(cue, TodoPlayerGeoAnimatable.REVISED_ANIM);
	}

	private static void trigger(VfxCue cue, String animation) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity entity = cue.anchorEntityId() == VfxCue.NO_ANCHOR
				? null
				: client.level.getEntity(cue.anchorEntityId());
		if (entity != null) {
			TodoPlayerGeoAnimatable.INSTANCE.triggerAction(entity, animation);
		}
	}
}
