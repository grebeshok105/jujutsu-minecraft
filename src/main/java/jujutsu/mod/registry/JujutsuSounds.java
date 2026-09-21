package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import jujutsu.mod.JujutsuMod;

public final class JujutsuSounds {
	public static final SoundEvent HAIRPIN_PREP = create("hairpin.prep");
	public static final SoundEvent HAIRPIN_HAMMER_SNAP = create("hairpin.hammer_snap");
	public static final SoundEvent HAIRPIN_NAIL_IGNITE = create("hairpin.nail_ignite");
	public static final SoundEvent HAIRPIN_BLOOM = create("hairpin.bloom");
	public static final SoundEvent HAIRPIN_AFTERGLOW = create("hairpin.afterglow");
	public static final SoundEvent PROJECTJJK_SNAP = create("projectjjk.snap");
	public static final SoundEvent PROJECTJJK_SPELL_SHOT = create("projectjjk.spell_shot");
	public static final SoundEvent PROJECTJJK_WHOOSH_HIT = create("projectjjk.whoosh_hit");
	public static final SoundEvent PROJECTJJK_CINEMATIC_WHOOSH = create("projectjjk.cinematic_whoosh");
	public static final SoundEvent PROJECTJJK_EXPLODE = create("projectjjk.explode");
	public static final SoundEvent PROJECTJJK_IMPLODE = create("projectjjk.implode");
	public static final SoundEvent PROJECTJJK_DEEP_EXPLOSION = create("projectjjk.deep_explosion");
	public static final SoundEvent PROJECTJJK_BLACK_FLASH_IMPACT = create("projectjjk.black_flash_impact");
	public static final SoundEvent PROJECTJJK_BLACK_FLASH_IMPACT_2 = create("projectjjk.black_flash_impact2");
	public static final SoundEvent PROJECTJJK_GOO_FOLEY = create("projectjjk.goo_foley");
	public static final SoundEvent PROJECTJJK_CHIME = create("projectjjk.chime");
	public static final SoundEvent PROJECTJJK_MAGIC = create("projectjjk.magic");
	public static final SoundEvent PROJECTJJK_SIZZLE = create("projectjjk.sizzle");
	public static final SoundEvent PROJECTJJK_CLAP = create("projectjjk.clap");
	public static final SoundEvent PROJECTJJK_LONG_WHOOSH = create("projectjjk.long_whoosh");
	public static final SoundEvent PROJECTJJK_WHOOSH_VORTEX = create("projectjjk.whoosh_vortex");
	/** Low body-impact report. Two variants, so a rapid exchange does not sound like one looped sample. */
	public static final SoundEvent PROJECTJJK_AEC_BOOM = create("projectjjk.aec_boom");
	/** Mega Nail: 1.3 s synthesized power build-up, played once at charge start (see tools/synth_mega_sounds.py). */
	public static final SoundEvent NOBARA_MEGA_CHARGE_RISER = create("nobara.mega_charge_riser");
	/** Mega Nail: jet-ignition blast at launch. */
	public static final SoundEvent NOBARA_MEGA_LAUNCH_BLAST = create("nobara.mega_launch_blast");
	/** Dire Wolf (Divine Dogs visual): ambient panting, 4 variants. */
	public static final SoundEvent MEGUMI_DOG_AMBIENT = create("megumi.dog_ambient");
	/** Dire Wolf (Divine Dogs visual): growl. */
	public static final SoundEvent MEGUMI_DOG_GROWL = create("megumi.dog_growl");
	// Shikigami quick selector (issue #109): four tactile UI roles, synthesized by
	// tools/synth_selector_sounds.py. Deliberately short (45-120 ms) so repeated combat use
	// never queues: the strip speaks open / hover / confirm / reject and nothing else.
	/** Selector strip slides in. */
	public static final SoundEvent MEGUMI_SELECTOR_OPEN = create("megumi.selector_open");
	/** Cursor crosses into another slot (fires often -- quietest row, pitched with jitter). */
	public static final SoundEvent MEGUMI_SELECTOR_HOVER = create("megumi.selector_hover");
	/** A shikigami became the active one. */
	public static final SoundEvent MEGUMI_SELECTOR_SELECT = create("megumi.selector_select");
	/** The clicked entry is unavailable and the click was refused. */
	public static final SoundEvent MEGUMI_SELECTOR_REJECT = create("megumi.selector_reject");
	// Cursed spirits: 34 channels, one row per frozen variant/channel pair (Block 1 ships the
	// .ogg files and sounds.json keys these constants point at). GULBER and GUZZLER have no
	// scream — there is deliberately no CURSED_GULBER_SCREAM / CURSED_GUZZLER_SCREAM row.
	public static final SoundEvent CURSED_PROWLER_AMBIENT = create("cursed.prowler_ambient");
	public static final SoundEvent CURSED_PROWLER_HURT = create("cursed.prowler_hurt");
	public static final SoundEvent CURSED_PROWLER_DEATH = create("cursed.prowler_death");
	public static final SoundEvent CURSED_PROWLER_SCREAM = create("cursed.prowler_scream");
	public static final SoundEvent CURSED_FLOATING_CURSE_AMBIENT = create("cursed.floating_curse_ambient");
	public static final SoundEvent CURSED_FLOATING_CURSE_HURT = create("cursed.floating_curse_hurt");
	public static final SoundEvent CURSED_FLOATING_CURSE_DEATH = create("cursed.floating_curse_death");
	public static final SoundEvent CURSED_FLOATING_CURSE_SCREAM = create("cursed.floating_curse_scream");
	public static final SoundEvent CURSED_GULBER_AMBIENT = create("cursed.gulber_ambient");
	public static final SoundEvent CURSED_GULBER_HURT = create("cursed.gulber_hurt");
	public static final SoundEvent CURSED_GULBER_DEATH = create("cursed.gulber_death");
	public static final SoundEvent CURSED_KELVIN_AMBIENT = create("cursed.kelvin_ambient");
	public static final SoundEvent CURSED_KELVIN_HURT = create("cursed.kelvin_hurt");
	public static final SoundEvent CURSED_KELVIN_DEATH = create("cursed.kelvin_death");
	public static final SoundEvent CURSED_KELVIN_SCREAM = create("cursed.kelvin_scream");
	public static final SoundEvent CURSED_BUTCHER_AMBIENT = create("cursed.butcher_ambient");
	public static final SoundEvent CURSED_BUTCHER_HURT = create("cursed.butcher_hurt");
	public static final SoundEvent CURSED_BUTCHER_DEATH = create("cursed.butcher_death");
	public static final SoundEvent CURSED_BUTCHER_SCREAM = create("cursed.butcher_scream");
	public static final SoundEvent CURSED_GUZZLER_AMBIENT = create("cursed.guzzler_ambient");
	public static final SoundEvent CURSED_GUZZLER_HURT = create("cursed.guzzler_hurt");
	public static final SoundEvent CURSED_GUZZLER_DEATH = create("cursed.guzzler_death");
	public static final SoundEvent CURSED_BLUD_AMBIENT = create("cursed.blud_ambient");
	public static final SoundEvent CURSED_BLUD_HURT = create("cursed.blud_hurt");
	public static final SoundEvent CURSED_BLUD_DEATH = create("cursed.blud_death");
	public static final SoundEvent CURSED_BLUD_SCREAM = create("cursed.blud_scream");
	public static final SoundEvent CURSED_WALKING_BED_AMBIENT = create("cursed.walking_bed_ambient");
	public static final SoundEvent CURSED_WALKING_BED_HURT = create("cursed.walking_bed_hurt");
	public static final SoundEvent CURSED_WALKING_BED_DEATH = create("cursed.walking_bed_death");
	public static final SoundEvent CURSED_WALKING_BED_SCREAM = create("cursed.walking_bed_scream");
	public static final SoundEvent CURSED_WISTIVER_AMBIENT = create("cursed.wistiver_ambient");
	public static final SoundEvent CURSED_WISTIVER_HURT = create("cursed.wistiver_hurt");
	public static final SoundEvent CURSED_WISTIVER_DEATH = create("cursed.wistiver_death");
	public static final SoundEvent CURSED_WISTIVER_SCREAM = create("cursed.wistiver_scream");
	public static final SoundEvent INCIDENT_DRONE = create("incident_drone");
	public static final SoundEvent SEAL_CRACK = create("seal_crack");
	public static final SoundEvent SEAL_BREAK = create("seal_break");
	public static final SoundEvent SECONDARY_BIRTH = create("secondary_birth");
	// Black hole visual experiment (debug-only): PRELUDE and IMPULSE are synthesized by
	// tools/synth_blackhole_sounds.py; DRONE is the licensed freesound loop
	// 568574__ericnorcross81__void-of-a-black-hole-fictional (mono, loop-wrapped, bass-boosted).
	// All layers are strictly positional at the hole's centre.
	public static final SoundEvent BLACK_HOLE_PRELUDE = create("blackhole.prelude");
	public static final SoundEvent BLACK_HOLE_DRONE = create("blackhole.drone");
	public static final SoundEvent BLACK_HOLE_IMPULSE = create("blackhole.impulse");

