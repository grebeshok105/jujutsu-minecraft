package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Straw Doll's strong connection: Deeply Anchored setup, then a fixed 40-tick ritual.
 *
 * <p>Unlike the retired implementation this runtime never mutates the server tick rate. The
 * pending ritual is an authored sequence in game ticks, and resources are consumed only at the
 * release beat after the target is resolved again.
 */
public final class ProjectJjkStrawDollRuntime {
	private static final int RITUAL_VFX_INTENSITY = 2;
	private static final Double VFX_DELIVERY_RADIUS = 64.0;
	private static final Map<UUID, PendingRitual> PENDING_RITUALS = new HashMap<>();

	private ProjectJjkStrawDollRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkStrawDollRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
	}

	/**
	 * Extracts a remnant from the overhead hammer role. Setup is deliberately not consumed: only
	 * the later release beat spends one nail and one remnant. A live bound remnant makes extraction
	 * idempotent for a caster-target pair.
	 */
	public static boolean tryExtractRemnant(ServerPlayer caster, LivingEntity target) {
		if (caster == null || target == null || !caster.isAlive() || !target.isAlive()
				|| caster.getUUID().equals(target.getUUID())
				|| !(caster.level() instanceof ServerLevel level)
				|| target.level() != level) {
			return false;
		}
		if (!NailAnchorRegistry.isDeeplyAnchored(level, caster.getUUID(), target.getUUID())
				|| hasLiveBoundRemnant(caster, target.getUUID())) {
			return false;
		}
		return mintRemnant(caster, level, target, target.position().add(0.0, target.getBbHeight() * 0.55, 0.0));
	}

	/**
	 * Starts the S+hammer-RMB ritual. The item/hand gate is intentionally inside this method so
	 * every caller receives the same handled failure semantics.
	 */
	public static AbilityResult tryStartResonance(ServerPlayer caster) {
		if (caster == null || !isHammer(caster.getMainHandItem())) {
			if (caster != null) {
				showFailure(caster, ResonancePolicy.Validation.NO_DOLL);
			}
			return AbilityResult.HANDLED_FAILURE;
		}

		Selection selection = selectRemnant(caster, PENDING_RITUALS.containsKey(caster.getUUID()));
		if (selection.validation() != ResonancePolicy.Validation.OK || selection.remnant() == null) {
			showFailure(caster, selection.validation());
			return AbilityResult.HANDLED_FAILURE;
		}

		PendingRitual pending = new PendingRitual(
				caster.getUUID(),
				selection.remnant(),
				caster.level().getGameTime()
		);
		PENDING_RITUALS.put(caster.getUUID(), pending);
		triggerDollRitual(caster);
		Vec3 origin = caster.getEyePosition().add(caster.getLookAngle().scale(0.45));
		long gameTime = caster.level().getGameTime();
		JujutsuNetworking.broadcastVfxCue(caster.level(), caster.position(), VFX_DELIVERY_RADIUS,
				cue(caster.level(), NobaraVfxIds.RITUAL_BIND, 1, origin, gameTime, caster));
		emitCasterAction(caster, NobaraVfxIds.CASTER_RESONANCE_RITUAL);
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.nobara.resonance.casting",
				selection.remnant().targetName()
		), true);
		return AbilityResult.SUCCESS;
	}

	private static void onServerTick(MinecraftServer server) {
		for (PendingRitual pending : List.copyOf(PENDING_RITUALS.values())) {
			if (PENDING_RITUALS.get(pending.casterId()) != pending) {
				continue;
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(pending.casterId());
			if (caster == null || !caster.isAlive()) {
				PENDING_RITUALS.remove(pending.casterId(), pending);
				continue;
			}

			long elapsed = caster.level().getGameTime() - pending.startedAt();
			if (elapsed >= NobaraActionTimeline.RESONANCE_WINDUP_TICK && !pending.windupEmitted()) {
				pending.markWindupEmitted();
				emitWorldCue(caster, NobaraVfxIds.RITUAL_WINDUP, caster.getEyePosition(), RITUAL_VFX_INTENSITY);
			}
			if (elapsed >= NobaraActionTimeline.DOLL_STRIKE.impactTick() && !pending.strikeEmitted()) {
				pending.markStrikeEmitted();
				triggerDollImpact(caster);
				Vec3 targetOrigin = resolveTargetOrigin(caster, pending.remnant());
				emitWorldCue(caster, NobaraVfxIds.DOLL_STRIKE, targetOrigin, RITUAL_VFX_INTENSITY);
				emitResonanceLink(caster);
			}
			if (elapsed >= NobaraActionTimeline.RESONANCE_RELEASE_TICK) {
				resolveRelease(caster, pending);
				PENDING_RITUALS.remove(pending.casterId(), pending);
			}
		}
	}

	private static Selection selectRemnant(ServerPlayer caster, boolean alreadyCasting) {
		boolean hasDoll = isDoll(caster.getOffhandItem());
		boolean hasNail = hasNail(caster);
		ResonancePolicy.Validation fallback = ResonancePolicy.validate(
				hasDoll,
				false,
				hasNail,
				true,
				false,
				alreadyCasting
		);

		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			ProjectJjkResonanceRemnant remnant = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
			if (!stack.is(JujutsuItems.RESONANCE_REMNANT) || remnant == null) {
				continue;
			}
			ResolvedCandidate candidate = resolveCandidate(caster, remnant, hasDoll, hasNail, alreadyCasting);
			if (candidate.validation() == ResonancePolicy.Validation.OK) {
				return new Selection(candidate.validation(), remnant);
			}
			fallback = candidate.validation();
		}
		return new Selection(fallback, null);
	}

	private static void resolveRelease(ServerPlayer caster, PendingRitual pending) {
		ResolvedCandidate candidate = resolveCandidate(
				caster,
				pending.remnant(),
				isDoll(caster.getOffhandItem()),
				hasNail(caster),
				false
		);
		if (candidate.target() == null
				|| candidate.validation() == ResonancePolicy.Validation.WRONG_DIMENSION
				|| candidate.validation() == ResonancePolicy.Validation.TARGET_INVALID) {
			emitFizzle(caster);
			caster.displayClientMessage(Component.translatable(
					"message.jujutsumod.nobara.resonance.target_lost"
			), true);
			return;
		}
		if (candidate.validation() != ResonancePolicy.Validation.OK) {
			emitFizzle(caster);
			showFailure(caster, candidate.validation());
			return;
		}
		resolveImpact(caster, candidate.target(), pending.remnant());
	}

	private static ResolvedCandidate resolveCandidate(
			ServerPlayer caster,
			ProjectJjkResonanceRemnant remnant,
			boolean hasDoll,
			boolean hasNail,
			boolean alreadyCasting
	) {
		boolean sameDimension = remnant.dimension().equals(caster.level().dimension().location());
		LivingEntity target = sameDimension ? resolveTarget(caster.level(), remnant.targetId()) : null;
		ResonancePolicy.Validation validation = ResonancePolicy.validate(
				hasDoll,
				true,
				hasNail,
				sameDimension,
				target != null,
				alreadyCasting
		);
		return new ResolvedCandidate(validation, target, sameDimension);
	}

	private static LivingEntity resolveTarget(ServerLevel level, UUID targetId) {
		Entity entity = level.getEntity(targetId);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static void resolveImpact(ServerPlayer caster, LivingEntity target, ProjectJjkResonanceRemnant remnant) {
		// This is the only resource-spend site. Every preceding timeline beat is presentation-only.
		if (!consumeResources(caster, remnant)) {
			emitFizzle(caster);
			showFailure(caster, ResonancePolicy.Validation.NO_REMNANT);
			return;
		}

		ServerLevel level = (ServerLevel) caster.level();
		long gameTime = level.getGameTime();
		boolean damaged = target.hurtServer(
				level,
				level.damageSources().indirectMagic(caster, caster),
				ProjectJjkNobaraProfile.RESONANCE_DAMAGE
		);
		if (damaged) {
			CombatStagger.GLOBAL.apply(target, gameTime, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
			ResonantMomentum.grantExecutionMoment(caster, ProjectJjkNobaraProfile.MOMENTUM_WINDOW_TICKS);
		}

		Vec3 struckOrigin = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		emitWorldCue(caster, NobaraVfxIds.RESONANCE_RELEASE, struckOrigin, RITUAL_VFX_INTENSITY);
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.nobara.resonance.complete",
				remnant.targetName()
		), true);
	}

	private static boolean mintRemnant(ServerPlayer caster, ServerLevel level, LivingEntity target, Vec3 vfxAt) {
		ProjectJjkResonanceRemnant binding = new ProjectJjkResonanceRemnant(
				target.getUUID(),
				level.dimension().location(),
				target.getDisplayName(),
				remnantVisualType(target)
		);
		ItemStack stack = new ItemStack(JujutsuItems.RESONANCE_REMNANT);
		stack.set(JujutsuDataComponents.RESONANCE_TARGET, binding);
		stack.set(JujutsuDataComponents.RESONANCE_REMNANT_VISUAL, binding.visualType());
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
				"item.jujutsumod.resonance_remnant.bound",
				binding.targetName()
		));
		if (!caster.getInventory().add(stack)) {
			ItemEntity dropped = new ItemEntity(level, caster.getX(), caster.getY() + 0.5, caster.getZ(), stack);
			dropped.setNoPickUpDelay();
			dropped.setThrower(caster);
			level.addFreshEntity(dropped);
			vfxAt = caster.position().add(0.0, 1.0, 0.0);
		}
		long gameTime = level.getGameTime();
		JujutsuNetworking.broadcastVfxCue(level, vfxAt, VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(NobaraVfxIds.REMNANT_EXTRACT, vfxAt, 1, gameTime, level.random.nextLong()));
		emitCasterAction(caster, NobaraVfxIds.CASTER_REMNANT_EXTRACT);
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.nobara.remnant.extracted",
				target.getDisplayName()
		), true);
		return true;
	}

	private static boolean hasLiveBoundRemnant(ServerPlayer caster, UUID targetId) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (isBoundRemnant(stack, targetId)) {
				return true;
			}
		}
		if (caster.level() instanceof ServerLevel level) {
			AABB search = caster.getBoundingBox().inflate(ProjectJjkNobaraProfile.TARGET_RANGE);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, search, entity -> !entity.getItem().isEmpty())) {
				if (isBoundRemnant(item.getItem(), targetId)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isBoundRemnant(ItemStack stack, UUID targetId) {
		if (!stack.is(JujutsuItems.RESONANCE_REMNANT)) {
			return false;
		}
		ProjectJjkResonanceRemnant binding = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
		return binding != null && targetId.equals(binding.targetId());
	}

	private static RemnantVisualType remnantVisualType(LivingEntity target) {
		return RemnantVisualType.classify(
				target.getType().is(ProjectJjkTags.RESONANCE_REMNANT_CURSE),
				target instanceof Animal
		);
	}

	private static boolean consumeResources(ServerPlayer player, ProjectJjkResonanceRemnant expected) {
		ItemStack nail = ItemStack.EMPTY;
		ItemStack remnant = ItemStack.EMPTY;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (nail.isEmpty() && isNail(stack)) {
				nail = stack;
			}
			if (remnant.isEmpty() && stack.is(JujutsuItems.RESONANCE_REMNANT)
					&& expected.equals(stack.get(JujutsuDataComponents.RESONANCE_TARGET))) {
				remnant = stack;
			}
		}
		if (nail.isEmpty() || remnant.isEmpty()) {
			return false;
		}
		nail.shrink(1);
		remnant.shrink(1);
		return true;
	}

	private static boolean hasNail(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (isNail(player.getInventory().getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isNail(ItemStack stack) {
		return stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL);
	}

	private static boolean isHammer(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}

	private static boolean isDoll(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL);
	}

	private static void triggerDollRitual(ServerPlayer caster) {
		if (JujutsuItems.STRAW_DOLL instanceof ProjectJjkStrawDollItem doll) {
			doll.triggerRitual(caster, caster.getOffhandItem());
		}
	}

	private static void triggerDollImpact(ServerPlayer caster) {
		if (JujutsuItems.STRAW_DOLL instanceof ProjectJjkStrawDollItem doll) {
			doll.triggerImpact(caster, caster.getOffhandItem());
		}
	}

	private static Vec3 resolveTargetOrigin(ServerPlayer caster, ProjectJjkResonanceRemnant remnant) {
		if (caster.level() instanceof ServerLevel level) {
			LivingEntity target = resolveTarget(level, remnant.targetId());
			if (target != null) {
				return target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			}
		}
		return caster.getEyePosition();
	}

	private static void emitWorldCue(ServerPlayer caster, ResourceLocation id, Vec3 origin, int intensity) {
		ServerLevel level = (ServerLevel) caster.level();
		JujutsuNetworking.broadcastVfxCue(level, origin, VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(id, origin, intensity, level.getGameTime(), level.random.nextLong()));
	}

	private static void emitCasterAction(ServerPlayer caster, int action) {
		ServerLevel level = (ServerLevel) caster.level();
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_DELIVERY_RADIUS,
				VfxCues.anchored(NobaraVfxIds.CASTER_ACTION, caster.position(), caster.getId(), caster.position(),
						action, level.getGameTime(), caster.getRandom().nextLong()));
	}

	private static void emitResonanceLink(ServerPlayer caster) {
		ServerLevel level = (ServerLevel) caster.level();
		JujutsuNetworking.sendVfxCue(caster,
				VfxCues.anchored(NobaraVfxIds.RESONANCE_LINK, caster.getEyePosition(), caster.getId(),
						caster.position(), RITUAL_VFX_INTENSITY, level.getGameTime(), caster.getRandom().nextLong()));
	}

	private static void emitFizzle(ServerPlayer caster) {
		ServerLevel level = (ServerLevel) caster.level();
		JujutsuNetworking.sendVfxCue(caster,
				VfxCues.anchored(NobaraVfxIds.RESONANCE_LINK, caster.getEyePosition(), caster.getId(),
						caster.position(), 1, level.getGameTime(), caster.getRandom().nextLong()));
	}

	private static void showFailure(ServerPlayer caster, ResonancePolicy.Validation validation) {
		String suffix = validation.name().toLowerCase(Locale.ROOT);
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.nobara.resonance." + suffix
		), true);
	}

	/** Drops the caster's pending ritual. World anchors and curse-links remain untouched. */
	public static void resetCaster(UUID casterId) {
		if (casterId != null) {
			PENDING_RITUALS.remove(casterId);
		}
	}

	private static void clearAll() {
		PENDING_RITUALS.clear();
	}

	private static VfxCue cue(
			ServerLevel level,
			ResourceLocation effectId,
			int intensity,
			Vec3 origin,
			long gameTime,
			Entity anchor
	) {
		return VfxCues.anchored(effectId, origin, anchor.getId(), anchor.position(), intensity, gameTime,
				level.random.nextLong());
	}

	private record Selection(ResonancePolicy.Validation validation, ProjectJjkResonanceRemnant remnant) {}

	private record ResolvedCandidate(
			ResonancePolicy.Validation validation,
			LivingEntity target,
			boolean sameDimension
	) {}

	private static final class PendingRitual {
		private final UUID casterId;
		private final ProjectJjkResonanceRemnant remnant;
		private final long startedAt;
		private boolean windupEmitted;
		private boolean strikeEmitted;

		private PendingRitual(UUID casterId, ProjectJjkResonanceRemnant remnant, long startedAt) {
			this.casterId = casterId;
			this.remnant = remnant;
			this.startedAt = startedAt;
		}

		private UUID casterId() { return casterId; }
		private ProjectJjkResonanceRemnant remnant() { return remnant; }
		private long startedAt() { return startedAt; }
		private boolean windupEmitted() { return windupEmitted; }
		private boolean strikeEmitted() { return strikeEmitted; }
		private void markWindupEmitted() { windupEmitted = true; }
		private void markStrikeEmitted() { strikeEmitted = true; }
	}
}
