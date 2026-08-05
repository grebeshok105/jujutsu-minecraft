package jujutsu.mod.client.vfx.todo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.character.todo.TodoPairSelectionClientState;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxPalette;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Todo's VFX Core recipes. No effect owns separate callbacks or packet receivers. */
public final class TodoVfxRecipes {
	private static final DustParticleOptions TODO_VIOLET = new DustParticleOptions(
			packRgb(VfxPalette.TODO_VIOLET_R, VfxPalette.TODO_VIOLET_G, VfxPalette.TODO_VIOLET_B), 1.05f);
	private static final DustParticleOptions TODO_EDGE = new DustParticleOptions(
			packRgb(VfxPalette.TODO_EDGE_R, VfxPalette.TODO_EDGE_G, VfxPalette.TODO_EDGE_B), 0.72f);
	/** Plain rock gray: the stone reads as a mundane object, never as another violet cast. */
	private static final DustParticleOptions STONE_GRAY = new DustParticleOptions(
			packRgb(138, 143, 148), 0.9f);
	private static final DustParticleOptions STONE_DARK = new DustParticleOptions(
			packRgb(88, 92, 97), 0.65f);

	private TodoVfxRecipes() {}

	public static final double BOOGIE_WOOGIE_PRESENTATION_RADIUS = 56.0;
	public static final int BOOGIE_WOOGIE_DURATION_TICKS = 15;
	public static final int SWAP_ENDPOINT_DURATION_TICKS = 8;
	/** Four ticks: long enough to register, short enough that the residue does not sit inside the arriving body. */
	private static final int AFTERIMAGE_TICKS = 4;
	private static final int ARRIVAL_TICKS = 6;
	public static final int MOMENTUM_STRIKE_DURATION_TICKS = 8;
	public static final int FEINT_TELL_DURATION_TICKS = 6;
	public static final int PAIR_MARK_DURATION_TICKS = 8;
	/** Long enough for the flick to read, short enough that it never competes with the swap clap. */
	public static final int STONE_THROW_DURATION_TICKS = 6;
	/** One compact poof; the stone leaves no lingering residue by design. */
	public static final int STONE_VANISH_DURATION_TICKS = 8;
	/** The stream is a single snapshot per edge; ten ticks covers the three edges of a cast. */
	public static final int TRIPLE_SWAP_DURATION_TICKS = 10;
	/** Six ticks of the world stepping back, ending as the arrival visuals do. */
	private static final int DUCK_TICKS = 6;
	/** A body is standing on its own arrival point at the instant the cue is authored. 1.5 blocks. */
	private static final double LOCAL_ARRIVAL_RADIUS_SQR = 2.25;

	public static void register() {
		VfxDirector.register(TodoVfxIds.BOOGIE_WOOGIE, TodoVfxRecipes::boogieWoogie);
		VfxDirector.register(TodoVfxIds.SWAP_ENDPOINT, TodoVfxRecipes::swapEndpoint);
		VfxDirector.register(TodoVfxIds.SWAP_AFTERIMAGE, TodoVfxRecipes::swapAfterimage);
		VfxDirector.register(TodoVfxIds.SWAP_ARRIVAL, TodoVfxRecipes::swapArrival);
		VfxDirector.register(TodoVfxIds.MOMENTUM_STRIKE, TodoVfxRecipes::momentumStrike);
		VfxDirector.register(TodoVfxIds.FEINT_TELL, TodoVfxRecipes::feintTell);
		VfxDirector.register(TodoVfxIds.PAIR_MARK, TodoVfxRecipes::pairMark);
		VfxDirector.register(TodoVfxIds.STONE_THROW, TodoVfxRecipes::stoneThrow);
		VfxDirector.register(TodoVfxIds.STONE_VANISH, TodoVfxRecipes::stoneVanish);
		VfxDirector.register(TodoVfxIds.TRIPLE_SWAP, TodoVfxRecipes::tripleSwap);
	}

