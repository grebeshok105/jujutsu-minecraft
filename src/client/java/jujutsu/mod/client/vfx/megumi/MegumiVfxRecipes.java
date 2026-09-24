package jujutsu.mod.client.vfx.megumi;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.client.character.megumi.MegumiAnimationHooks;
import jujutsu.mod.client.render.HiddenBodyRenderGate;
import jujutsu.mod.client.render.ShadowBodySink;
import jujutsu.mod.client.vfx.VfxRecipe;
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

	// Shikigami slice: dive streak outlives the dive commit by a beat; the shock owns its flash.
	private static final int NUE_DIVE_DURATION_TICKS = 8;
	private static final int NUE_SHOCK_DURATION_TICKS = 12;
	// The partial wings: a short unfold snap, not a summon — the sustained glide is vanilla's own
	// fall-flying pose, so nothing has to be re-emitted while the wings stay out.
	private static final int NUE_PARTIAL_WINGS_DURATION_TICKS = 10;
	private static final int TOAD_TONGUE_DURATION_TICKS = 8;
	private static final int RABBITS_POP_DURATION_TICKS = 6;
	private static final int ELEPHANT_JET_DURATION_TICKS = 8;

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
		VfxDirector.register(MegumiVfxIds.NUE_SUMMON, MegumiVfxRecipes::nueSummon);
		VfxDirector.register(MegumiVfxIds.NUE_DIVE, MegumiVfxRecipes::nueDive);
		VfxDirector.register(MegumiVfxIds.NUE_SHOCK, MegumiVfxRecipes::nueShock);
		VfxDirector.register(MegumiVfxIds.NUE_PARTIAL_WINGS, MegumiVfxRecipes::nuePartialWings);
		VfxDirector.register(MegumiVfxIds.SHIKIGAMI_SIC, MegumiVfxRecipes::shikigamiSic);
		VfxDirector.register(MegumiVfxIds.SHIKIGAMI_RECALL, MegumiVfxRecipes::shikigamiRecall);
		VfxDirector.register(MegumiVfxIds.TOAD_SUMMON, MegumiVfxRecipes::toadSummon);
		VfxDirector.register(MegumiVfxIds.TOAD_TONGUE, MegumiVfxRecipes::toadTongue);
		VfxDirector.register(MegumiVfxIds.RABBITS_SUMMON, MegumiVfxRecipes::rabbitsSummon);
		VfxDirector.register(MegumiVfxIds.RABBITS_POP, MegumiVfxRecipes::rabbitsPop);
		VfxDirector.register(MegumiVfxIds.ELEPHANT_SUMMON, MegumiVfxRecipes::elephantSummon);
		VfxDirector.register(MegumiVfxIds.ELEPHANT_JET, MegumiVfxRecipes::elephantJet);
		VfxDirector.register(MegumiVfxIds.SERPENT_SUMMON_BODY, MegumiVfxRecipes::serpentSummonBody);
		VfxDirector.register(MegumiVfxIds.SERPENT_SUMMON, MegumiVfxRecipes::serpentSummon);
		VfxDirector.register(MegumiVfxIds.DEER_SUMMON_BODY, MegumiVfxRecipes::deerSummonBody);
		VfxDirector.register(MegumiVfxIds.DEER_SUMMON, MegumiVfxRecipes::deerSummon);
		VfxDirector.register(MegumiVfxIds.OX_SUMMON_BODY, MegumiVfxRecipes::oxSummonBody);
		VfxDirector.register(MegumiVfxIds.OX_SUMMON, MegumiVfxRecipes::oxSummon);
		VfxDirector.register(MegumiVfxIds.TIGER_SUMMON_BODY, MegumiVfxRecipes::tigerSummonBody);
		VfxDirector.register(MegumiVfxIds.TIGER_SUMMON, MegumiVfxRecipes::tigerSummon);
		registerMechanicRecipes();
	}

	/** Mechanic recipes follow their cue id's PLANNED→LIVE graduation automatically. */
	private static void registerMechanicRecipes() {
		registerIfLive(MegumiVfxIds.SERPENT_EMERGE, MegumiVfxRecipes::serpentEmerge);
		registerIfLive(MegumiVfxIds.SERPENT_BIND, MegumiVfxRecipes::serpentBind);
		registerIfLive(MegumiVfxIds.SERPENT_RELEASE, MegumiVfxRecipes::serpentRelease);
		registerIfLive(MegumiVfxIds.DEER_PULSE, MegumiVfxRecipes::deerPulse);
		registerIfLive(MegumiVfxIds.DEER_CLEANSE, MegumiVfxRecipes::deerCleanse);
		registerIfLive(MegumiVfxIds.OX_WINDUP, MegumiVfxRecipes::oxWindup);
		registerIfLive(MegumiVfxIds.OX_CHARGE, MegumiVfxRecipes::oxCharge);
		registerIfLive(MegumiVfxIds.OX_IMPACT, MegumiVfxRecipes::oxImpact);
		registerIfLive(MegumiVfxIds.OX_WALL_HIT, MegumiVfxRecipes::oxWallHit);
		registerIfLive(MegumiVfxIds.TIGER_STRIKE, MegumiVfxRecipes::tigerStrike);
		registerIfLive(MegumiVfxIds.TIGER_MISS, MegumiVfxRecipes::tigerMiss);
		registerIfLive(MegumiVfxIds.TIGER_RECOVER, MegumiVfxRecipes::tigerRecover);
	}

	private static void registerIfLive(ResourceLocation id, VfxRecipe recipe) {
		if (MegumiVfxIds.LIVE.contains(id)) {
			VfxDirector.register(id, recipe);
		}
	}

	/** Rabbit Escape's summon pool: one shared shadow over a scatter of small pops. */
	private static VfxInstance rabbitsSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x52414201L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 12, 0.55, 0.14, random);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.15, 0.0), 8, 0.45, 0.10, random);
		});
	}

	/** One rabbit arriving (upkeep), bumping a hostile, or the swarm's expiry burst. */
	private static VfxInstance rabbitsPop(VfxCue cue) {
		return VfxInstance.of(RABBITS_POP_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x52414202L);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.15, 0.0), 10, 0.30, 0.08, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.05, 0.0), 8, 0.45, 0.0, 0.05, random);
		});
	}

	/** Max Elephant's summon pool: a heavy shadow opening. */
	private static VfxInstance elephantSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x454C3101L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.20, 0.0), 20, 0.70, 0.16, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.06, 0.0), 16, 1.6, 0.0, 0.08, random);
		});
	}

	/** The trunk jet: the windup splash and every damage pulse ride this cue. */
	private static VfxInstance elephantJet(VfxCue cue) {
		return VfxInstance.of(ELEPHANT_JET_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 trunk = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x454C3102L);
			NueArcRenderer.registerElephantJet(cue);
			context.burst(ParticleTypes.SPLASH, trunk, 10, 0.35, 0.20, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, trunk, 8, 0.55, 0.02, 0.10, random);
		});
	}

	/** Toad's summon pool: the shared shadow opening over a wet splash. */
	private static VfxInstance toadSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x70440001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.40, 0.13, random);
			context.burst(ParticleTypes.SPLASH, origin.add(0.0, 0.25, 0.0), 10, 0.45, 0.12, random);
		});
	}

	/** The tongue: a short flick on the body, then the grab flash on the target. */
	private static VfxInstance toadTongue(VfxCue cue) {
		return VfxInstance.of(TOAD_TONGUE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x70440002L);
			context.burst(ParticleTypes.SPLASH, target, 10, 0.35, 0.15, random);
			context.burst(SHADOW_DARK, target, 6, 0.20, 0.10, random);
		});
	}

	// --- shikigami slice (Nue / Toad / Rabbit Escape / Max Elephant) ---

	/** Nue's summon pool: the dogs' shadow opening with an electric crackle. */
	private static VfxInstance nueSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x4E554501L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.40, 0.13, random);
			context.burst(ParticleTypes.ELECTRIC_SPARK, origin.add(0.0, 0.20, 0.0), 6, 0.30, 0.05, random);
		});
	}

	/** Nue commits to the dive: a short sparking streak on the body. */
	private static VfxInstance nueDive(VfxCue cue) {
		return VfxInstance.of(NUE_DIVE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x4E554502L);
			context.burst(SHADOW_DARK, origin, 8, 0.25, 0.20, random);
			context.burst(ParticleTypes.ELECTRIC_SPARK, origin, 4, 0.20, 0.08, random);
			if (cue.direction().lengthSqr() > 1.0E-8) {
				for (int index = 1; index <= 5; index++) {
					Vec3 alongPath = origin.add(cue.direction().scale(index * 0.45));
					context.burst(ParticleTypes.ELECTRIC_SPARK, alongPath, 2, 0.08, 0.05, random);
				}
			}
		});
	}

	/** The electric discharge on impact. */
	private static VfxInstance nueShock(VfxCue cue) {
		return VfxInstance.of(NUE_SHOCK_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = cue.origin();
			NueArcState.shared().registerCue(cue);
			RandomSource random = random(cue, 0x4E554503L);
			context.burst(ParticleTypes.ELECTRIC_SPARK, target.add(0.0, 0.4, 0.0), 8, 0.28, 0.16, random);
			context.ring(SHADOW_DARK, target, 10, 0.50, 0.02, 0.0, random);
		});
	}

	/**
	 * The partial wings snap open (issue #108): a short electric crackle at the owner's shoulders.
	 * No shadow pool — the wings are a worn manifestation, not a summon, and a ground impact under
	 * the feet reads as something crawling out of the shadow that never arrives.
	 */
	private static VfxInstance nuePartialWings(VfxCue cue) {
		return VfxInstance.of(NUE_PARTIAL_WINGS_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 shoulders = cue.origin().add(0.0, 1.0, 0.0);
			RandomSource random = random(cue, 0x4E554504L);
			context.burst(ParticleTypes.ELECTRIC_SPARK, shoulders, 14, 0.40, 0.18, random);
			context.burst(ParticleTypes.POOF, shoulders, 4, 0.20, 0.06, random);
		});
	}

	/** Generic sic marker (the dog Sic visual without the dog-specific seed). */
	private static VfxInstance shikigamiSic(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x51C7A3E7L);
			context.ring(SHADOW_DARK, target, 14, 0.55, 0.0, -0.05, random);
			context.burst(SHADOW_DARK, target, 7, 0.18, 0.06, random);
		});
	}

	/** Generic recall sweep for any shikigami pack. */
	private static VfxInstance shikigamiRecall(VfxCue cue) {
		return VfxInstance.of(RECALL_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x51C7A3E8L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE, RECALL_DURATION_TICKS);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.08, 0.0), 14, 0.68, 0.0, -0.08, random);
		});
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
		MegumiAnimationHooks.triggerDivineDogs(cue);
		return summonBodyFor(cue, null);
	}

	/** One player-anchored body cue per summoned shikigami type: sign clip + first-person beat. */
	private static VfxInstance summonBodyFor(VfxCue cue,
			java.util.function.Consumer<VfxCue> hook) {
		return VfxInstance.of(SUMMON_BODY_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Entity anchor = context.client().level == null ? null : context.client().level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				if (hook != null) {
					hook.accept(cue);
				}
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
		});
	}

	private static VfxInstance serpentSummonBody(VfxCue cue) {
		return summonBodyFor(cue, MegumiAnimationHooks::triggerSerpentSummon);
	}

	private static VfxInstance deerSummonBody(VfxCue cue) {
		return summonBodyFor(cue, MegumiAnimationHooks::triggerDeerSummon);
	}

	private static VfxInstance oxSummonBody(VfxCue cue) {
		return summonBodyFor(cue, MegumiAnimationHooks::triggerOxSummon);
	}

	private static VfxInstance tigerSummonBody(VfxCue cue) {
		return summonBodyFor(cue, MegumiAnimationHooks::triggerTigerSummon);
	}

	// --- roster expansion (Great Serpent / Round Deer / Piercing Ox / Tiger Funeral) ---

	/** Great Serpent's summon pool: the shadow opening with a slithering dark accent. */
	private static VfxInstance serpentSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5E525001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 16, 0.50, 0.14, random);
			context.burst(SHADOW_DARK, origin.add(0.0, 0.15, 0.0), 8, 0.35, 0.10, random);
		});
	}

	/** The serpent breaks the surface under its target: exit pool and an upward dark burst. */
	private static VfxInstance serpentEmerge(VfxCue cue) {
		return VfxInstance.of(NUE_DIVE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x5E525002L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, NUE_DIVE_DURATION_TICKS);
			context.burst(SHADOW_DARK, origin.add(0.0, 0.25, 0.0), 10, 0.40, 0.22, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin, 10, 0.8, 0.0, 0.12, random);
		});
	}

	/** The coil closes: a tightening dark ring pulled toward the victim's chest. */
	private static VfxInstance serpentBind(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x5E525003L);
			context.ring(SHADOW_DARK, target.add(0.0, 0.9, 0.0), 16, 0.9, 0.0, -0.06, random);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, target.add(0.0, 0.6, 0.0), 8, 0.30, -0.05, random);
		});
	}

	/** The bind lets go: motes dissipate where the victim stood. */
	private static VfxInstance serpentRelease(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x5E525004L);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, target.add(0.0, 0.5, 0.0), 10, 0.35, 0.16, random);
		});
	}

	/** Round Deer's summon pool: the shadow opening with a warm rising accent. */
	private static VfxInstance deerSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xDE320001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.42, 0.13, random);
			context.burst(ParticleTypes.END_ROD, origin.add(0.0, 0.4, 0.0), 5, 0.25, 0.12, random);
		});
	}

	/** A heal pulse lands: soft bright sparks over the healed body. */
	private static VfxInstance deerPulse(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xDE320002L);
			context.burst(ParticleTypes.END_ROD, target.add(0.0, 0.8, 0.0), 8, 0.30, 0.14, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, target.add(0.0, 0.2, 0.0), 8, 0.5, 0.0, 0.06, random);
		});
	}

	/** A cleanse strips an effect: sparks rise off the cleared body. */
	private static VfxInstance deerCleanse(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xDE320003L);
			context.burst(ParticleTypes.END_ROD, target.add(0.0, 1.0, 0.0), 10, 0.25, 0.20, random);
		});
	}

	/** Piercing Ox's summon pool: the shadow opening with a ground-shaking dust edge. */
	private static VfxInstance oxSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x0F000001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.12, 0.0), 18, 0.55, 0.15, random);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.2, 0.0), 8, 0.50, 0.10, random);
		});
	}

	/** The windup telegraph: the ox paws the ground before committing. */
	private static VfxInstance oxWindup(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x0F000002L);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.10, 0.0), 10, 0.45, 0.08, random);
			context.ring(SHADOW_DARK, origin, 8, 0.7, 0.0, -0.04, random);
		});
	}

	/** The charge launches: a dust trail along the committed line, scaled by real distance. */
	private static VfxInstance oxCharge(VfxCue cue) {
		return VfxInstance.of(SIC_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x0F000003L);
			int charge = Math.max(1, cue.intensity());
			context.burst(ParticleTypes.POOF, origin, 4 + charge, 0.40, 0.10, random);
			if (cue.direction().lengthSqr() > 1.0E-8) {
				for (int index = 1; index <= 2 + charge; index++) {
					Vec3 alongPath = origin.add(cue.direction().scale(index * 0.5));
					context.burst(ParticleTypes.POOF, alongPath, 2, 0.10, 0.04, random);
				}
			}
		});
	}

	/** The line runs through a target: the impact burst on the hit body, scaled by impact power. */
	private static VfxInstance oxImpact(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x0F000004L);
			int impact = Math.max(1, cue.intensity());
			context.burst(SHADOW_DARK, target.add(0.0, 0.6, 0.0), 8 + impact, 0.35, 0.18, random);
			context.burst(ParticleTypes.POOF, target.add(0.0, 0.4, 0.0), 5 + impact / 2, 0.30, 0.12, random);
		});
	}

	/** The charge dies against a wall: the dust explosion that sells the stop. */
	private static VfxInstance oxWallHit(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x0F000005L);
			int impact = Math.max(1, cue.intensity());
			context.burst(ParticleTypes.POOF, target.add(0.0, 0.5, 0.0), 10 + impact, 0.55, 0.18, random);
			context.burst(SHADOW_DARK, target, 4 + impact / 2, 0.30, 0.12, random);
		});
	}

	/** Tiger Funeral's summon pool: the shadow opening with a predatory amber edge. */
	private static VfxInstance tigerSummon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x71670001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.12, 0.0), 16, 0.50, 0.15, random);
			context.burst(SHADOW_DARK, origin.add(0.0, 0.3, 0.0), 6, 0.40, 0.12, random);
		});
	}

	/** One combo beat landed: a slash streak; the cue's intensity carries the beat index. */
	private static VfxInstance tigerStrike(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x71670002L);
			int beat = Math.max(1, cue.intensity());
			context.burst(SHADOW_DARK, target.add(0.0, 0.7, 0.0), 8 + beat * 4, 0.35, 0.16, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, target.add(0.0, 0.5, 0.0), 6 + beat * 2, 0.6, 0.02, 0.0, random);
		});
	}

	/** A combo beat missed its arc: a thin wisp where the claw passed. */
	private static VfxInstance tigerMiss(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x71670003L);
			context.burst(SHADOW_DARK, target.add(0.0, 0.5, 0.0), 4, 0.20, 0.10, random);
		});
	}

	/** The committed sequence ends: a slow dust spread plus a shadow accent at the paws. */
	private static VfxInstance tigerRecover(VfxCue cue) {
		return VfxInstance.of(POUNCE_DURATION_TICKS + 4, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x71670004L);
			context.burst(SHADOW_DARK, target.add(0.0, 0.3, 0.0), 10, 0.45, 0.06, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, target.add(0.0, 0.15, 0.0), 8, 0.8, 0.0, -0.04, random);
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
