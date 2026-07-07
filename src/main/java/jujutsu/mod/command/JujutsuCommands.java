package jujutsu.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.fx.HairpinTimeline;
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
								.executes(ctx -> playHairpinParticlePreview(ctx.getSource())))
						.then(Commands.literal("particle")
								.then(Commands.literal("mark_stain")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "mark_stain", JujutsuParticles.HAIRPIN_MARK_STAIN)))
								.then(Commands.literal("warn_edge")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "warn_edge", JujutsuParticles.HAIRPIN_WARN_EDGE)))
								.then(Commands.literal("compression_mote")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "compression_mote", JujutsuParticles.HAIRPIN_COMPRESSION_MOTE)))
								.then(Commands.literal("snap_crack")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "snap_crack", JujutsuParticles.HAIRPIN_SNAP_CRACK)))
								.then(Commands.literal("burst_residue")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "burst_residue", JujutsuParticles.HAIRPIN_BURST_RESIDUE)))
								.then(Commands.literal("metal_shard")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "metal_shard", JujutsuParticles.HAIRPIN_BURST_METAL_SHARD)))
								.then(Commands.literal("ignition_tick")
										.executes(ctx -> playSingleParticle(ctx.getSource(), "ignition_tick", JujutsuParticles.HAIRPIN_IGNITION_TICK))))
						.then(Commands.literal("stage")
								.then(Commands.literal("mark")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "mark")))
								.then(Commands.literal("warning")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "warning")))
								.then(Commands.literal("compression")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "compression")))
								.then(Commands.literal("snap")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "snap")))
								.then(Commands.literal("burst")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "burst")))
								.then(Commands.literal("afterglow")
										.executes(ctx -> playHairpinStage(ctx.getSource(), "afterglow"))))
						.executes(ctx -> playHairpin(ctx.getSource())))
				.then(Commands.literal("debug")
						.then(Commands.literal("hairpin")
								.executes(ctx -> reportHairpinDebug(ctx.getSource()))
								.then(Commands.literal("true")
										.executes(ctx -> setHairpinDebug(ctx.getSource(), true)))
								.then(Commands.literal("false")
										.executes(ctx -> setHairpinDebug(ctx.getSource(), false))))));
	}

	private static int reportHairpinDebug(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("Hairpin debug logging is " + (HairpinDebugLog.isEnabled() ? "enabled" : "disabled")), false);
		return 1;
	}

	private static int setHairpinDebug(CommandSourceStack source, boolean enabled) {
		HairpinDebugLog.setEnabled(enabled);
		source.sendSuccess(() -> Component.literal("Hairpin debug logging " + (enabled ? "enabled" : "disabled")), true);
		return 1;
	}

	private static int playSingleParticle(CommandSourceStack source, String label, SimpleParticleType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		VisualProbe probe = createVisualProbe(source, 3.0);
		sendControlledParticle(source, probe.player(), label, type, probe.center());
		HairpinDebugLog.info("command /jujutsu hairpin particle {} player={} center={}", label, probe.player().getGameProfile().getName(), HairpinDebugLog.vec(probe.center()));
		source.sendSuccess(() -> Component.literal("Spawned controlled Hairpin particle: " + label), false);
		return 1;
	}

	private static int playHairpinStage(CommandSourceStack source, String stage) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		HairpinTimeline.Phase phase = switch (stage) {
			case "mark" -> HairpinTimeline.Phase.PREP_FREEZE;
			case "warning" -> HairpinTimeline.Phase.HAMMER_SNAP;
			case "compression" -> HairpinTimeline.Phase.NAIL_IGNITION;
			case "snap", "burst" -> HairpinTimeline.Phase.HAIRPIN_BLOOM;
			case "afterglow" -> HairpinTimeline.Phase.AFTERGLOW;
			default -> {
				source.sendFailure(Component.literal("Unknown Hairpin stage: " + stage));
				yield null;
			}
		};
		if (phase == null) {
			return 0;
		}

		return playHairpinAtPhase(source, phase, "stage " + stage);
	}

	private static int playHairpinParticlePreview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		VisualProbe probe = createVisualProbe(source, 3.0);
		ServerPlayer player = probe.player();
		Vec3 look = probe.look();
		Vec3 center = probe.center();
		Vec3 right = probe.right();
		Vec3 up = probe.up();

		HairpinDebugLog.info("command /jujutsu hairpin particles player={} center={} look={}", player.getGameProfile().getName(), HairpinDebugLog.vec(center), HairpinDebugLog.vec(look));
		sendControlledParticle(source, player, "mark_stain", JujutsuParticles.HAIRPIN_MARK_STAIN, center.add(up.scale(0.75)));
		sendControlledParticle(source, player, "warn_edge", JujutsuParticles.HAIRPIN_WARN_EDGE, center.add(right.scale(-0.9)));
		sendControlledParticle(source, player, "compression_mote", JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, center.add(right.scale(0.9)));
		sendControlledParticle(source, player, "snap_crack", JujutsuParticles.HAIRPIN_SNAP_CRACK, center);
		sendControlledParticle(source, player, "burst_residue", JujutsuParticles.HAIRPIN_BURST_RESIDUE, center.add(up.scale(-0.65)));
		sendControlledParticle(source, player, "burst_metal_shard", JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, center.add(right.scale(-0.35)).add(up.scale(-0.25)));
		sendControlledParticle(source, player, "ignition_tick", JujutsuParticles.HAIRPIN_IGNITION_TICK, center.add(right.scale(0.35)).add(up.scale(-0.25)));

		source.sendSuccess(() -> Component.literal("Spawned controlled Hairpin particle preview."), false);
		return 1;
	}

	private static VisualProbe createVisualProbe(CommandSourceStack source, double distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 center = eye.add(look.scale(distance));
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-4) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();
		return new VisualProbe(player, look, center, right, up);
	}

	private static void sendControlledParticle(CommandSourceStack source, ServerPlayer player, String label, SimpleParticleType type, Vec3 position) {
		boolean sent = source.getLevel().sendParticles(player, type, true, true, position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
		HairpinDebugLog.info("controlled particle label={} sent={} pos={}", label, sent, HairpinDebugLog.vec(position));
	}

	private record VisualProbe(ServerPlayer player, Vec3 look, Vec3 center, Vec3 right, Vec3 up) {}
	private record HairpinCast(ServerPlayer player, Vec3 look, Vec3 target, Vec3 nail0, Vec3 nail1, Vec3 nail2, Vec3 nail3, HairpinFxPayload payload) {}

	private static int playHairpin(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return playHairpinAtPhase(source, HairpinTimeline.Phase.PREP_FREEZE, "cinematic prototype");
	}

	private static int playHairpinAtPhase(CommandSourceStack source, HairpinTimeline.Phase phase, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		long phaseOffsetTicks = Math.round(HairpinTimeline.phaseStartMillis(phase) / 50.0f);
		long startGameTime = Math.max(0L, source.getLevel().getGameTime() - phaseOffsetTicks);
		HairpinCast cast = createHairpinCast(source, startGameTime);
		HairpinDebugLog.info(
				"command /jujutsu hairpin {} player={} seed={} phase={} startGameTime={} target={} look={} nails=[{}|{}|{}|{}]",
				label,
				cast.player().getGameProfile().getName(),
				cast.payload().seed(),
				phase,
				startGameTime,
				HairpinDebugLog.vec(cast.target()),
				HairpinDebugLog.vec(cast.look()),
				HairpinDebugLog.vec(cast.nail0()),
				HairpinDebugLog.vec(cast.nail1()),
				HairpinDebugLog.vec(cast.nail2()),
				HairpinDebugLog.vec(cast.nail3())
		);
		int sent = JujutsuNetworking.broadcastHairpin(source.getLevel(), cast.target(), BROADCAST_RADIUS, cast.payload());
		HairpinDebugLog.info("command /jujutsu hairpin broadcast sent={} radius={} seed={}", sent, BROADCAST_RADIUS, cast.payload().seed());
		if (sent == 0) {
			source.sendFailure(Component.literal("No nearby client can receive Hairpin."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Triggered Hairpin " + label + " for " + sent + " client(s)."), false);
		return 1;
	}

	private static HairpinCast createHairpinCast(CommandSourceStack source, long startGameTime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
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
				startGameTime,
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
		return new HairpinCast(player, look, target, nail0, nail1, nail2, nail3, payload);
	}
}
