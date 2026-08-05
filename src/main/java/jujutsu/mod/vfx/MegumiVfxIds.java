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

	public static final Set<ResourceLocation> LIVE = Set.of(
			DOGS_SUMMON_BODY, DOGS_SUMMON, DOGS_RECALL, DOGS_SIC, DOGS_POUNCE,
			SHADOW_TRAP_OPEN, SHADOW_TRAP_ZONE, SHADOW_TRAP_GRIP, SHADOW_TRAP_CLOSE,
			SHADOW_DIVE, SHADOW_RIPPLE, SHADOW_EMERGE,
			DROP_ZONE_OPEN, DROP_ZONE, DROP_ZONE_CLOSE);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	private MegumiVfxIds() {}
}
