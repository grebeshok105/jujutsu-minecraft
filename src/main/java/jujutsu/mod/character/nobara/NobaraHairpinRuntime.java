package jujutsu.mod.character.nobara;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.fx.HairpinTimeline;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.network.PreparedNailsPayload;
import jujutsu.mod.registry.JujutsuItems;

public final class NobaraHairpinRuntime {
	private static final double TARGET_RANGE = 32.0;
	private static final double BROADCAST_RADIUS = 64.0;
	private static final double DAMAGE_RADIUS = 4.25;
	private static final NobaraCombatStateManager STATE = new NobaraCombatStateManager();
	private static final List<PendingImpact> PENDING_IMPACTS = new ArrayList<>();
	private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSIONS = new HashMap<>();
	private static final Map<UUID, String> LAST_TARGETS = new HashMap<>();

	private NobaraHairpinRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NobaraHairpinRuntime::tickServer);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
	}

	public static void prepareNails(ServerPlayer player, ItemStack stack) {
		ServerLevel level = player.level();
		long gameTime = level.getGameTime();
		boolean creative = player.getAbilities().instabuild;
		int availableNails = creative ? NobaraCombatStateManager.MAX_PREPARED_NAILS : countHairpinNails(player);
		NobaraCombatStateManager.PrepareResult result = STATE.prepareNails(player.getUUID(), dimensionId(level), gameTime, availableNails, creative);
		if (result.preparedCount() <= 0) {
			player.displayClientMessage(Component.literal("No Hairpin nails to prepare."), true);
			return;
		}

		if (result.consumedCount() > 0) {
			consumeHairpinNails(player, stack, result.consumedCount());
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.28f, 0.58f);
		int seed = seed(level, player, result.preparedCount());
		Vec3 rowOrigin = player.getEyePosition();
		List<Vec3> row = HairpinGameplayService.preparedNailRow(rowOrigin, player.getLookAngle(), result.preparedCount());
		PreparedNailsPayload payload = PreparedNailsPayload.create(seed, player.getId(), result.preparedCount(), gameTime, row);
		int sent = JujutsuNetworking.broadcastPreparedNails(level, player.position(), BROADCAST_RADIUS, payload);
		HairpinDebugLog.info("nobara prepared nails player={} count={} consumed={} sent={}", player.getGameProfile().getName(), result.preparedCount(), result.consumedCount(), sent);
		player.displayClientMessage(Component.literal("Prepared " + result.preparedCount() + " cursed nail(s)."), true);
	}

	public static void startHairpin(ServerPlayer player, ItemStack hammerStack, InteractionHand hand) {
		ServerLevel level = player.level();
		long gameTime = level.getGameTime();
		NobaraCombatStateManager.LaunchResult launch = STATE.startHairpin(player.getUUID(), dimensionId(level), gameTime);
		if (!launch.accepted()) {
			String message = launch.reason() == NobaraCombatStateManager.RejectReason.COOLDOWN ? "Hammer is recovering." : "Prepare nails first.";
			player.displayClientMessage(Component.literal(message), true);
			HairpinDebugLog.info("nobara hairpin rejected player={} reason={}", player.getGameProfile().getName(), launch.reason());
			return;
		}

		TargetResolver.Result target = TargetResolver.resolve(level, player, TARGET_RANGE);
		List<Vec3> starts = HairpinGameplayService.preparedNailRow(player.getEyePosition(), player.getLookAngle(), launch.nailCount());
		List<Vec3> fourStarts = fillNails(starts, target.point());
		int seed = seed(level, player, launch.nailCount());
		int targetEntityId = target.entityId().orElse(-1);
		HairpinNailFlightPayload flightPayload = flightPayload(seed, player.getId(), targetEntityId, launch.nailCount(), target.point(), launch.windupEndsAt(), fourStarts);
		int sent = JujutsuNetworking.broadcastNailFlight(level, target.point(), BROADCAST_RADIUS, flightPayload);
		PENDING_IMPACTS.add(new PendingImpact(player.getUUID(), level.dimension(), targetEntityId, target.point(), fourStarts, launch.nailCount(), launch.impactAt(), seed));
		LAST_TARGETS.put(player.getUUID(), target.mode() + " " + format(target.point()) + target.entityId().map(id -> " entity=" + id).orElse(""));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.9f, 0.68f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.PLAYERS, 0.5f, 0.74f);
		damageHammer(player, hammerStack, hand);
		HairpinDebugLog.info("nobara hairpin launched player={} nails={} targetMode={} target={} impactAt={} sent={}", player.getGameProfile().getName(), launch.nailCount(), target.mode(), HairpinDebugLog.vec(target.point()), launch.impactAt(), sent);
	}

	public static String describe(ServerPlayer player) {
		ServerLevel level = player.level();
		NobaraCombatStateManager.State state = STATE.state(player.getUUID());
		long gameTime = level.getGameTime();
		long cooldownLeft = Math.max(0L, state.cooldownEndsAt() - gameTime);
		return "phase=" + state.phaseAt(gameTime)
				+ " prepared=" + state.preparedCount(gameTime)
				+ " cooldownLeft=" + cooldownLeft
				+ " pendingImpacts=" + pendingCount(player.getUUID())
				+ " lastTarget=" + LAST_TARGETS.getOrDefault(player.getUUID(), "none");
	}

	public static void clear(ServerPlayer player) {
		clear(player.getUUID());
		player.displayClientMessage(Component.literal("Cleared Nobara combat state."), true);
	}

	private static void clear(UUID playerId) {
		STATE.clear(playerId);
		LAST_DIMENSIONS.remove(playerId);
		LAST_TARGETS.remove(playerId);
		PENDING_IMPACTS.removeIf(impact -> impact.ownerId().equals(playerId));
	}

	private static void tickServer(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ResourceKey<Level> previous = LAST_DIMENSIONS.put(player.getUUID(), player.level().dimension());
			if ((previous != null && !previous.equals(player.level().dimension())) || !player.isAlive()) {
				clear(player.getUUID());
			}
		}

		Iterator<PendingImpact> iterator = PENDING_IMPACTS.iterator();
		while (iterator.hasNext()) {
			PendingImpact impact = iterator.next();
			ServerLevel level = server.getLevel(impact.dimension());
			if (level == null || level.getGameTime() < impact.impactAt()) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(impact.ownerId());
			if (owner != null) {
				resolveImpact(level, owner, impact);
			}
			STATE.markImpactResolved(impact.ownerId(), level.getGameTime());
			iterator.remove();
		}
	}

	private static void resolveImpact(ServerLevel level, ServerPlayer owner, PendingImpact impact) {
		float damage = HairpinGameplayService.damageForNailCount(impact.nailCount());
		float knockback = HairpinGameplayService.knockbackForNailCount(impact.nailCount());
		AABB bounds = new AABB(impact.target(), impact.target()).inflate(DAMAGE_RADIUS);
		for (Entity entity : level.getEntities(owner, bounds, candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living) || living.getUUID().equals(owner.getUUID())) {
				continue;
			}
			living.hurtServer(level, owner.damageSources().playerAttack(owner), damage);
			Vec3 direction = living.position().subtract(impact.target());
			if (direction.lengthSqr() < 1.0E-5) {
				direction = owner.getLookAngle();
			}
			living.knockback(knockback, -direction.x, -direction.z);
		}

		HairpinFxPayload payload = hairpinPayload(impact, Math.max(0L, level.getGameTime() - Math.round(HairpinTimeline.phaseStartMillis(HairpinTimeline.Phase.HAIRPIN_BLOOM) / 50.0f)));
		int sent = JujutsuNetworking.broadcastHairpin(level, impact.target(), BROADCAST_RADIUS, payload);
		HairpinDebugLog.info("nobara hairpin impact owner={} nails={} damage={} knockback={} sent={} target={}", owner.getGameProfile().getName(), impact.nailCount(), damage, knockback, sent, HairpinDebugLog.vec(impact.target()));
	}

	private static void damageHammer(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		if (player.getAbilities().instabuild || stack.isEmpty()) {
			return;
		}
		EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
		stack.hurtAndBreak(1, player.level(), player, item -> player.onEquippedItemBroken(item, slot));
	}

	private static int countHairpinNails(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(JujutsuItems.HAIRPIN_NAIL)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void consumeHairpinNails(ServerPlayer player, ItemStack usedStack, int count) {
		int remaining = count;
		if (usedStack.is(JujutsuItems.HAIRPIN_NAIL)) {
			int consumed = Math.min(remaining, usedStack.getCount());
			usedStack.shrink(consumed);
			remaining -= consumed;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(JujutsuItems.HAIRPIN_NAIL)) {
				continue;
			}
			int consumed = Math.min(remaining, stack.getCount());
			stack.shrink(consumed);
			remaining -= consumed;
		}
	}

	private static HairpinNailFlightPayload flightPayload(int seed, int ownerEntityId, int targetEntityId, int nailCount, Vec3 target, long startGameTime, List<Vec3> nails) {
		return new HairpinNailFlightPayload(
				seed,
				ownerEntityId,
				targetEntityId,
				nailCount,
				target.x,
				target.y,
				target.z,
				startGameTime,
				nails.get(0).x,
				nails.get(0).y,
				nails.get(0).z,
				nails.get(1).x,
				nails.get(1).y,
				nails.get(1).z,
				nails.get(2).x,
				nails.get(2).y,
				nails.get(2).z,
				nails.get(3).x,
				nails.get(3).y,
				nails.get(3).z
		);
	}

	private static HairpinFxPayload hairpinPayload(PendingImpact impact, long startGameTime) {
		List<Vec3> nails = impact.nails();
		Vec3 target = impact.target();
		return new HairpinFxPayload(
				impact.seed(),
				impact.targetEntityId(),
				target.x,
				target.y,
				target.z,
				startGameTime,
				nails.get(0).x,
				nails.get(0).y,
				nails.get(0).z,
				nails.get(1).x,
				nails.get(1).y,
				nails.get(1).z,
				nails.get(2).x,
				nails.get(2).y,
				nails.get(2).z,
				nails.get(3).x,
				nails.get(3).y,
				nails.get(3).z
		);
	}

	private static List<Vec3> fillNails(List<Vec3> nails, Vec3 target) {
		List<Vec3> filled = new ArrayList<>(nails);
		while (filled.size() < 4) {
			filled.add(target);
		}
		return List.copyOf(filled.subList(0, 4));
	}

	private static int seed(ServerLevel level, ServerPlayer player, int nailCount) {
		return (int) (level.getGameTime() * 31L ^ player.getUUID().getLeastSignificantBits() ^ nailCount * 131L);
	}

	private static int pendingCount(UUID playerId) {
		int count = 0;
		for (PendingImpact impact : PENDING_IMPACTS) {
			if (impact.ownerId().equals(playerId)) {
				count++;
			}
		}
		return count;
	}

	private static String dimensionId(ServerLevel level) {
		return level.dimension().location().toString();
	}

	private static String format(Vec3 vec) {
		BlockPos pos = BlockPos.containing(vec);
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private record PendingImpact(UUID ownerId, ResourceKey<Level> dimension, int targetEntityId, Vec3 target, List<Vec3> nails, int nailCount, long impactAt, int seed) {}
}
