package jujutsu.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraActions;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.curse.CurseLinkRegistry;
import java.util.Set;

public final class JujutsuCommands {
	private JujutsuCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(JujutsuCommands::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("jujutsu")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("hairpin")
						.then(Commands.literal("enlarge")
								.executes(ctx -> castHairpinEnlarge(ctx.getSource())))
						.then(Commands.literal("explosion")
								.executes(ctx -> castHairpinExplosion(ctx.getSource())))
						.then(Commands.literal("particles")
								.executes(ctx -> playProjectJjkImpactPreview(ctx.getSource())))
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
						.executes(ctx -> playProjectJjkImpactPreview(ctx.getSource())))
				.then(Commands.literal("give")
						.then(Commands.literal("nobara_tools")
								.executes(ctx -> giveNobaraTools(ctx.getSource()))))
				.then(Commands.literal("curse_link")
						.then(Commands.literal("create")
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> createTestCurseLink(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
						.then(Commands.literal("clear").executes(ctx -> clearTestCurseLinks(ctx.getSource())))
						.then(Commands.literal("list").executes(ctx -> listCurseLinks(ctx.getSource())))));
	}

	private static int createTestCurseLink(CommandSourceStack source, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer owner = source.getPlayerOrException();
		var link = CurseLinkRegistry.GLOBAL.createLink(owner.getUUID(), jujutsu.mod.JujutsuMod.id("dev_decay"), Set.of(owner.getUUID(), target.getUUID()), owner.level().getGameTime());
		source.sendSuccess(() -> Component.literal("Created dev curse link " + link.id()), false);
		return 1;
	}

	private static int clearTestCurseLinks(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int removed = CurseLinkRegistry.GLOBAL.removeLinksOwnedBy(source.getPlayerOrException().getUUID());
		source.sendSuccess(() -> Component.literal("Removed " + removed + " owned curse links."), false);
		return removed;
	}

	private static int listCurseLinks(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var links = CurseLinkRegistry.GLOBAL.linksForParticipant(source.getPlayerOrException().getUUID());
		source.sendSuccess(() -> Component.literal("Active curse links: " + links.size() + " " + links.stream().map(link -> link.techniqueId().toString()).toList()), false);
		return links.size();
	}

	private static int giveNobaraTools(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean storedHammer = giveOrDrop(player, new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
		boolean storedNails = giveOrDrop(player, new ItemStack(JujutsuItems.HAIRPIN_NAIL, 16));
		String location = storedHammer && storedNails ? "inventory" : "inventory/drop overflow";
		source.sendSuccess(() -> Component.literal("Gave Nobara JJK hammer and Hairpin nails (" + location + ")."), false);
		return 1;
	}

	private static boolean giveOrDrop(ServerPlayer player, ItemStack stack) {
		boolean stored = player.getInventory().add(stack);
		if (!stored && !stack.isEmpty()) {
			player.drop(stack, false);
		}
		return stored;
	}

	private static int castHairpinEnlarge(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return castNobaraAction(source, ProjectJjkNobaraActions.HAIRPIN_ENLARGE, "Hairpin Enlarge");
	}

	private static int castHairpinExplosion(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return castNobaraAction(source, ProjectJjkNobaraActions.HAIRPIN_EXPLOSION, "Hairpin Explosion");
	}

	private static int castNobaraAction(CommandSourceStack source, int action, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean cast = ProjectJjkNobaraActions.tryCast(player, action, true);
		source.sendSuccess(() -> Component.literal(cast ? "Cast " + label + "." : label + " did not cast."), false);
		return cast ? 1 : 0;
	}

	private static int playSingleParticle(CommandSourceStack source, String label, SimpleParticleType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		VisualProbe probe = createVisualProbe(source, 3.0);
		sendControlledParticles(source, probe.player(), type, probe.center(), 1, 0.0, 0.0, 0.0, 0.0);
		source.sendSuccess(() -> Component.literal("Spawned Nobara particle: " + label), false);
		return 1;
	}

	private static int playProjectJjkImpactPreview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		VisualProbe probe = createVisualProbe(source, 3.0);
		ServerPlayer player = probe.player();
		Vec3 center = probe.center();
		Vec3 right = probe.right();
		Vec3 up = probe.up();

		sendControlledParticles(source, player, JujutsuParticles.HAIRPIN_IGNITION_TICK, center.add(probe.look().scale(-0.12)), 5, 0.08, 0.08, 0.08, 0.04);
		sendControlledParticles(source, player, JujutsuParticles.HAIRPIN_SPARK, center, 24, 0.34, 0.28, 0.34, 0.24);
		sendControlledParticles(source, player, JujutsuParticles.HAIRPIN_SNAP_CRACK, center.add(up.scale(0.04)), 4, 0.16, 0.10, 0.16, 0.06);
		sendControlledParticles(source, player, JujutsuParticles.HAIRPIN_BURST_RESIDUE, center.add(probe.look().scale(-0.05)), 18, 0.44, 0.30, 0.44, 0.18);
		sendControlledParticles(source, player, JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, center.add(right.scale(-0.06)).add(up.scale(0.02)), 14, 0.38, 0.22, 0.38, 0.32);

		source.sendSuccess(() -> Component.literal("Spawned Nobara JJK nail impact particle preview."), false);
		return 1;
	}

	private static VisualProbe createVisualProbe(CommandSourceStack source, double distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 look = safeDirection(player.getLookAngle());
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

	private static void sendControlledParticles(CommandSourceStack source, ServerPlayer player, SimpleParticleType type, Vec3 position, int count, double xSpread, double ySpread, double zSpread, double speed) {
		source.getLevel().sendParticles(player, type, true, true, position.x, position.y, position.z, count, xSpread, ySpread, zSpread, speed);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private record VisualProbe(ServerPlayer player, Vec3 look, Vec3 center, Vec3 right, Vec3 up) {}
}
