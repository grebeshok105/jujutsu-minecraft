package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.combat.CombatStagger;

public final class ProjectJjkStrawDollRuntime {
	private static final int REMNANT_HIT_THRESHOLD = 2;
	private static final int RITUAL_VFX_INTENSITY = 2;
	private static final double VFX_RADIUS = 64.0;
	private static final ProjectJjkRemnantProgress REMNANT_PROGRESS = new ProjectJjkRemnantProgress(REMNANT_HIT_THRESHOLD);
	private static final Map<UUID, PendingRitual> PENDING_RITUALS = new HashMap<>();

	private ProjectJjkStrawDollRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkStrawDollRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearCaster(handler.player.getUUID()));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity) {
				REMNANT_PROGRESS.clearTarget(entity.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			REMNANT_PROGRESS.clearTarget(entity.getUUID());
			if (entity instanceof ServerPlayer player) {
				clearCaster(player.getUUID());
			}
		});
	}

	public static void onOrdinaryNailHit(ServerLevel level, ServerPlayer caster, LivingEntity target, Vec3 wound) {
		if (caster == null || !caster.isAlive() || !target.isAlive() || caster.getUUID().equals(target.getUUID())) {
			return;
		}
		if (!REMNANT_PROGRESS.recordHit(caster.getUUID(), target.getUUID())) {
			return;
		}

		ProjectJjkResonanceRemnant binding = new ProjectJjkResonanceRemnant(
				target.getUUID(),
				level.dimension().location(),
				target.getDisplayName()
		);
		ItemStack stack = new ItemStack(JujutsuItems.RESONANCE_REMNANT);
		stack.set(JujutsuDataComponents.RESONANCE_TARGET, binding);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
				"item.jujutsumod.resonance_remnant.bound",
				binding.targetName()
		));
		Vec3 dropAt = wound == null
				? target.position().add(0.0, target.getBbHeight() * 0.55, 0.0)
				: wound;
		ItemEntity dropped = new ItemEntity(level, dropAt.x, dropAt.y, dropAt.z, stack);
		dropped.setDefaultPickUpDelay();
		level.addFreshEntity(dropped);
		JujutsuNetworking.broadcastVfxCue(level, dropAt, VFX_RADIUS,
				cue(level, NobaraVfxIds.REMNANT_DROP, 1, dropAt, level.getGameTime(), target));
	}

	public static boolean tryStart(ServerPlayer caster, ItemStack hammer, InteractionHand hand) {
		if (!isHammer(hammer) || hand != InteractionHand.MAIN_HAND) {
			showFailure(caster, ProjectJjkRitualPolicy.Validation.NO_DOLL);
			return false;
		}

		Selection selection = selectRemnant(caster, PENDING_RITUALS.containsKey(caster.getUUID()));
		if (selection.validation() != ProjectJjkRitualPolicy.Validation.OK || selection.remnant() == null) {
			showFailure(caster, selection.validation());
			return false;
		}

		long dueGameTime = caster.level().getGameTime() + NobaraActionTimeline.DOLL_STRIKE.impactTick();
		PendingRitual pending = new PendingRitual(caster.getUUID(), selection.remnant(), dueGameTime);
		PENDING_RITUALS.put(caster.getUUID(), pending);
		triggerDollRitual(caster);
		Vec3 origin = caster.getEyePosition().add(caster.getLookAngle().scale(0.45));
		JujutsuNetworking.broadcastVfxCue(caster.level(), caster.position(), VFX_RADIUS,
				cue(caster.level(), NobaraVfxIds.RITUAL_BIND, 1, origin, caster.level().getGameTime(), caster));
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.projectjjk.resonance.casting",
				selection.remnant().targetName()
		), true);
		return true;
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

			ResolvedPending resolved = resolvePending(caster, pending);
			if (resolved.validation() != ProjectJjkRitualPolicy.Validation.OK || resolved.target() == null) {
				showFailure(caster, resolved.validation());
				PENDING_RITUALS.remove(pending.casterId(), pending);
				continue;
			}
			if (caster.level().getGameTime() < pending.dueGameTime()) {
				continue;
			}

			resolveImpact(caster, resolved.target(), pending.remnant());
			PENDING_RITUALS.remove(pending.casterId(), pending);
		}
	}

	private static Selection selectRemnant(ServerPlayer caster, boolean alreadyCasting) {
		boolean hasDoll = isDoll(caster.getOffhandItem());
		boolean hasNail = hasNail(caster);
		ProjectJjkRitualPolicy.Validation fallback = ProjectJjkRitualPolicy.validate(
				hasDoll,
				false,
				hasNail,
				false,
				true,
				0.0,
				alreadyCasting
		);

		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			ProjectJjkResonanceRemnant remnant = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
			if (!stack.is(JujutsuItems.RESONANCE_REMNANT) || remnant == null) {
				continue;
			}
			ResolvedCandidate candidate = resolveCandidate(caster, remnant, hasDoll, hasNail, alreadyCasting);
			if (candidate.validation() == ProjectJjkRitualPolicy.Validation.OK) {
				return new Selection(candidate.validation(), remnant);
			}
			fallback = candidate.validation();
		}
		return new Selection(fallback, null);
	}

	private static ResolvedPending resolvePending(ServerPlayer caster, PendingRitual pending) {
		boolean hasDoll = isHammer(caster.getMainHandItem()) && isDoll(caster.getOffhandItem());
		boolean hasRemnant = hasRemnant(caster, pending.remnant());
		boolean hasNail = hasNail(caster);
		ResolvedCandidate candidate = resolveCandidate(caster, pending.remnant(), hasDoll, hasNail, false);
		ProjectJjkRitualPolicy.Validation validation = ProjectJjkRitualPolicy.validate(
				hasDoll,
				hasRemnant,
				hasNail,
				candidate.target() != null,
				candidate.sameDimension(),
				candidate.distance(),
				false
		);
		return new ResolvedPending(validation, candidate.target());
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
		double distance = target == null ? 0.0 : caster.distanceTo(target);
		ProjectJjkRitualPolicy.Validation validation = ProjectJjkRitualPolicy.validate(
				hasDoll,
				true,
				hasNail,
				target != null,
				sameDimension,
				distance,
				alreadyCasting
		);
		return new ResolvedCandidate(validation, target, sameDimension, distance);
	}

	private static LivingEntity resolveTarget(ServerLevel level, UUID targetId) {
		Entity entity = level.getEntity(targetId);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static void resolveImpact(ServerPlayer caster, LivingEntity target, ProjectJjkResonanceRemnant remnant) {
		if (!consumeResources(caster, remnant)) {
			showFailure(caster, ProjectJjkRitualPolicy.Validation.NO_REMNANT);
			return;
		}

		ServerLevel level = caster.level();
		triggerDollImpact(caster);
		long gameTime = level.getGameTime();
		target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), ProjectJjkNobaraProfile.RESONANCE_DAMAGE);
		CombatStagger.GLOBAL.apply(target, gameTime, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);

		Vec3 targetOrigin = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 dollOrigin = caster.getEyePosition().add(caster.getLookAngle().scale(0.45));
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_RADIUS,
				cue(level, NobaraVfxIds.DOLL_STRIKE, RITUAL_VFX_INTENSITY, dollOrigin, gameTime, caster));
		JujutsuNetworking.broadcastVfxCue(level, targetOrigin, VFX_RADIUS,
				cue(level, NobaraVfxIds.RESONANCE_RELEASE, RITUAL_VFX_INTENSITY, targetOrigin, gameTime, target));
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.projectjjk.resonance.complete",
				remnant.targetName()
		), true);
	}

	private static boolean hasRemnant(ServerPlayer player, ProjectJjkResonanceRemnant expected) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(JujutsuItems.RESONANCE_REMNANT)
					&& expected.equals(stack.get(JujutsuDataComponents.RESONANCE_TARGET))) {
				return true;
			}
		}
		return false;
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

	private static void showFailure(ServerPlayer caster, ProjectJjkRitualPolicy.Validation validation) {
		String suffix = validation.name().toLowerCase(Locale.ROOT);
		caster.displayClientMessage(Component.translatable(
				"message.jujutsumod.projectjjk.resonance." + suffix
		), true);
	}

	private static void clearCaster(UUID casterId) {
		PENDING_RITUALS.remove(casterId);
		REMNANT_PROGRESS.clearCaster(casterId);
	}

	private static void clearAll() {
		PENDING_RITUALS.clear();
		REMNANT_PROGRESS.clear();
	}

	private static VfxCue cue(
			ServerLevel level,
			ResourceLocation effectId,
			int intensity,
			Vec3 origin,
			long gameTime,
			Entity anchor
	) {
		return new VfxCue(
				effectId,
				origin,
				anchor.getId(),
				origin.subtract(anchor.position()),
				Math.max(1, intensity),
				gameTime,
				level.random.nextLong()
		);
	}

	private record PendingRitual(UUID casterId, ProjectJjkResonanceRemnant remnant, long dueGameTime) {}
	private record Selection(ProjectJjkRitualPolicy.Validation validation, ProjectJjkResonanceRemnant remnant) {}
	private record ResolvedCandidate(
			ProjectJjkRitualPolicy.Validation validation,
			LivingEntity target,
			boolean sameDimension,
			double distance
	) {}
	private record ResolvedPending(ProjectJjkRitualPolicy.Validation validation, LivingEntity target) {}
}
