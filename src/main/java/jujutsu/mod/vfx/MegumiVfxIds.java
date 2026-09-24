package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/** Typed VFX ids owned by Megumi's slice: the Divine Dogs and the shadow kit. */
public final class MegumiVfxIds {
	public static final ResourceLocation DOGS_SUMMON_BODY = JujutsuMod.id("megumi/dogs_summon_body");
	public static final ResourceLocation DOGS_SUMMON = JujutsuMod.id("megumi/dogs_summon");
	public static final ResourceLocation DOGS_RECALL = JujutsuMod.id("megumi/dogs_recall");
	public static final ResourceLocation DOGS_SIC = JujutsuMod.id("megumi/dogs_sic");
	public static final ResourceLocation DOGS_POUNCE = JujutsuMod.id("megumi/dogs_pounce");
	/** Trap cast lands: the pool unfurls under the target. */
	public static final ResourceLocation SHADOW_TRAP_OPEN = JujutsuMod.id("megumi/shadow_trap_open");
	/** Persistent pool + inward ring; re-emitted by the server pulse, never re-ticked by the client. */
	public static final ResourceLocation SHADOW_TRAP_ZONE = JujutsuMod.id("megumi/shadow_trap_zone");
	/** Pull-down motes on one gripped body. */
	public static final ResourceLocation SHADOW_TRAP_GRIP = JujutsuMod.id("megumi/shadow_trap_grip");
	/** The pool collapses on expiry or teardown. */
	public static final ResourceLocation SHADOW_TRAP_CLOSE = JujutsuMod.id("megumi/shadow_trap_close");
	/** Shadow move begins: sink pool, dive animation trigger, first-person beat for the caster. */
	public static final ResourceLocation SHADOW_DIVE = JujutsuMod.id("megumi/shadow_dive");
	/** Faint moving ripple over a submerged Megumi; doubles as the client-side hide signal. */
	public static final ResourceLocation SHADOW_RIPPLE = JujutsuMod.id("megumi/shadow_ripple");
	/** Shadow move ends: exit pool, upward burst, emerge animation trigger. */
	public static final ResourceLocation SHADOW_EMERGE = JujutsuMod.id("megumi/shadow_emerge");
	/** Shadow Drop cast lands: the zone unfurls above the target. */
	public static final ResourceLocation DROP_ZONE_OPEN = JujutsuMod.id("megumi/drop_zone_open");
	/** Hovering zone disc; re-emitted by the server pulse, never re-ticked by the client. */
	public static final ResourceLocation DROP_ZONE = JujutsuMod.id("megumi/drop_zone");
	/** The zone collapses: the block is on its way down. */
	public static final ResourceLocation DROP_ZONE_CLOSE = JujutsuMod.id("megumi/drop_zone_close");

	// --- shikigami slice (Nue / Toad / Rabbit Escape / Max Elephant) ---
	/** Nue's summon pool. */
	public static final ResourceLocation NUE_SUMMON = JujutsuMod.id("megumi/nue_summon");
	/** Nue commits to a dive: a trailing streak at the body. */
	public static final ResourceLocation NUE_DIVE = JujutsuMod.id("megumi/nue_dive");
	/** The electric discharge on impact. */
	public static final ResourceLocation NUE_SHOCK = JujutsuMod.id("megumi/nue_shock");
	/** Generic sic command marker at the ordered target. */
	public static final ResourceLocation SHIKIGAMI_SIC = JujutsuMod.id("megumi/shikigami_sic");
	/** Generic recall sweep when any shikigami pack is dismissed. */
	public static final ResourceLocation SHIKIGAMI_RECALL = JujutsuMod.id("megumi/shikigami_recall");
	/** Toad's summon splash. */
	public static final ResourceLocation TOAD_SUMMON = JujutsuMod.id("megumi/toad_summon");
	/** The tongue windup and the grab flash. */
	public static final ResourceLocation TOAD_TONGUE = JujutsuMod.id("megumi/toad_tongue");
	/** Max Elephant's summon pool. */
	public static final ResourceLocation ELEPHANT_SUMMON = JujutsuMod.id("megumi/elephant_summon");
	/** Jet start and the per-pulse emission at the trunk. */
	public static final ResourceLocation ELEPHANT_JET = JujutsuMod.id("megumi/elephant_jet");
	/** Rabbit Escape's summon pool. */
	public static final ResourceLocation RABBITS_SUMMON = JujutsuMod.id("megumi/rabbits_summon");
	/** Upkeep pop, bump puff and the expiry burst of the rabbit swarm. */
	public static final ResourceLocation RABBITS_POP = JujutsuMod.id("megumi/rabbits_pop");
	/**
	 * Nue's partial manifestation snaps open (issue #108): one unfold beat at the owner. Emitted by
	 * {@code MegumiNueWings.playUnfoldCue}; the sustained wings are vanilla's fall-flying pose, so
	 * there is no per-tick re-emission and no close cue — landing simply stops the glide.
	 */
	public static final ResourceLocation NUE_PARTIAL_WINGS = JujutsuMod.id("megumi/nue_partial_wings");

