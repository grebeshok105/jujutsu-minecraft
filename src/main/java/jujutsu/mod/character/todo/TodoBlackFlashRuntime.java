package jujutsu.mod.character.todo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
import jujutsu.mod.combat.BlackFlashFocus;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.VfxCue;

/** Bridges Todo's vanilla melee hits into the existing Black Flash focus, damage, stagger, and VFX path. */
public final class TodoBlackFlashRuntime {
	private static final Set<UUID> APPLYING_BONUS = new HashSet<>();

	private TodoBlackFlashRuntime() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(TodoBlackFlashRuntime::afterDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> APPLYING_BONUS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> APPLYING_BONUS.clear());
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0f || APPLYING_BONUS.contains(target.getUUID())) {
			return;
		}
		Entity direct = source.getDirectEntity();
		Entity owner = source.getEntity();
		if (!(direct instanceof ServerPlayer todo) || owner != todo
				|| CharacterSelectionManager.selected(todo) != JujutsuCharacter.TODO
				|| !todo.isAlive() || todo.isSpectator()) {
			return;
		}
		if (!ForcedBlackFlash.isEnabled(todo) && todo.getRandom().nextFloat() >= TodoProfile.BLACK_FLASH_CHANCE) {
			return;
		}

		float bonus = baseDamageTaken * Math.max(0.0f, TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER - 1.0f);
		if (bonus > 0.0f) {
			APPLYING_BONUS.add(target.getUUID());
			try {
				target.hurtServer(todo.level(), JujutsuDamageSources.blackFlash(todo.level(), todo), bonus);
			} finally {
				APPLYING_BONUS.remove(target.getUUID());
			}
		}
		CombatStagger.GLOBAL.apply(target, todo.level().getGameTime(), TodoProfile.BLACK_FLASH_STAGGER_TICKS);
		Vec3 look = todo.getLookAngle();
		target.knockback(2.0, -look.x, -look.z);
		BlackFlashFocus.grant(todo);
		Vec3 origin = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
		JujutsuNetworking.broadcastVfxCue(todo.level(), origin, 64.0,
				new VfxCue(NobaraVfxIds.BLACK_FLASH, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, 2,
						todo.level().getGameTime(), todo.getRandom().nextLong(), look));
	}
}
