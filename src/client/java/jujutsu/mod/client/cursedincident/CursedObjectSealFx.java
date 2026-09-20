package jujutsu.mod.client.cursedincident;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import jujutsu.mod.cursedincident.object.CursedObjectState;
import jujutsu.mod.cursedincident.object.SealState;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuParticles;

/**
 * Object-side seal degradation: a sealed cursed object lying in the world shows its
 * integrity band — faint motes when the seal starts to wear, a crackle when it is half
 * gone, an unstable glow near failure, and a heavy leak when it is about to break.
 * The zone renderer already draws the seal band around the incident; this is the same
 * contract on the physical object itself.
 */
public final class CursedObjectSealFx {
	private static final int SCAN_INTERVAL_TICKS = 4;
	private static final double MAX_DISTANCE_SQR = 48.0 * 48.0;
	private static int ticksUntilScan;

	private CursedObjectSealFx() {
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}
		if (--ticksUntilScan > 0) {
			return;
		}
		ticksUntilScan = SCAN_INTERVAL_TICKS;
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity item)
					|| item.distanceToSqr(client.player) > MAX_DISTANCE_SQR) {
				continue;
			}
			CursedObjectState state = item.getItem().get(JujutsuDataComponents.CURSED_OBJECT_STATE);
			if (state == null || !state.sealed()) {
				continue;
			}
			int maximum = SealState.integrityMax(state.sealTier());
			double ratio = maximum <= 0 ? 0.0 : (double) state.sealIntegrity() / maximum;
			SealState.DegradationSignal signal = SealState.signalAt(ratio);
			if (signal != SealState.DegradationSignal.NONE) {
				emit(level, item, signal);
			}
		}
	}

	private static void emit(ClientLevel level, ItemEntity item, SealState.DegradationSignal signal) {
		double x = item.getX();
		double y = item.getY() + 0.25;
		double z = item.getZ();
		switch (signal) {
			case FAINT_PARTICLES -> {
				level.addParticle(JujutsuParticles.CURSED_MOTE,
						x + spread(item), y, z + spread(item), 0.0, 0.015, 0.0);
			}
			case CRACKING_SOUND -> {
				level.addParticle(JujutsuParticles.CURSED_MOTE,
						x + spread(item), y, z + spread(item), 0.0, 0.02, 0.0);
				if (item.getRandom().nextInt(8) == 0) {
					level.playLocalSound(x, y, z, SoundEvents.TURTLE_EGG_CRACK,
							SoundSource.BLOCKS, 0.35f, 1.1f, false);
				}
			}
			case UNSTABLE_GLOW -> {
				for (int i = 0; i < 3; i++) {
					level.addParticle(JujutsuParticles.CURSED_MOTE,
							x + spread(item), y + item.getRandom().nextDouble() * 0.3,
							z + spread(item), 0.0, 0.03, 0.0);
				}
			}
			case FAILING -> {
				for (int i = 0; i < 4; i++) {
					level.addParticle(JujutsuParticles.CURSED_MOTE,
							x + spread(item), y + item.getRandom().nextDouble() * 0.4,
							z + spread(item), 0.0, 0.05, 0.0);
				}
				if (item.getRandom().nextInt(5) == 0) {
					level.playLocalSound(x, y, z, SoundEvents.TURTLE_EGG_CRACK,
							SoundSource.BLOCKS, 0.5f, 0.8f, false);
				}
			}
			default -> {
			}
		}
	}

	private static double spread(ItemEntity item) {
		return (item.getRandom().nextDouble() - 0.5) * 0.5;
	}
}
