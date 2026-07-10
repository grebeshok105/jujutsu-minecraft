package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.curse.CurseLink;
import jujutsu.mod.curse.CurseLinkRegistry;
import jujutsu.mod.curse.CurseLinkSelection;
import jujutsu.mod.network.CurseLinkOptionsPayload;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

public final class SelfResonanceRuntime {
	private static final Map<UUID, UUID> SELECTED = new HashMap<>();
	private SelfResonanceRuntime() {}

	public static boolean tryCast(ServerPlayer player) {
		List<CurseLink> links = CurseLinkRegistry.GLOBAL.linksForParticipant(player.getUUID());
		CurseLinkSelection selection = CurseLinkSelection.resolve(links, SELECTED.get(player.getUUID()));
		if (selection.status() == CurseLinkSelection.Status.NEEDS_SELECTION || selection.status() == CurseLinkSelection.Status.INVALID_SELECTION) {
			SELECTED.remove(player.getUUID());
			if (ServerPlayNetworking.canSend(player, CurseLinkOptionsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new CurseLinkOptionsPayload(links.stream().map(link -> new CurseLinkOptionsPayload.Entry(link.id(), link.sourceId(), link.techniqueId())).toList()));
			}
			return true;
		}
		if (selection.status() != CurseLinkSelection.Status.READY || selection.link() == null) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.self_resonance.no_link"), true);
			return false;
		}
		CurseLink link = selection.link();
		if (!link.participants().contains(player.getUUID())) { SELECTED.remove(player.getUUID()); return false; }
		Vec3Cue.emitCaster(player);
		player.hurtServer(player.level(), player.level().damageSources().magic(), ProjectJjkNobaraProfile.SELF_RESONANCE_SELF_DAMAGE);
		for (UUID participant : link.participants()) {
			if (participant.equals(player.getUUID())) continue;
			Entity entity = player.level().getEntity(participant);
			if (entity instanceof LivingEntity living && living.isAlive()) {
				living.hurtServer(player.level(), NobaraDamageSources.hairpin(player.level(), player), ProjectJjkNobaraProfile.SELF_RESONANCE_LINKED_DAMAGE);
				CombatStagger.GLOBAL.apply(living, player.level().getGameTime(), ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
				Vec3Cue.emitTarget(player, living);
			}
		}
		SELECTED.remove(player.getUUID());
		return true;
	}

	public static boolean select(ServerPlayer player, UUID linkId) {
		CurseLink link = CurseLinkRegistry.GLOBAL.get(linkId);
		if (link == null || !link.participants().contains(player.getUUID()) || CurseLinkRegistry.GLOBAL.linksForParticipant(player.getUUID()).size() < 2) return false;
		SELECTED.put(player.getUUID(), linkId);
		player.displayClientMessage(Component.translatable("message.jujutsumod.nobara.self_resonance.selected", link.techniqueId().toString()), true);
		return true;
	}

	private static final class Vec3Cue {
		private static void emitCaster(ServerPlayer caster) {
			var at = caster.getEyePosition();
			JujutsuNetworking.broadcastVfxCue(caster.level(), caster.position(), 64.0, new VfxCue(NobaraVfxIds.SELF_RESONANCE, at, caster.getId(), at.subtract(caster.position()), 2, caster.level().getGameTime(), caster.getRandom().nextLong()));
		}
		private static void emitTarget(ServerPlayer caster, LivingEntity target) {
			var at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			JujutsuNetworking.broadcastVfxCue(caster.level(), at, 64.0, new VfxCue(NobaraVfxIds.RESONANCE_RELEASE, at, target.getId(), at.subtract(target.position()), 2, caster.level().getGameTime(), caster.getRandom().nextLong()));
		}
	}
}