	public static void register() {
		register("hairpin.prep", HAIRPIN_PREP);
		register("hairpin.hammer_snap", HAIRPIN_HAMMER_SNAP);
		register("hairpin.nail_ignite", HAIRPIN_NAIL_IGNITE);
		register("hairpin.bloom", HAIRPIN_BLOOM);
		register("hairpin.afterglow", HAIRPIN_AFTERGLOW);
		register("projectjjk.snap", PROJECTJJK_SNAP);
		register("projectjjk.spell_shot", PROJECTJJK_SPELL_SHOT);
		register("projectjjk.whoosh_hit", PROJECTJJK_WHOOSH_HIT);
		register("projectjjk.cinematic_whoosh", PROJECTJJK_CINEMATIC_WHOOSH);
		register("projectjjk.explode", PROJECTJJK_EXPLODE);
		register("projectjjk.implode", PROJECTJJK_IMPLODE);
		register("projectjjk.deep_explosion", PROJECTJJK_DEEP_EXPLOSION);
		register("projectjjk.black_flash_impact", PROJECTJJK_BLACK_FLASH_IMPACT);
		register("projectjjk.black_flash_impact2", PROJECTJJK_BLACK_FLASH_IMPACT_2);
		register("projectjjk.goo_foley", PROJECTJJK_GOO_FOLEY);
		register("projectjjk.chime", PROJECTJJK_CHIME);
		register("projectjjk.magic", PROJECTJJK_MAGIC);
		register("projectjjk.sizzle", PROJECTJJK_SIZZLE);
		register("projectjjk.clap", PROJECTJJK_CLAP);
		register("projectjjk.long_whoosh", PROJECTJJK_LONG_WHOOSH);
		register("projectjjk.whoosh_vortex", PROJECTJJK_WHOOSH_VORTEX);
		register("projectjjk.aec_boom", PROJECTJJK_AEC_BOOM);
		register("nobara.mega_charge_riser", NOBARA_MEGA_CHARGE_RISER);
		register("nobara.mega_launch_blast", NOBARA_MEGA_LAUNCH_BLAST);
		register("megumi.dog_ambient", MEGUMI_DOG_AMBIENT);
		register("megumi.dog_growl", MEGUMI_DOG_GROWL);
		register("megumi.selector_open", MEGUMI_SELECTOR_OPEN);
		register("megumi.selector_hover", MEGUMI_SELECTOR_HOVER);
		register("megumi.selector_select", MEGUMI_SELECTOR_SELECT);
		register("megumi.selector_reject", MEGUMI_SELECTOR_REJECT);
		register("cursed.prowler_ambient", CURSED_PROWLER_AMBIENT);
		register("cursed.prowler_hurt", CURSED_PROWLER_HURT);
		register("cursed.prowler_death", CURSED_PROWLER_DEATH);
		register("cursed.prowler_scream", CURSED_PROWLER_SCREAM);
		register("cursed.floating_curse_ambient", CURSED_FLOATING_CURSE_AMBIENT);
		register("cursed.floating_curse_hurt", CURSED_FLOATING_CURSE_HURT);
		register("cursed.floating_curse_death", CURSED_FLOATING_CURSE_DEATH);
		register("cursed.floating_curse_scream", CURSED_FLOATING_CURSE_SCREAM);
		register("cursed.gulber_ambient", CURSED_GULBER_AMBIENT);
		register("cursed.gulber_hurt", CURSED_GULBER_HURT);
		register("cursed.gulber_death", CURSED_GULBER_DEATH);
		register("cursed.kelvin_ambient", CURSED_KELVIN_AMBIENT);
		register("cursed.kelvin_hurt", CURSED_KELVIN_HURT);
		register("cursed.kelvin_death", CURSED_KELVIN_DEATH);
		register("cursed.kelvin_scream", CURSED_KELVIN_SCREAM);
		register("cursed.butcher_ambient", CURSED_BUTCHER_AMBIENT);
		register("cursed.butcher_hurt", CURSED_BUTCHER_HURT);
		register("cursed.butcher_death", CURSED_BUTCHER_DEATH);
		register("cursed.butcher_scream", CURSED_BUTCHER_SCREAM);
		register("cursed.guzzler_ambient", CURSED_GUZZLER_AMBIENT);
		register("cursed.guzzler_hurt", CURSED_GUZZLER_HURT);
		register("cursed.guzzler_death", CURSED_GUZZLER_DEATH);
		register("cursed.blud_ambient", CURSED_BLUD_AMBIENT);
		register("cursed.blud_hurt", CURSED_BLUD_HURT);
		register("cursed.blud_death", CURSED_BLUD_DEATH);
		register("cursed.blud_scream", CURSED_BLUD_SCREAM);
		register("cursed.walking_bed_ambient", CURSED_WALKING_BED_AMBIENT);
		register("cursed.walking_bed_hurt", CURSED_WALKING_BED_HURT);
		register("cursed.walking_bed_death", CURSED_WALKING_BED_DEATH);
		register("cursed.walking_bed_scream", CURSED_WALKING_BED_SCREAM);
		register("cursed.wistiver_ambient", CURSED_WISTIVER_AMBIENT);
		register("cursed.wistiver_hurt", CURSED_WISTIVER_HURT);
		register("cursed.wistiver_death", CURSED_WISTIVER_DEATH);
		register("cursed.wistiver_scream", CURSED_WISTIVER_SCREAM);
		register("incident_drone", INCIDENT_DRONE);
		register("seal_crack", SEAL_CRACK);
		register("seal_break", SEAL_BREAK);
		register("secondary_birth", SECONDARY_BIRTH);
		register("blackhole.prelude", BLACK_HOLE_PRELUDE);
		register("blackhole.drone", BLACK_HOLE_DRONE);
		register("blackhole.impulse", BLACK_HOLE_IMPULSE);
	}

	private static SoundEvent create(String path) {
		return SoundEvent.createVariableRangeEvent(JujutsuMod.id(path));
	}

	private static void register(String path, SoundEvent soundEvent) {
		Registry.register(BuiltInRegistries.SOUND_EVENT, JujutsuMod.id(path), soundEvent);
	}
}
