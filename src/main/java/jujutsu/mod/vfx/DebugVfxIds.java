package jujutsu.mod.vfx;

import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire-level ids for debug/dev-only effects.
 *
 * <p>These live in {@code src/main} only because both sides need the constant (the dev-lane MCP tool
 * is a main-source-set sourceSet). Nothing here is vessel content: no production emitter in this
 * source set may reach {@code sendVfxCue}/{@code broadcastVfxCue} with one of these ids — the only
 * triggers are client-side debug entry points (the {@code /jujutsu domain_sphere} client command and
 * the dev-lane MCP tool). That is also why this class stays invisible to
 * {@code VfxCompletenessTest.compiledProductionEmittersCoverEveryLiveId}, which walks exactly the
 * four vessel id classes.
 */
public final class DebugVfxIds {
	public static final ResourceLocation DOMAIN_SPHERE = JujutsuMod.id("domain_sphere");

	private DebugVfxIds() {}
}
