package jujutsu.mod.client.vfx.blackhole;

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
 * Debug trigger for the black hole visual experiment: {@code /jujutsu_debug black_hole [seconds]}.
 *
 * <p>Shares the {@code jujutsu_debug} client root with the domain-sphere prototype (see
 * {@code DomainSphereDebug} for why the root is not {@code jujutsu}). The hole spawns
 * {@link BlackHoleProfile#SPAWN_DISTANCE} blocks down the look vector, nudged up when the raw point
 * would bury the disk in the ground. One hole at a time: a second call while one is alive is a
 * no-op with a diagnostic, per the spec.
 */
public final class BlackHoleDebug {
	private static final String COMMAND_ROOT = "jujutsu_debug";
	private static final int DEFAULT_SECONDS = 7;
	private static final int MIN_SECONDS = 1;
	private static final int MAX_SECONDS = 60;

	private BlackHoleDebug() {}

	public static void register() {
		BlackHoleRenderer.pipeline();
		VfxDirector.register(DebugVfxIds.BLACK_HOLE, BlackHoleDebug::blackHole);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal(COMMAND_ROOT)
						.then(ClientCommandManager.literal("black_hole")
								.executes(context -> trigger(context.getSource(), DEFAULT_SECONDS))
								.then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(MIN_SECONDS, MAX_SECONDS))
										.executes(context -> trigger(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"))))
								.then(ClientCommandManager.literal("remove")
										.executes(BlackHoleDebug::remove)))));
	}

	private static VfxInstance blackHole(VfxCue cue) {
		int stableTicks = Math.max(BlackHoleTiming.MIN_STABLE_TICKS, cue.intensity());
		BlackHoleTiming timing = new BlackHoleTiming(stableTicks, cue.seed());
		return VfxInstance.of(timing.totalTicks(), (context, ignoredInitialAgeTicks) -> {
			Vec3 diskNormal = diskNormal(context.client(), cue.origin());
			float diskPhase = (cue.seed() & 0xFFFF) / 65536.0f * 6.2831853f;
			boolean started = context.blackHole().tryTrigger(cue, timing, diskNormal, diskPhase);
			if (started) {
				context.blackHole().playPrelude(context.client(), cue.origin());
			}
		});
	}

	/**
	 * Disk normal tilted in the vertical plane containing the eye→hole line: the equatorial band
	 * always reads horizontal on screen (Gargantua), only the top of the disk leans away from the
	 * viewer. A random azimuth slants the band — that reads as a bug, not a design.
	 */
	private static Vec3 diskNormal(net.minecraft.client.Minecraft client, Vec3 center) {
		Vec3 eye = client.player != null ? client.player.getEyePosition() : center;
		Vec3 toHole = center.subtract(eye);
		Vec3 vh = new Vec3(toHole.x, 0.0, toHole.z);
		if (vh.lengthSqr() < 1.0e-6) {
			vh = new Vec3(0.0, 0.0, 1.0);
		}
		vh = vh.normalize();
		double tilt = BlackHoleProfile.DISK_TILT_RADIANS;
		return new Vec3(-vh.x * Math.sin(tilt), Math.cos(tilt), -vh.z * Math.sin(tilt));
	}


	private static int trigger(FabricClientCommandSource source, int seconds) {
		LocalPlayer player = source.getPlayer();
		ClientLevel level = source.getClient().level;
		if (level == null) {
			source.sendError(Component.literal("black_hole needs a loaded client level"));
			return 0;
		}
		if (VfxDirector.blackHole().hasActive()) {
			source.sendFeedback(Component.literal("black_hole: already active — ignoring"));
			return 0;
		}
		Vec3 origin = spawnPoint(player, level);
		int stableTicks = seconds * 20;
		VfxCue cue = VfxCues.worldFixed(
				DebugVfxIds.BLACK_HOLE, origin, stableTicks, level.getGameTime(), level.getRandom().nextLong());
		VfxDirector.receive(cue);
		source.sendFeedback(Component.literal("black_hole: " + seconds + "s stable at " + format(origin)));
		return 1;
	}

	private static int remove(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		if (!VfxDirector.blackHole().hasActive()) {
			source.sendFeedback(Component.literal("black_hole: nothing to remove"));
			return 0;
		}
		VfxDirector.blackHole().forceRemove(source.getClient());
		source.sendFeedback(Component.literal("black_hole: removed"));
		return 1;
	}

	/**
	 * Eye position + look * {@link BlackHoleProfile#SPAWN_DISTANCE}, lifted when the raw point sits
	 * inside the ground so the disk is not born half-buried.
	 */
	private static Vec3 spawnPoint(LocalPlayer player, ClientLevel level) {
		Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(BlackHoleProfile.SPAWN_DISTANCE));
		// Lift until the whole disk clears the terrain, capped so a cave wall does not push the
		// hole into the sky.
		for (int i = 0; i < 12; i++) {
			var pos = net.minecraft.core.BlockPos.containing(origin.x, origin.y - BlackHoleProfile.DISK_OUTER_RADIUS - 1.0, origin.z);
			if (level.getBlockState(pos).isAir()) {
				break;
			}
			origin = origin.add(0.0, 2.0, 0.0);
		}
		return origin;
	}

	private static String format(Vec3 position) {
		return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x, position.y, position.z);
	}
}
