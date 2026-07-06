package jujutsu.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.JujutsuNetworking;

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
						.executes(ctx -> playHairpin(ctx.getSource()))));
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

		HairpinFxPayload payload = new HairpinFxPayload(
				(int) (source.getLevel().getGameTime() ^ (long) player.getId() * 31L),
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

		int sent = JujutsuNetworking.broadcastHairpin(source.getLevel(), target, BROADCAST_RADIUS, payload);
		if (sent == 0) {
			source.sendFailure(Component.literal("No nearby client can receive Hairpin."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Triggered Hairpin cinematic prototype for " + sent + " client(s)."), false);
		return 1;
	}
}
