package jujutsu.mod.client.vfx.megumi;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.client.character.megumi.MegumiAnimationHooks;
import jujutsu.mod.client.render.HiddenBodyRenderGate;
import jujutsu.mod.client.render.ShadowBodySink;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/**
 * Megumi's recipe pack: Divine Dog effects and the Shadow Kit (trap pool, grip, dive/ripple/emerge),
 * all composed from the existing VFX Core channels. One-shot per cue with opening-beat guards;
 * continuous trap visuals ride the server's periodic cue re-emission.
 */
public final class MegumiVfxRecipes {
	private static final DustParticleOptions SHADOW_DARK = new DustParticleOptions(0x102E2B, 0.75f);

	private MegumiVfxRecipes() {}

	public static final int SUMMON_DURATION_TICKS = 16;
	public static final int RECALL_DURATION_TICKS = 12;
	private static final int SUMMON_BODY_DURATION_TICKS = 1;
	private static final int SIC_DURATION_TICKS = 8;
	private static final int POUNCE_DURATION_TICKS = 6;

	// Shadow Trap: open 16 t beats the 40-tick zone pulse, whose 42-tick window must overlap it
	// without a hole; close rides the trap teardown. Grip is a per-body pull-down flutter.
	public static final int SHADOW_TRAP_OPEN_DURATION_TICKS = 16;
	public static final int SHADOW_TRAP_ZONE_DURATION_TICKS = 42;
	private static final int SHADOW_TRAP_GRIP_DURATION_TICKS = 8;
	public static final int SHADOW_TRAP_CLOSE_DURATION_TICKS = 12;
	private static final int SHADOW_DIVE_DURATION_TICKS = 12;
	private static final int SHADOW_RIPPLE_DURATION_TICKS = 8;
	private static final int SHADOW_EMERGE_DURATION_TICKS = 12;

	// Shadow Drop: open 10 t beats the 5-tick zone pulse, whose 7-tick window keeps the hovering
	// disc solid; close rides the fall. The tell is motes dripping off the disc rim.
	public static final int DROP_ZONE_OPEN_DURATION_TICKS = 10;
	public static final int DROP_ZONE_DURATION_TICKS = 7;
	public static final int DROP_ZONE_CLOSE_DURATION_TICKS = 8;

	/**
	 * Ripple re-emits arrive every 5 ticks while under (the first one the moment the body actually
	 * hides, at the end of the sink); an 8-tick TTL keeps the body hidden with slack. The dive cue
	 * deliberately hides nothing: the sink is the watchable interruption window.
	 */
	private static final int RIPPLE_HIDE_TTL_TICKS = 8;
	private static final float TRAP_POOL_RADIUS = (float) MegumiProfile.SHADOW_TRAP_RADIUS;
	private static final float DROP_ZONE_RADIUS = (float) MegumiProfile.DROP_ZONE_RADIUS;

