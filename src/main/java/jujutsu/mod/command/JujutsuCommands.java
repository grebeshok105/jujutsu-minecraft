package jujutsu.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuParticles;

public final class JujutsuCommands {
	private static final double TARGET_DISTANCE = 5.0;
	private static final double BROADCAST_RADIUS = 64.0;

	private JujutsuCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(JujutsuCommands::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("jujutsu")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("hairpin")
						.then(Commands.literal("particles")
								.executes(ctx -> playHairpinParticleSmoke(ctx.getSource())))
						.executes(ctx -> playHairpin(ctx.getSource()))));
	}

	private static int playHairpinParticleSmoke(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 center = eye.add(look.scale(3.0));
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-4) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();

		HairpinDebugLog.info("command /jujutsu hairpin particles player={} center={} look={}", player.getGameProfile().getName(), HairpinDebugLog.vec(center), HairpinDebugLog.vec(look));
		sendSmokeParticles(source, player, "mark_stain", JujutsuParticles.HAIRPIN_MARK_STAIN, center.add(up.scale(0.75)), 18, 0.28, 0.18, 0.28, 0.02);
		sendSmokeParticles(source, player, "warn_edge", JujutsuParticles.HAIRPIN_WARN_EDGE, center.add(right.scale(-0.9)), 24, 0.18, 0.18, 0.18, 0.06);
		sendSmokeParticles(source, player, "compression_mote", JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, center.add(right.scale(0.9)), 34, 0.22, 0.22, 0.22, 0.05);
		sendSmokeParticles(source, player, "snap_crack", JujutsuParticles.HAIRPIN_SNAP_CRACK, center, 24, 0.18, 0.18, 0.18, 0.05);
		sendSmokeParticles(source, player, "burst_residue", JujutsuParticles.HAIRPIN_BURST_RESIDUE, center.add(up.scale(-0.65)), 42, 0.35, 0.25, 0.35, 0.08);
		sendSmokeParticles(source, player, "burst_metal_shard", JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, center.add(right.scale(-0.35)).add(up.scale(-0.25)), 24, 0.26, 0.18, 0.26, 0.12);
		sendSmokeParticles(source, player, "ignition_tick", JujutsuParticles.HAIRPIN_IGNITION_TICK, center.add(right.scale(0.35)).add(up.scale(-0.25)), 30, 0.18, 0.18, 0.18, 0.06);

		source.sendSuccess(() -> Component.literal("Spawned Hairpin particle smoke test."), false);
		return 1;
	}

	private static void sendSmokeParticles(CommandSourceStack source, ServerPlayer player, String label, net.minecraft.core.particles.SimpleParticleType type, Vec3 position, int count, double xSpread, double ySpread, double zSpread, double speed) {
		boolean sent = source.getLevel().sendParticles(player, type, true, true, position.x, position.y, position.z, count, xSpread, ySpread, zSpread, speed);
		HairpinDebugLog.info("smoke particles label={} sent={} count={} pos={} spread={},{},{} speed={}", label, sent, count, HairpinDebugLog.vec(position), xSpread, ySpread, zSpread, speed);
	}

	private static int playHairpin(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 target = eye.add(look.scale(TARGET_DISTANCE));
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-4) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();

		Vec3 nail0 = target.add(right.scale(-1.1)).add(up.scale(0.45));
		Vec3 nail1 = target.add(right.scale(1.1)).add(up.scale(0.30));
		Vec3 nail2 = target.add(right.scale(-0.45)).add(up.scale(-0.55));
		Vec3 nail3 = target.add(right.scale(0.55)).add(up.scale(-0.70));
		int seed = (int) (source.getLevel().getGameTime() ^ (long) player.getId() * 31L);

		HairpinFxPayload payload = new HairpinFxPayload(
				seed,
				target.x,
				target.y,
				target.z,
				source.getLevel().getGameTime(),
				nail0.x,
				nail0.y,
				nail0.z,
				nail1.x,
				nail1.y,
				nail1.z,
				nail2.x,
				nail2.y,
				nail2.z,
				nail3.x,
				nail3.y,
				nail3.z
		);

		HairpinDebugLog.info("command /jujutsu hairpin player={} seed={} target={} look={} nails=[{}|{}|{}|{}]", player.getGameProfile().getName(), seed, HairpinDebugLog.vec(target), HairpinDebugLog.vec(look), HairpinDebugLog.vec(nail0), HairpinDebugLog.vec(nail1), HairpinDebugLog.vec(nail2), HairpinDebugLog.vec(nail3));
		int sent = JujutsuNetworking.broadcastHairpin(source.getLevel(), target, BROADCAST_RADIUS, payload);
		HairpinDebugLog.info("command /jujutsu hairpin broadcast sent={} radius={} seed={}", sent, BROADCAST_RADIUS, seed);
		if (sent == 0) {
			source.sendFailure(Component.literal("No nearby client can receive Hairpin."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Triggered Hairpin cinematic prototype for " + sent + " client(s)."), false);
		return 1;
	}
}
