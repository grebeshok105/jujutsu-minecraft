package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.curse.CurseLink;
import jujutsu.mod.curse.CurseLinkRegistry;
import jujutsu.mod.curse.CurseLinkSelection;
import jujutsu.mod.network.CurseLinkOptionsPayload;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCues;

public final class SelfResonanceRuntime {
	private static final Double VFX_DELIVERY_RADIUS = 64.0;
	private static final Map<UUID, UUID> SELECTED = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private SelfResonanceRuntime() {}
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SelfResonanceRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> { SELECTED.clear(); PENDING.clear(); });
	}

	public static AbilityResult tryCast(ServerPlayer player) {
		if (PENDING.containsKey(player.getUUID())) return AbilityResult.UNHANDLED_FAILURE;
		List<CurseLink> links = CurseLinkRegistry.GLOBAL.linksForParticipant(player.getUUID());
		CurseLinkSelection selection = CurseLinkSelection.resolve(links, SELECTED.get(player.getUUID()));
		if (selection.status() == CurseLinkSelection.Status.NEEDS_SELECTION || selection.status() == CurseLinkSelection.Status.INVALID_SELECTION) {
			SELECTED.remove(player.getUUID());
			if (ServerPlayNetworking.canSend(player, CurseLinkOptionsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new CurseLinkOptionsPayload(links.stream().map(link -> new CurseLinkOptionsPayload.Entry(link.id(), link.sourceId(), link.techniqueId())).toList()));
			}
			// The picker opened: the input was consumed and the player got a UI, so this is not a failure.
			return AbilityResult.SUCCESS;
		}
		if (selection.status() != CurseLinkSelection.Status.READY || selection.link() == null) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.self_resonance.no_link"), true);
			return AbilityResult.HANDLED_FAILURE;
		}
		CurseLink link = selection.link();
		if (!link.participants().contains(player.getUUID())) { SELECTED.remove(player.getUUID()); return AbilityResult.UNHANDLED_FAILURE; }
		Vec3Cue.emitCaster(player);
		PENDING.put(player.getUUID(), new Pending(link.id(), player.level().getGameTime() + NobaraActionTimeline.SELF_RESONANCE.impactTick()));
		SELECTED.remove(player.getUUID());
		return AbilityResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		for (var entry : List.copyOf(PENDING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) { PENDING.remove(entry.getKey()); continue; }
			if (player.level().getGameTime() < entry.getValue().dueGameTime()) continue;
			CurseLink link = CurseLinkRegistry.GLOBAL.get(entry.getValue().linkId());
			if (link != null && link.participants().contains(player.getUUID())) resolveImpact(player, link);
			PENDING.remove(entry.getKey());
		}
	}

	private static boolean resolveImpact(ServerPlayer player, CurseLink link) {
		if (!player.hurtServer(player.level(), NobaraDamageSources.selfResonance(player.level(), player), ProjectJjkNobaraProfile.SELF_RESONANCE_SELF_DAMAGE)) return false;
		for (UUID participant : link.participants()) {
			if (participant.equals(player.getUUID())) continue;
			Entity entity = player.level().getEntity(participant);
			if (entity instanceof LivingEntity living && living.isAlive()) {
				living.hurtServer(player.level(), NobaraDamageSources.selfResonance(player.level(), player), ProjectJjkNobaraProfile.SELF_RESONANCE_LINKED_DAMAGE);
				CombatStagger.GLOBAL.apply(living, player.level().getGameTime(), ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
				Vec3Cue.emitTarget(player, living);
			}
		}
		return true;
	}

	public static boolean select(ServerPlayer player, UUID linkId) {
		CurseLink link = CurseLinkRegistry.GLOBAL.get(linkId);
		if (link == null || !link.participants().contains(player.getUUID()) || CurseLinkRegistry.GLOBAL.linksForParticipant(player.getUUID()).size() < 2) return false;
		SELECTED.put(player.getUUID(), linkId);
		player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.self_resonance.selected", link.techniqueId().toString()), true);
		return true;
	}

	/** Drops the player's resonance selection and any pending impact. Exists for the dev control surface + gametests. */
	public static void clearCaster(UUID casterId) {
		SELECTED.remove(casterId);
		PENDING.remove(casterId);
	}

	private static final class Vec3Cue {
		private static void emitCaster(ServerPlayer caster) {
			var at = caster.getEyePosition();
			JujutsuNetworking.broadcastVfxCue(caster.level(), caster.position(), VFX_DELIVERY_RADIUS,
					VfxCues.anchored(NobaraVfxIds.SELF_RESONANCE, at, caster.getId(), caster.position(), 2,
							caster.level().getGameTime(), caster.getRandom().nextLong()));
		}
		private static void emitTarget(ServerPlayer caster, LivingEntity target) {
			var at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			JujutsuNetworking.broadcastVfxCue(caster.level(), at, VFX_DELIVERY_RADIUS,
					VfxCues.anchored(NobaraVfxIds.RESONANCE_RELEASE, at, target.getId(), target.position(), 2,
							caster.level().getGameTime(), caster.getRandom().nextLong()));
		}
	}
	private record Pending(UUID linkId, long dueGameTime) {}
}