	// --- shikigami slice (Great Serpent / Round Deer / Piercing Ox / Tiger Funeral) ---
	/** Great Serpent's summon pool. */
	public static final ResourceLocation SERPENT_SUMMON = JujutsuMod.id("megumi/serpent_summon");
	/** The shadow rise as the serpent emerges beside its mark. */
	public static final ResourceLocation SERPENT_AMBUSH = JujutsuMod.id("megumi/serpent_ambush");
	/** Bind accent while a victim is held. */
	public static final ResourceLocation SERPENT_BIND = JujutsuMod.id("megumi/serpent_bind");
	/** Release beat when the hold lets go. */
	public static final ResourceLocation SERPENT_RELEASE = JujutsuMod.id("megumi/serpent_release");
	/** Round Deer's summon pool. */
	public static final ResourceLocation DEER_SUMMON = JujutsuMod.id("megumi/deer_summon");
	/** Positive-energy pulse: the heal beat. */
	public static final ResourceLocation DEER_PULSE = JujutsuMod.id("megumi/deer_pulse");
	/** Cleanse burst when a debuff is lifted. */
	public static final ResourceLocation DEER_CLEANSE = JujutsuMod.id("megumi/deer_cleanse");
	/** Piercing Ox's summon pool. */
	public static final ResourceLocation OX_SUMMON = JujutsuMod.id("megumi/ox_summon");
	/** Horn-lowering windup before the committed charge. */
	public static final ResourceLocation OX_WINDUP = JujutsuMod.id("megumi/ox_windup");
	/** Ground trail + growing intensity while the charge runs. */
	public static final ResourceLocation OX_CHARGE = JujutsuMod.id("megumi/ox_charge");
	/** Entity impact burst, scaled by accumulated distance. */
	public static final ResourceLocation OX_IMPACT = JujutsuMod.id("megumi/ox_impact");
	/** Wall/abort impact when the charge dies on world collision. */
	public static final ResourceLocation OX_ABORT = JujutsuMod.id("megumi/ox_abort");
	/** Tiger Funeral's summon pool. */
	public static final ResourceLocation TIGER_SUMMON = JujutsuMod.id("megumi/tiger_summon");
	/** Per-strike accent on combo hits. */
	public static final ResourceLocation TIGER_STRIKE = JujutsuMod.id("megumi/tiger_strike");
	/** Finisher burst: the heaviest beat of the combo. */
	public static final ResourceLocation TIGER_FINISHER = JujutsuMod.id("megumi/tiger_finisher");

	public static final Set<ResourceLocation> LIVE = Set.of(
			DOGS_SUMMON_BODY, DOGS_SUMMON, DOGS_RECALL, DOGS_SIC, DOGS_POUNCE,
			SHADOW_TRAP_OPEN, SHADOW_TRAP_ZONE, SHADOW_TRAP_GRIP, SHADOW_TRAP_CLOSE,
			SHADOW_DIVE, SHADOW_RIPPLE, SHADOW_EMERGE,
			DROP_ZONE_OPEN, DROP_ZONE, DROP_ZONE_CLOSE,
			NUE_SUMMON, NUE_DIVE, NUE_SHOCK, NUE_PARTIAL_WINGS, SHIKIGAMI_SIC, SHIKIGAMI_RECALL,
			TOAD_SUMMON, TOAD_TONGUE,
			ELEPHANT_SUMMON, ELEPHANT_JET,
			RABBITS_SUMMON, RABBITS_POP,
			SERPENT_SUMMON, SERPENT_AMBUSH, SERPENT_BIND, SERPENT_RELEASE,
			DEER_SUMMON, DEER_PULSE, DEER_CLEANSE,
			OX_SUMMON, OX_WINDUP, OX_CHARGE, OX_IMPACT, OX_ABORT,
			TIGER_SUMMON, TIGER_STRIKE, TIGER_FINISHER);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	private MegumiVfxIds() {}
}
