package jujutsu.mod.character.todo;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.combat.BlackFlashStrike;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.VfxCues;

/** Bridges Todo's vanilla melee hits into the existing Black Flash focus, damage, stagger, and VFX path. */
public final class TodoBlackFlashRuntime {
	private TodoBlackFlashRuntime() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(TodoBlackFlashRuntime::afterDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlackFlashStrike.forgetBonusHit(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BlackFlashStrike.clearBonusHits());
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0f || BlackFlashStrike.isApplyingBonus(target)) {
			return;
		}
		Entity direct = source.getDirectEntity();
		Entity owner = source.getEntity();
		if (!(direct instanceof ServerPlayer todo) || owner != todo
				|| CharacterSelectionManager.selected(todo) != JujutsuCharacter.TODO
				|| !todo.isAlive() || todo.isSpectator()) {
			return;
		}
		if (!BlackFlashStrike.rolls(todo, TodoProfile.BLACK_FLASH_CHANCE)) {
			return;
		}

		BlackFlashStrike.resolve(
				todo,
				target,
				baseDamageTaken,
				TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER,
				JujutsuDamageSources.blackFlash(todo.level(), todo),
				true,
				TodoProfile.BLACK_FLASH_STAGGER_TICKS,
				2.0);
		Vec3 origin = BlackFlashStrike.impactOrigin(target);
		JujutsuNetworking.broadcastVfxCue(todo.level(), origin, TodoProfile.BLACK_FLASH_VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDirected(NobaraVfxIds.BLACK_FLASH, origin, 2,
						todo.level().getGameTime(), todo.getRandom().nextLong(), todo.getLookAngle()));
	}
}