	public static void register() {
		VfxDirector.register(MegumiVfxIds.DOGS_SUMMON_BODY, MegumiVfxRecipes::summonBody);
		VfxDirector.register(MegumiVfxIds.DOGS_SUMMON, MegumiVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.DOGS_RECALL, MegumiVfxRecipes::recall);
		VfxDirector.register(MegumiVfxIds.DOGS_SIC, MegumiVfxRecipes::sic);
		VfxDirector.register(MegumiVfxIds.DOGS_POUNCE, MegumiVfxRecipes::pounce);
		VfxDirector.register(MegumiVfxIds.SHADOW_TRAP_OPEN, MegumiVfxRecipes::shadowTrapOpen);
		VfxDirector.register(MegumiVfxIds.SHADOW_TRAP_ZONE, MegumiVfxRecipes::shadowTrapZone);
		VfxDirector.register(MegumiVfxIds.SHADOW_TRAP_GRIP, MegumiVfxRecipes::shadowTrapGrip);
		VfxDirector.register(MegumiVfxIds.SHADOW_TRAP_CLOSE, MegumiVfxRecipes::shadowTrapClose);
		VfxDirector.register(MegumiVfxIds.SHADOW_DIVE, MegumiVfxRecipes::shadowDive);
		VfxDirector.register(MegumiVfxIds.SHADOW_RIPPLE, MegumiVfxRecipes::shadowRipple);
		VfxDirector.register(MegumiVfxIds.SHADOW_EMERGE, MegumiVfxRecipes::shadowEmerge);
		VfxDirector.register(MegumiVfxIds.DROP_ZONE_OPEN, MegumiVfxRecipes::dropZoneOpen);
		VfxDirector.register(MegumiVfxIds.DROP_ZONE, MegumiVfxRecipes::dropZone);
		VfxDirector.register(MegumiVfxIds.DROP_ZONE_CLOSE, MegumiVfxRecipes::dropZoneClose);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xD0655A11L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.42, 0.13, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0), 10, 0.58, 0.0, 0.05, random);
		});
	}

	private static VfxInstance summonBody(VfxCue cue) {
		return VfxInstance.of(SUMMON_BODY_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Entity anchor = context.client().level == null ? null : context.client().level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerDivineDogs(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
		});
	}

	private static VfxInstance recall(VfxCue cue) {
		return VfxInstance.of(RECALL_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xD0652ECA11L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE, RECALL_DURATION_TICKS);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.08, 0.0), 14, 0.68, 0.0, -0.08, random);
		});
	}

	private static VfxInstance sic(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x51C7A26E7L);
			context.ring(SHADOW_DARK, target, 14, 0.55, 0.0, -0.05, random);
			context.burst(SHADOW_DARK, target, 7, 0.18, 0.06, random);
		});
	}

	private static VfxInstance pounce(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xD065B00FL);
			context.ring(SHADOW_DARK, target, 18, 0.72, 0.02, 0.06, random);
			context.burst(SHADOW_DARK, target.add(0.0, 0.18, 0.0), 12, 0.28, 0.12, random);
		});
	}

	private static VfxInstance shadowTrapOpen(VfxCue cue) {
		return VfxInstance.of(SHADOW_TRAP_OPEN_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5A7B04D1L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_TRAP_OPEN, SHADOW_TRAP_OPEN_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 16, 0.5, 0.15, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0), 18, TRAP_POOL_RADIUS * 0.85, 0.0, 0.06, random);
		});
	}

	private static VfxInstance shadowTrapZone(VfxCue cue) {
		return VfxInstance.of(SHADOW_TRAP_ZONE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x2E07C041L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_POOL, SHADOW_TRAP_ZONE_DURATION_TICKS);
			// Slow inward pull: negative horizontal speed drags the motes toward the pool centre.
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.06, 0.0), 12, TRAP_POOL_RADIUS * 0.85, 0.0, -0.05, random);
		});
	}

	private static VfxInstance shadowTrapGrip(VfxCue cue) {
		return VfxInstance.of(SHADOW_TRAP_GRIP_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x4A7E11L);
			ClientLevel level = context.client().level;
			if (level == null) {
				return;
			}
			// Dark motes around the torso dragged DOWN the body: the grip pulls, not floats.
			int count = context.quality().scaledCount(10);
			for (int index = 0; index < count; index++) {
				double downwardSpeed = -(0.10 + random.nextDouble() * 0.14);
				level.addParticle(JujutsuParticles.MEGUMI_SHADOW_MOTE,
						origin.x + (random.nextDouble() - 0.5) * 0.9,
						origin.y + 1.2 + (random.nextDouble() - 0.5) * 0.9,
						origin.z + (random.nextDouble() - 0.5) * 0.9,
						(random.nextDouble() - 0.5) * 0.05, downwardSpeed, (random.nextDouble() - 0.5) * 0.05);
			}
		});
	}

	private static VfxInstance shadowTrapClose(VfxCue cue) {
		return VfxInstance.of(SHADOW_TRAP_CLOSE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xC105A74EL);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_TRAP_CLOSE, SHADOW_TRAP_CLOSE_DURATION_TICKS);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.08, 0.0), 16, TRAP_POOL_RADIUS * 0.85, 0.0, -0.10, random);
		});
	}

	private static VfxInstance shadowDive(VfxCue cue) {
		return VfxInstance.of(SHADOW_DIVE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			// Start the third/first-person dive: the body lowers over SHADOW_SINK_TICKS from the cue's
			// authoritative server time; the first ripple completes the sink and hides the body.
			ShadowBodySink.beginSink(cue.anchorEntityId(), cue.startGameTime(),
					MegumiProfile.SHADOW_SINK_TICKS);
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xD1A78B05L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SHADOW_DIVE_DURATION_TICKS);
			MegumiAnimationHooks.triggerShadowDive(cue);
			boolean localCaster = cue.anchorEntityId() != VfxCue.NO_ANCHOR
					&& context.client().player != null
					&& cue.anchorEntityId() == context.client().player.getId();
			if (localCaster) {
				context.postProcess().triggerBlur(700, initialAgeTicks);
				context.hud().triggerNausea(0.15f, 1200, initialAgeTicks);
				context.firstPerson().triggerSign(0.0f);
			}
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0), 12, 0.8, 0.0, 0.04, random);
		});
	}

	private static VfxInstance shadowRipple(VfxCue cue) {
		return VfxInstance.of(SHADOW_RIPPLE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x9A15B07L);
			// The ripple is the "body is fully under" beat: complete the dive and re-arm the hold TTL.
			ShadowBodySink.completeSink(cue.anchorEntityId());
			HiddenBodyRenderGate.markHidden(cue.anchorEntityId(), RIPPLE_HIDE_TTL_TICKS);
			// Faint tell: a tiny dark ring and a couple of motes at the walker's feet.
			context.ring(SHADOW_DARK, origin.add(0.0, 0.02, 0.0), 4, 0.5, 0.0, 0.03, random);
			context.burst(SHADOW_DARK, origin.add(0.0, 0.06, 0.0), 2, 0.12, 0.02, random);
		});
	}

	private static VfxInstance shadowEmerge(VfxCue cue) {
		return VfxInstance.of(SHADOW_EMERGE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xE4E7B01L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE, SHADOW_EMERGE_DURATION_TICKS);
			MegumiAnimationHooks.triggerShadowEmerge(cue);
			// Start the rise before revealing: the body lifts over SHADOW_EMERGE_TICKS from the cue's
			// authoritative server time while the reveal below clears the render gate.
			ShadowBodySink.beginEmerge(cue.anchorEntityId(), cue.startGameTime(),
					MegumiProfile.SHADOW_EMERGE_TICKS);
			HiddenBodyRenderGate.markRevealed(cue.anchorEntityId());
			boolean localCaster = cue.anchorEntityId() != VfxCue.NO_ANCHOR
					&& context.client().player != null
					&& cue.anchorEntityId() == context.client().player.getId();
			if (localCaster) {
				context.postProcess().triggerBlur(300, initialAgeTicks);
			}
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.4, 0.18, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.06, 0.0), 12, 0.7, 0.0, 0.08, random);
		});
	}

	private static VfxInstance dropZoneOpen(VfxCue cue) {
		return VfxInstance.of(DROP_ZONE_OPEN_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x0D7A0A11L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_TRAP_OPEN, DROP_ZONE_OPEN_DURATION_TICKS);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0), 6, DROP_ZONE_RADIUS * 0.85, 0.0, 0.05, random);
		});
	}

	private static VfxInstance dropZone(VfxCue cue) {
		return VfxInstance.of(DROP_ZONE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x0D7A0A12L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_POOL, DROP_ZONE_DURATION_TICKS);
			ClientLevel level = context.client().level;
			if (level == null) {
				return;
			}
			// Two motes dripping off the disc rim with a negative y velocity: the tell that a block
			// is about to fall out of the zone.
			double angle = random.nextDouble() * Math.PI * 2.0;
			for (int index = 0; index < 2; index++) {
				level.addParticle(JujutsuParticles.MEGUMI_SHADOW_MOTE,
						origin.x + Math.cos(angle) * DROP_ZONE_RADIUS,
						origin.y - 0.1,
						origin.z + Math.sin(angle) * DROP_ZONE_RADIUS,
						(random.nextDouble() - 0.5) * 0.05, -(0.10 + random.nextDouble() * 0.10),
						(random.nextDouble() - 0.5) * 0.05);
				angle += Math.PI;
			}
		});
	}

	private static VfxInstance dropZoneClose(VfxCue cue) {
		return VfxInstance.of(DROP_ZONE_CLOSE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_TRAP_CLOSE, DROP_ZONE_CLOSE_DURATION_TICKS);
		});
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
