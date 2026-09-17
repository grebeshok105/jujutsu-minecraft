package jujutsu.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.curse.CurseLinkRegistry;
import java.util.Set;
import java.util.UUID;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.KnowledgeLevel;
import jujutsu.mod.cursedincident.SourceKind;

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
						.then(Commands.literal("list").executes(ctx -> listCurseLinks(ctx.getSource()))))
				.then(incidentCommands())
				.then(Commands.literal("debug")
						.then(Commands.literal("black_flash_force")
								.then(Commands.argument("enabled", BoolArgumentType.bool())
										.executes(ctx -> setForcedBlackFlash(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentCommands() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("incident");
		root.then(incidentSpawnCommand());
		root.then(incidentObjectSpawnCommand());
		root.then(incidentInspectCommand());
		root.then(incidentListCommand());
		root.then(incidentSetStageCommand());
		root.then(incidentAdvanceCommand());
		root.then(incidentEscalateCommand());
		root.then(incidentSealCommand());
		root.then(incidentUnsealCommand());
		root.then(incidentDamageSealCommand());
		root.then(incidentRelocateCommand());
		root.then(incidentSecondaryCommand());
		root.then(incidentIdentifyCommand());
		root.then(incidentCleanupCommand());
		root.then(incidentSeedCommand());
		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentSpawnCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("spawn");
		command.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> spawnIncident(ctx))
				.then(Commands.argument("template", StringArgumentType.word())
						.executes(ctx -> spawnIncident(ctx))
						.then(Commands.argument("grade", IntegerArgumentType.integer(1, 5))
								.executes(ctx -> spawnIncident(ctx))
								.then(Commands.argument("seed", LongArgumentType.longArg())
										.executes(ctx -> spawnIncident(ctx))
										.then(Commands.argument("stage", StringArgumentType.word())
												.executes(ctx -> spawnIncident(ctx))
												.then(Commands.argument("object", StringArgumentType.word())
														.executes(ctx -> spawnIncident(ctx))))))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentObjectSpawnCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("object_spawn");
		command.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> spawnIncidentObject(ctx))
				.then(Commands.argument("type", StringArgumentType.word())
						.executes(ctx -> spawnIncidentObject(ctx))
						.then(Commands.argument("grade", IntegerArgumentType.integer(1, 5))
								.executes(ctx -> spawnIncidentObject(ctx))
								.then(Commands.argument("seed", LongArgumentType.longArg())
										.executes(ctx -> spawnIncidentObject(ctx))))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentInspectCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("inspect");
		command.then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> inspectIncident(ctx)));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentListCommand() {
		return Commands.literal("list").executes(ctx -> listIncidents(ctx));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentSetStageCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("set_stage");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("stage", StringArgumentType.word())
						.executes(ctx -> setIncidentStage(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentAdvanceCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("advance");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("ticks", LongArgumentType.longArg(0L))
						.executes(ctx -> advanceIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentEscalateCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("escalate");
		command.then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> escalateIncident(ctx))
				.then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D))
						.executes(ctx -> escalateIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentSealCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("seal");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> sealIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentUnsealCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("unseal");
		command.then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> unsealIncident(ctx)));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentDamageSealCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("damage_seal");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> damageIncidentSeal(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentRelocateCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("relocate");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
						.executes(ctx -> relocateIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentSecondaryCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("secondary");
		command.then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> secondaryIncident(ctx))
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
						.executes(ctx -> secondaryIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentIdentifyCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("identify");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("level", StringArgumentType.word())
						.executes(ctx -> identifyIncident(ctx))));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentCleanupCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("cleanup");
		command.then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> cleanupIncident(ctx)));
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> incidentSeedCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("seed");
		command.then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("value", LongArgumentType.longArg())
						.executes(ctx -> reseedIncident(ctx))));
		return command;
	}

	private static int spawnIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		BlockPos center = BlockPosArgument.getBlockPos(context, "pos");
		String template = optionalString(context, "template");
		Integer grade = optionalInteger(context, "grade");
		Long seed = optionalLong(context, "seed");
		String stageName = optionalString(context, "stage");
		IncidentStage stage = stageName == null ? null : IncidentStage.byName(stageName);
		if (stageName != null && stage == null) {
			source.sendFailure(Component.literal("Unknown incident stage: " + stageName));
			return 0;
		}
		String objectType = optionalString(context, "object");
		SourceKind sourceKind = null;
		if (objectType != null) {
			if (objectType.equalsIgnoreCase("none")) {
				objectType = null;
				sourceKind = SourceKind.FREE;
			} else {
				sourceKind = SourceKind.OBJECT;
			}
		}
		try {
			var record = IncidentControl.spawn(new IncidentControl.SpawnRequest(
					center, source.getLevel().dimension(), template, grade, seed, stage, objectType, sourceKind, null));
			IncidentControl.InspectView view = IncidentControl.inspect(record.id);
			source.sendSuccess(() -> Component.literal("incident spawned: " + renderIncident(view)), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int spawnIncidentObject(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		String type = optionalString(context, "type");
		int grade = optionalInteger(context, "grade", 3);
		long seed = optionalLong(context, "seed", source.getLevel().getGameTime());
		UUID objectId = IncidentControl.spawnObject(source.getLevel(), pos, type == null ? "" : type, grade, seed);
		String result = objectId == null ? "object spawn refused" : "object spawned: " + objectId;
		source.sendSuccess(() -> Component.literal(result), false);
		return objectId == null ? 0 : 1;
	}

	private static int inspectIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		try {
			IncidentControl.InspectView view = IncidentControl.inspect(id);
			source.sendSuccess(() -> Component.literal(renderIncident(view)), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int listIncidents(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		var incidents = IncidentControl.list();
		source.sendSuccess(() -> Component.literal("incidents(" + incidents.size() + "): "
				+ incidents.stream().map(JujutsuCommands::renderIncident).toList()), false);
		return incidents.size();
	}

	private static int setIncidentStage(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		String rawStage = StringArgumentType.getString(context, "stage");
		IncidentStage stage = IncidentStage.byName(rawStage);
		if (stage == null) {
			source.sendFailure(Component.literal("Unknown incident stage: " + rawStage));
			return 0;
		}
		try {
			IncidentControl.setStage(id, stage);
			source.sendSuccess(() -> Component.literal(renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int advanceIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		long ticks = LongArgumentType.getLong(context, "ticks");
		try {
			long age = IncidentControl.advance(id, ticks);
			source.sendSuccess(() -> Component.literal("advanced_ticks=" + ticks + " age_result=" + age
					+ " " + renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int escalateIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		double multiplier = optionalDouble(context, "multiplier", 2.0D);
		if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
			source.sendFailure(Component.literal("multiplier must be finite and positive"));
			return 0;
		}
		try {
			IncidentControl.escalate(id, multiplier);
			source.sendSuccess(() -> Component.literal("multiplier=" + multiplier + " "
					+ renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int sealIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		int tier = IntegerArgumentType.getInteger(context, "tier");
		try {
			IncidentControl.SealAttempt attempt = IncidentControl.seal(id, tier);
			String message = (attempt.ok() ? "seal accepted" : "seal refused")
					+ " requiredTier=" + attempt.requiredTier() + " reason=" + attempt.reason()
					+ " " + renderIncident(IncidentControl.inspect(id));
			source.sendSuccess(() -> Component.literal(message), false);
			return attempt.ok() ? 1 : 0;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int unsealIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		try {
			boolean changed = IncidentControl.unseal(id);
			source.sendSuccess(() -> Component.literal("changed=" + changed + " "
					+ renderIncident(IncidentControl.inspect(id))), false);
			return changed ? 1 : 0;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int damageIncidentSeal(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		int amount = IntegerArgumentType.getInteger(context, "amount");
		try {
			int integrity = IncidentControl.damageSeal(id, amount);
			source.sendSuccess(() -> Component.literal("integrity_result=" + integrity + " "
					+ renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int relocateIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		try {
			IncidentControl.relocate(id, pos);
			source.sendSuccess(() -> Component.literal(renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int secondaryIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		BlockPos pos = hasArgument(context, "pos")
				? BlockPosArgument.getBlockPos(context, "pos")
				: IncidentControl.inspect(id).center();
		try {
			var secondary = IncidentControl.forceSecondary(id, pos);
			source.sendSuccess(() -> Component.literal("secondary=" + secondary + " "
					+ renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int identifyIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		String rawLevel = StringArgumentType.getString(context, "level");
		KnowledgeLevel level = KnowledgeLevel.byName(rawLevel);
		if (level == null) {
			source.sendFailure(Component.literal("Unknown knowledge level: " + rawLevel));
			return 0;
		}
		try {
			IncidentControl.identify(id, level);
			source.sendSuccess(() -> Component.literal(renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int cleanupIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		try {
			IncidentControl.cleanup(id);
			source.sendSuccess(() -> Component.literal(renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static int reseedIncident(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		UUID id = parseIncidentId(source, StringArgumentType.getString(context, "id"));
		if (id == null) return 0;
		long seed = LongArgumentType.getLong(context, "value");
		try {
			long result = IncidentControl.reseed(id, seed);
			source.sendSuccess(() -> Component.literal("seed_result=" + result + " "
					+ renderIncident(IncidentControl.inspect(id))), false);
			return 1;
		} catch (IncidentControl.IncidentNotFoundException e) {
			return incidentFailure(source, e);
		}
	}

	private static String renderIncident(IncidentControl.InspectView view) {
		return "id=" + view.id() + " seed=" + view.seed() + " template=" + view.templateId()
				+ " stage=" + view.stage().wireName() + " scarred=" + view.scarred()
				+ " age_ticks=" + view.ageTicks() + " created_at=" + view.createdGameTime()
				+ " last_update=" + view.lastUpdateGameTime() + " center=" + view.center()
				+ " radius=" + view.radius() + " shape=" + view.zoneShape()
				+ " curse_set=" + view.curseSet() + " atmosphere=" + view.atmosphereId()
				+ " local_goals=" + view.localGoals() + " ignore_shelter=" + view.ignoreShelter()
				+ " source{object_uuid=" + view.objectInstanceId() + ",type=" + view.objectTypeId()
				+ ",grade=" + view.objectGrade() + ",pos=" + view.sourcePos()
				+ ",container=" + view.sourceContainer() + "} seal{state="
				+ (view.sealed() ? "sealed" : "unsealed") + ",integrity=" + view.sealIntegrity()
				+ ",tier=" + view.sealTier() + ",failures=" + view.sealFailures() + "} knowledge="
				+ view.knowledge() + " secondaries=" + view.secondaries()
				+ " dependent_centers=" + view.dependentCenters() + " scars=" + view.scars()
				+ " work_counters=" + view.workCounters() + " transition_log=" + view.transitionLog();
	}

	private static UUID parseIncidentId(CommandSourceStack source, String raw) {
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.literal("Invalid incident UUID: " + raw));
			return null;
		}
	}

	private static int incidentFailure(CommandSourceStack source, IncidentControl.IncidentNotFoundException error) {
		source.sendFailure(Component.literal(error.getMessage()));
		return 0;
	}

	private static String optionalString(CommandContext<CommandSourceStack> context, String name) {
		return hasArgument(context, name) ? StringArgumentType.getString(context, name) : null;
	}

	private static Integer optionalInteger(CommandContext<CommandSourceStack> context, String name) {
		return hasArgument(context, name)
				? IntegerArgumentType.getInteger(context, name)
				: null;
	}

	private static int optionalInteger(CommandContext<CommandSourceStack> context, String name, int fallback) {
		return hasArgument(context, name) ? IntegerArgumentType.getInteger(context, name) : fallback;
	}

	private static Long optionalLong(CommandContext<CommandSourceStack> context, String name) {
		return hasArgument(context, name) ? LongArgumentType.getLong(context, name) : null;
	}

	private static long optionalLong(CommandContext<CommandSourceStack> context, String name, long fallback) {
		return hasArgument(context, name) ? LongArgumentType.getLong(context, name) : fallback;
	}

	private static double optionalDouble(CommandContext<CommandSourceStack> context, String name, double fallback) {
		return hasArgument(context, name) ? DoubleArgumentType.getDouble(context, name) : fallback;
	}

	private static boolean hasArgument(CommandContext<CommandSourceStack> context, String name) {
		try {
			context.getArgument(name, Object.class);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static int setForcedBlackFlash(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ForcedBlackFlash.set(source.getPlayerOrException(), enabled);
		source.sendSuccess(() -> Component.literal("Forced Black Flash: " + enabled), false);
		return 1;
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
		return castAbilitySlot(source, CharacterAbility.PRIMARY, "Hairpin Enlarge");
	}

	private static int castHairpinExplosion(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return castAbilitySlot(source, CharacterAbility.SECONDARY, "Hairpin Explosion");
	}

	/**
	 * These go through the shared gate rather than straight into a runtime, so they exercise the same
	 * selection, cooldown and stagger checks a key press does.
	 *
	 * <p>They stay Nobara's, though. A slot is an input position, so {@code PRIMARY} means a hairpin for
	 * her and a teleport for Todo — without this check a command that says "Hairpin" would fire Todo's
	 * swap and then report it as a hairpin.
	 */
	private static int castAbilitySlot(CommandSourceStack source, CharacterAbility slot, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA) {
			source.sendSuccess(() -> Component.literal(label + " did not cast: it is Nobara's."), false);
			return 0;
		}
		AbilityResult result = CharacterAbilityExecutor.tryCast(player, slot, true);
		source.sendSuccess(() -> Component.literal(result == AbilityResult.SUCCESS ? "Cast " + label + "." : label + " did not cast."), false);
		return result == AbilityResult.SUCCESS ? 1 : 0;
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
