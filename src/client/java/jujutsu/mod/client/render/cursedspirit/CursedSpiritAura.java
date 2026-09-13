package jujutsu.mod.client.render.cursedspirit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.cursedspirit.CursedSpiritAuraRuntime;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;

/**
 * Client-side grade-aura emitter (R59 spike): 1–3 dust motes per tick per
 * body, density and colour by grade, plus a rare soul-flame spark on grade 3.
 * Reads only the synced grade — never HP/damage/speed — and sends nothing to
 * the server, so there is no per-second cue spam.
 *
 * <p><b>BALANCE:</b> every density/colour number comes from
 * {@link CursedSpiritAuraRuntime} and tunes after the mandatory live review
 * (see that class); this emitter only executes the verdict.
 *
 * <p>Perception (spec #81, R59): emission passes through block 1's
 * {@link CurseRenderGate}, so a non-perceiving viewer sees neither the body
 * nor its aura. The gate resolves the local viewer itself.
 */
public final class CursedSpiritAura {
	private CursedSpiritAura() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CursedSpiritAura::tick);
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof CursedSpiritEntity spirit && shouldEmitFor(spirit)) {
				emit(level, spirit);
			}
		}
	}

	/**
	 * The one perception gate for the aura: liveness plus block 1's render
	 * gate. Fail-open without a local viewer, like the gate itself.
	 */
	static boolean shouldEmitFor(CursedSpiritEntity spirit) {
		return spirit.isAlive() && !spirit.isRemoved() && CurseRenderGate.shouldRender(spirit);
	}
	private static void emit(ClientLevel level, CursedSpiritEntity spirit) {
		CursedSpiritGrade grade = spirit.grade();
		int motes = CursedSpiritAuraRuntime.particlesPerTick(grade);
		DustParticleOptions dust =
				new DustParticleOptions(CursedSpiritAuraRuntime.primaryColorArgb(grade),
						CursedSpiritAuraRuntime.particleSize(grade));
		double halfWidth = spirit.getBbWidth() * 0.8;
		double height = Math.max(0.6, spirit.getBbHeight());
		for (int i = 0; i < motes; i++) {
			double x = spirit.getX() + (spirit.getRandom().nextDouble() - 0.5) * 2.0 * halfWidth;
			double y = spirit.getY() + spirit.getRandom().nextDouble() * height;
			double z = spirit.getZ() + (spirit.getRandom().nextDouble() - 0.5) * 2.0 * halfWidth;
			level.addParticle(dust, x, y, z, 0.0, 0.015, 0.0);
		}
		int flameEvery = CursedSpiritAuraRuntime.soulFlameEveryTicks(grade);
		if (flameEvery > 0 && spirit.tickCount % flameEvery == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
					spirit.getX() + (spirit.getRandom().nextDouble() - 0.5) * halfWidth,
					spirit.getY() + height * 0.9,
					spirit.getZ() + (spirit.getRandom().nextDouble() - 0.5) * halfWidth,
					0.0, 0.05, 0.0);
		}
	}
}