	/**
	 * The clap itself, and nothing about the swap. <b>The feint sends this same cue</b>, so anything added
	 * here is something a feint does too — which is exactly right for the clap and exactly wrong for
	 * everything that only a completed swap earns.
	 */
	private static VfxInstance boogieWoogie(VfxCue cue) {
		// ~15 ticks covers Nobara-style first-person snap phases + third-person GeckoLib clap.
		return VfxInstance.of(BOOGIE_WOOGIE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				float proximity = context.proximity(cue, BOOGIE_WOOGIE_PRESENTATION_RADIUS);
				// A snap, not a launch: the old triggerLaunch dipped the FOV by eight degrees, which reads
				// as being thrown forward rather than as a body being displaced beside you.
				context.camera().triggerSwapSnap(1, proximity, initialAgeTicks);
				context.hud().triggerFlash(80, Math.round(62.0f * proximity), initialAgeTicks);
				// Third-person GeckoLib clap (both arms) via replaced-entity animatable.
				TodoAnimationHooks.triggerBoogieWoogie(cue);
				if (isLocalAnchor(context, cue)) {
					// FP clap always starts at progress 0 (ignore late-cue age) so every cast matches.
					context.firstPerson().triggerClap(0.0f);
				}
			}
		});
	}

	private static VfxInstance swapEndpoint(VfxCue cue) {
		return VfxInstance.of(SWAP_ENDPOINT_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 endpoint = context.resolveOrigin(cue);
				emitFlash(context, endpoint, random(cue, 0xB001E13L));
				// Only the leading endpoint carries the pair delta. The trailing one used to claim a world
				// flash slot to draw nothing, because the ribbon renderer no-ops on a zero delta.
				if (cue.anchorOffset().lengthSqr() > 1.0E-4) {
					context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.BOOGIE_WOOGIE, SWAP_ENDPOINT_DURATION_TICKS);
				}
			}
		});
	}

	/** One body's residue where it used to be. Emitted only by a completed swap, never by the feint. */
	private static VfxInstance swapAfterimage(VfxCue cue) {
		return VfxInstance.of(AFTERIMAGE_TICKS, (context, initialAgeTicks) -> {
			// Outside the opening beat: the world channel seeks by game time, so a late cue still shows
			// the tail it should be showing rather than nothing at all.
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.SWAP_AFTERIMAGE, AFTERIMAGE_TICKS);
		});
	}

	/**
	 * One body's landing. This is where the duck and the participant's camera kick live, because this cue
	 * is emitted only when a swap actually completed — the clap cue is shared with the feint, and a feint
	 * that silenced the world or kicked a camera would announce itself.
	 */
	private static VfxInstance swapArrival(VfxCue cue) {
		return VfxInstance.of(ARRIVAL_TICKS, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.SWAP_ARRIVAL, ARRIVAL_TICKS);
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 arrival = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xA221AEL);
			// Inward, against the departure's outward throw, so the two ends of a swap never read alike.
			context.ring(TODO_EDGE, arrival.add(0.0, 0.12, 0.0), 9, 0.55, 0.0, -0.035, random);
			context.burst(TODO_VIOLET, arrival.add(0.0, 0.6, 0.0), 6, 0.22, 0.04, random);
			context.sound().duck(context.client(), DUCK_TICKS, initialAgeTicks);
			if (isLocalArrival(context, cue)) {
				// A displacement jolt belongs to the body that was displaced, not to everyone watching it.
				context.camera().triggerSwapSnap(2, 1.0f, initialAgeTicks);
			}
		});
	}

	/**
	 * The hit a landed swap bought. Deliberately one beat and no camera work: it can land on the same tick
	 * as a Black Flash, whose cue should win that frame outright.
	 */
	private static VfxInstance momentumStrike(VfxCue cue) {
		return VfxInstance.of(MOMENTUM_STRIKE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 impact = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0x30DE12L);
				context.ring(TODO_VIOLET, impact, 10, 0.42, 0.0, 0.09, random);
				context.burst(TODO_EDGE, impact, 7, 0.24, 0.22, random);
			}
		});
	}

	/**
	 * Caster-only feint confirmation: a thin wisp of dust at chest height and nothing else. No HUD
	 * flash, no camera kick, no sound — every one of those would be perceivable by the observer the
	 * feint exists to deceive. The cue is sent to a single player, never broadcast.
	 */
	private static VfxInstance feintTell(VfxCue cue) {
		return VfxInstance.of(FEINT_TELL_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 chest = context.resolveOrigin(cue).add(0.0, 1.1, 0.0);
				context.ring(TODO_EDGE, chest, 6, 0.16, 0.0, 0.008, random(cue, 0xFE117L));
			}
		});
	}

	/**
	 * Caster-only mark confirmation on the first pair-swap participant. A single beat, not a marker that
	 * follows the body for the whole selection: a transient cue is started once, and VFX Core keeps
	 * anything that must track a live entity on that entity's own renderer. The actionbar line names who
	 * was marked, which is what the caster actually needs to remember.
	 *
	 * <p>The server re-emits this cue as a silent pulse (intensity 0) every {@code PAIR_MARK_PULSE_TICKS}
	 * while the selection lives; the pulse feeds the HUD chip's hold without re-sounding the mark and
	 * without extending the countdown.
	 */
	private static VfxInstance pairMark(VfxCue cue) {
		return VfxInstance.of(PAIR_MARK_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (cue.intensity() == 0) {
				TodoPairSelectionClientState.pulse(cue.anchorEntityId(), cue.startGameTime());
				return;
			}
			TodoPairSelectionClientState.mark(cue.anchorEntityId(), cue.startGameTime());
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 marked = context.resolveOrigin(cue).add(0.0, 1.0, 0.0);
			RandomSource random = random(cue, 0x9A12CAFEL);
			context.ring(TODO_VIOLET, marked, 12, 0.55, 0.0, 0.02, random);
			context.burst(TODO_EDGE, marked, 6, 0.22, 0.05, random);
		});
	}

	/**
	 * The stone leaving Todo's hand. A short hand-flick of gray dust along the throw line and the
	 * vanilla throw whoosh — deliberately lighter than any clap, so a throw never reads as a swap.
	 * The stone itself is the persistent visual; this cue only covers the departure beat.
	 */
	private static VfxInstance stoneThrow(VfxCue cue) {
		return VfxInstance.of(STONE_THROW_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x57011EEL);
			context.playNoFalloff(SoundEvents.SNOWBALL_THROW, 0.5f, 1.12f, origin, random);
			Vec3 direction = cue.direction();
			ClientLevel level = context.client().level;
			if (direction.lengthSqr() > 1.0E-8 && level != null) {
				int count = context.quality().scaledCount(5);
				for (int index = 0; index < count; index++) {
					Vec3 at = origin.add(direction.scale(0.15 + random.nextDouble() * 0.5))
							.add((random.nextDouble() - 0.5) * 0.24, (random.nextDouble() - 0.5) * 0.24,
									(random.nextDouble() - 0.5) * 0.24);
					level.addParticle(STONE_GRAY, at.x, at.y, at.z,
							direction.x * 0.05 + (random.nextDouble() - 0.5) * 0.04,
							direction.y * 0.05 + (random.nextDouble() - 0.5) * 0.04,
							direction.z * 0.05 + (random.nextDouble() - 0.5) * 0.04);
				}
			}
			context.burst(TODO_EDGE, origin, 3, 0.10, 0.05, random);
		});
	}

	/**
	 * The stone ending — lifetime expiry, terminal block collision, or state cleanup. World-fixed at
	 * the stone's last position: one compact gray poof and a soft stone tap, no lingering residue.
	 */
	private static VfxInstance stoneVanish(VfxCue cue) {
		return VfxInstance.of(STONE_VANISH_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5711E3EL);
			context.playNoFalloff(SoundEvents.STONE_PLACE, 0.45f, 0.85f, origin, random);
			context.burst(STONE_GRAY, origin, 8, 0.24, 0.10, random);
			context.ring(STONE_DARK, origin.add(0.0, 0.06, 0.0), 5, 0.16, 0.0, 0.015, random);
		});
	}

	/**
	 * One edge of the triple cyclic swap. Emitted three times per cast — Todo→A, A→T, T→Todo — each
	 * world-fixed at the edge's start with {@code direction} along the edge and {@code anchorOffset.x}
	 * carrying the edge length. A violet particle stream runs to a brighter head at the far end, so the
	 * direction of each edge (and therefore the whole A→B→C→A flow) reads in-world; the gray puff at
	 * the start ties the edge back to the stone-free, hand-cast nature of the triple.
	 */
	private static VfxInstance tripleSwap(VfxCue cue) {
		return VfxInstance.of(TRIPLE_SWAP_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 from = cue.origin();
			Vec3 direction = cue.direction();
			double edgeLength = cue.anchorOffset().x;
			if (direction.lengthSqr() < 1.0E-8 || edgeLength <= 0.0) {
				return;
			}
			RandomSource random = random(cue, 0x331B5EEL);
			ClientLevel level = context.client().level;
			if (level == null) {
				return;
			}
			int count = context.quality().scaledCount(10);
			for (int index = 0; index < count; index++) {
				double t = (index + 1.0) / count;
				Vec3 at = from.add(direction.scale(edgeLength * t))
						.add(0.0, 0.28, 0.0)
						.add((random.nextDouble() - 0.5) * 0.20, (random.nextDouble() - 0.5) * 0.16,
								(random.nextDouble() - 0.5) * 0.20);
				level.addParticle(TODO_VIOLET, at.x, at.y, at.z,
						(random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.04,
						(random.nextDouble() - 0.5) * 0.06);
			}
			context.burst(STONE_GRAY, from.add(0.0, 0.3, 0.0), 5, 0.12, 0.06, random);
			Vec3 head = from.add(direction.scale(edgeLength)).add(0.0, 0.3, 0.0);
			context.ring(TODO_EDGE, head, 8, 0.42, 0.0, 0.035, random);
			context.burst(TODO_EDGE, head, 5, 0.14, 0.07, random);
		});
	}

	private static void emitFlash(VfxContext context, Vec3 origin, RandomSource random) {
		context.burst(TODO_VIOLET, origin, 12, 0.28, 0.16, random);
		context.burst(TODO_EDGE, origin, 8, 0.18, 0.10, random);
		context.ring(TODO_EDGE, origin, 10, 0.34, 0.06, 0.045, random);
	}

	/**
	 * Whether the local player is one of the bodies that landed here. Read from the position rather than
	 * from an anchor id, so the arrival cue can keep {@code NO_ANCHOR} and its offset stays free to carry
	 * the speed and dimensions the renderer needs.
	 */
	private static boolean isLocalArrival(VfxContext context, VfxCue cue) {
		return context.client().player != null
				&& context.client().player.position().distanceToSqr(cue.origin()) < LOCAL_ARRIVAL_RADIUS_SQR;
	}

	private static boolean isLocalAnchor(VfxContext context, VfxCue cue) {
		return context.client().player != null
				&& cue.anchorEntityId() != VfxCue.NO_ANCHOR
				&& cue.anchorEntityId() == context.client().player.getId();
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}

	private static int packRgb(int red, int green, int blue) {
		return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}
}
