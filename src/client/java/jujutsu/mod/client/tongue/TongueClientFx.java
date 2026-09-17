package jujutsu.mod.client.tongue;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The tongue's presentation: a particle line from the holder's mouth to the anchor, re-drawn every
 * client tick while the tongue is out. Deliberately <em>not</em> a VFX cue — a cue is an event, and
 * this line has to persist for as long as the state does, so it is driven by
 * {@link TongueClientState} directly (and owns no id in the VFX registry, which is why the player
 * sees it with the vfx pack alone).
 *
 * <p>Cost per tick is bounded by the design: the tongue is at most ten blocks long, and the line is
 * sampled every {@value #SPACING_BLOCKS} blocks, so this is a couple of dozen cheap dust particles.
 */
public final class TongueClientFx {
	/** Blocks between two line samples — the line reads continuous at this spacing. */
	private static final double SPACING_BLOCKS = 0.45;
	/** Upper bound on samples per tick; the design caps the reach at ten blocks, this is headroom. */
	private static final int MAX_SAMPLES = 32;
	/** Flesh red, slightly desaturated so it reads against the dark shadow VFX of the same vessel. */
	private static final int LINE_COLOR = 0xFFC4576A;
	/** Dust size: big enough to read as a body, small enough to leave the line a texture. */
	private static final float LINE_SCALE = 0.55f;
	/** The line leaves the mouth, not the eyes: a fixed drop under the eye position. */
	private static final double MOUTH_DROP_BLOCKS = 0.35;
	/** Lateral jitter per sample, so the line has thickness without needing a second emitter pass. */
	private static final double JITTER_BLOCKS = 0.06;

	private static final DustParticleOptions LINE = new DustParticleOptions(LINE_COLOR, LINE_SCALE);

	private TongueClientFx() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TongueClientFx::tick);
	}

	private static void tick(Minecraft client) {
		if (!TongueClientState.isActive()) {
			return;
		}
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}
		Vec3 origin = player.getEyePosition().add(0.0, -MOUTH_DROP_BLOCKS, 0.0);
		Vec3 span = TongueClientState.anchor().subtract(origin);
		int samples = Mth.clamp((int) Math.round(span.length() / SPACING_BLOCKS), 1, MAX_SAMPLES);
		double stepX = span.x / samples;
		double stepY = span.y / samples;
		double stepZ = span.z / samples;
		for (int index = 0; index <= samples; index++) {
			level.addParticle(LINE,
					origin.x + stepX * index + jitter(player),
					origin.y + stepY * index + jitter(player),
					origin.z + stepZ * index + jitter(player),
					0.0, 0.0, 0.0);
		}
	}

	private static double jitter(LocalPlayer player) {
		return (player.getRandom().nextDouble() - 0.5) * 2.0 * JITTER_BLOCKS;
	}
}
