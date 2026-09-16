package jujutsu.mod.client.vfx.domain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Locale;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.vfx.DebugVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Debug/dev-only effects register here; gameplay VFX go through vessel {@code *VfxRecipes}.
 *
 * <p>The domain sphere is a rendering prototype with no gameplay owner yet, so it cannot hang off a
 * vessel recipe pack (and must not: {@code VfxCompletenessTest} counts exactly the vessel packs and
 * their live ids). It therefore registers its recipe here, from client startup, and exposes a local
 * debug trigger. When a vessel ever ships a real Domain Expansion, its recipe belongs in that
 * vessel's {@code *VfxRecipes} pack like every other ability VFX — not in this class.
 *
 * <p>Trigger: {@code /jujutsu_debug domain_sphere [radius]} (radius 5..64, default 30).
 */
public final class DomainSphereDebug {
	private static final int DEFAULT_RADIUS = 30;
	private static final int MIN_RADIUS = 5;
	private static final int MAX_RADIUS = 64;
	/**
	 * Client command root. Deliberately NOT {@code jujutsu}: fabric-command-api-v2 intercepts a slash
	 * message before it reaches the server and only forwards it when the parse fails with
	 * {@code dispatcherUnknownCommand}. A client root {@code jujutsu} would therefore swallow every
	 * server-side {@code /jujutsu ...} subcommand (the parse fails on the unmatched subcommand with
	 * {@code dispatcherUnknownArgument}, which is not forwarded) — so the debug tree gets its own root.
	 */
	private static final String COMMAND_ROOT = "jujutsu_debug";
	/** Blocks in front of the eye the sphere is centred on. */
	private static final double LOOK_AHEAD_BLOCKS = 4.0;
	/** Lifetime of the whole effect. The argument is a radius, not a duration — only the default split matters. */
	private static final int TOTAL_TICKS = DomainSphereTiming.defaults(1).totalTicks();

	private DomainSphereDebug() {}

	public static void register() {
		// Force-touch so a broken pipeline surfaces at client startup instead of mid-frame on first trigger.
		DomainSphereRenderer.pipeline();
		VfxDirector.register(DebugVfxIds.DOMAIN_SPHERE, DomainSphereDebug::domainSphere);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal(COMMAND_ROOT)
						.then(ClientCommandManager.literal("domain_sphere")
								.executes(context -> trigger(context.getSource(), DEFAULT_RADIUS))
								.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(MIN_RADIUS, MAX_RADIUS))
										.executes(context -> trigger(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))));
	}

	private static VfxInstance domainSphere(VfxCue cue) {
		double radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, cue.intensity()));
		// The starter ignores initialAgeTicks on purpose: the channel derives the sphere's age from the
		// cue's start time every frame, so a cue arriving late still enters at its true phase.
		return VfxInstance.of(TOTAL_TICKS,
				(context, ignoredInitialAgeTicks) -> context.domainSphere().triggerSphere(cue, DomainSphereTiming.defaults(radius)));
	}

	private static int trigger(FabricClientCommandSource source, int radius) {
		LocalPlayer player = source.getPlayer();
		ClientLevel level = source.getClient().level;
		if (level == null) {
			source.sendError(Component.literal("domain_sphere needs a loaded client level"));
			return 0;
		}
		Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(LOOK_AHEAD_BLOCKS));
		VfxCue cue = VfxCues.worldFixed(
				DebugVfxIds.DOMAIN_SPHERE, origin, radius, level.getGameTime(), level.getRandom().nextLong());
		// Client commands are dispatched on the client game thread, so the cue goes straight in.
		VfxDirector.receive(cue);
		source.sendFeedback(Component.literal("domain_sphere: radius " + radius + " blocks at "
				+ format(origin) + ", " + TOTAL_TICKS + " ticks"));
		return 1;
	}

	private static String format(Vec3 position) {
		return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x, position.y, position.z);
	}
}
