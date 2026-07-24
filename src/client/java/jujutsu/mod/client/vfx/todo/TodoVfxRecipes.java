package jujutsu.mod.client.vfx.todo;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Todo's VFX Core recipes. No effect owns separate callbacks or packet receivers. */
public final class TodoVfxRecipes {
	private static final DustParticleOptions TODO_VIOLET = new DustParticleOptions(0xB26CFF, 1.05f);
	private static final DustParticleOptions TODO_EDGE = new DustParticleOptions(0x71D7FF, 0.72f);

	private TodoVfxRecipes() {}

	public static void register() {
		VfxDirector.register(TodoVfxIds.BOOGIE_WOOGIE, TodoVfxRecipes::boogieWoogie);
	}

	private static VfxInstance boogieWoogie(VfxCue cue) {
		return VfxInstance.of(8, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 todoOrigin = context.resolveOrigin(cue);
			Vec3 targetOrigin = todoOrigin.add(cue.anchorOffset());
			RandomSource random = random(cue, 0xB001E13L);
			emitFlash(context, todoOrigin, random);
			emitFlash(context, targetOrigin, random);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.BOOGIE_WOOGIE, 8);
			float proximity = context.proximity(cue, 56.0);
			context.camera().triggerLaunch(1, proximity * 0.45f, initialAgeTicks);
			context.hud().triggerFlash(80, Math.round(62.0f * proximity), initialAgeTicks);
			TodoAnimationHooks.triggerBoogieWoogie(cue);
		});
	}

	private static void emitFlash(VfxContext context, Vec3 origin, RandomSource random) {
		context.burst(TODO_VIOLET, origin, 12, 0.28, 0.16, random);
		context.burst(TODO_EDGE, origin, 8, 0.18, 0.10, random);
		context.ring(TODO_EDGE, origin, 10, 0.34, 0.06, 0.045, random);
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
