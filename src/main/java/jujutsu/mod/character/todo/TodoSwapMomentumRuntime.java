package jujutsu.mod.character.todo;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.BlackFlashStrike;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * The window a landed Boogie Woogie opens on Todo's next hit.
 *
 * <p>The damage is not applied here. It rides on the effect's own ATTACK_DAMAGE modifier, so the vanilla
 * swing is simply bigger and there is no second damage instance to double-count, pierce invulnerability
 * or fight with Black Flash's own bonus hit. This class only decides when the window closes.
 *
 * <p><b>Known and deliberate:</b> a sweeping attack keeps the boosted damage on its later victims even
 * after the window is spent on the first. {@code Player.attack} captures the damage into a local before
 * the sweep block runs, so removing the modifier mid-swing cannot shrink a float already on the stack.
 * The stagger and the cue do not duplicate, because the effect is gone by the time later victims arrive.
 * It also costs a deliberate hotbar swap: sweeping needs a sword, and both hands must be empty to clap.
 * Fixing it means a mixin into {@code Player.attack} or abandoning the attribute for a re-entrant bonus
 * hit, and both are more expensive than the leak.
 */
public final class TodoSwapMomentumRuntime {
	private TodoSwapMomentumRuntime() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(TodoSwapMomentumRuntime::afterDamage);
		// AFTER_DAMAGE is not fired when the damage kills. Without this a killing blow would silently
		// refund the window and show nothing, so the ability would look broken exactly when it worked best.
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(TodoSwapMomentumRuntime::afterKill);
	}

	/** Opens the window. Re-granting refreshes rather than stacks, because the amplifier never changes. */
	static void grant(ServerPlayer todo) {
		boolean granted = todo.addEffect(new MobEffectInstance(
				JujutsuEffects.TODO_SWAP_MOMENTUM,
				TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS,
				0,
				false,
				false,
				true));
		if (!granted) {
			// canBeAffected can veto. Say so rather than pretending the swap paid out.
			JujutsuMod.LOGGER.debug("Todo swap momentum refused player={}", todo.getGameProfile().getName());
		}
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		ServerPlayer todo = meleeAttacker(source);
		if (todo == null) {
			return;
		}
		TodoSwapMomentum.Spend spend = TodoSwapMomentum.decide(
				blocked,
				damageTaken,
				BlackFlashStrike.isApplyingBonus(target),
				source.getDirectEntity() == todo,
				CharacterSelectionManager.selected(todo) == JujutsuCharacter.TODO,
				todo.isAlive() && !todo.isSpectator(),
				todo.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM));
		if (spend == TodoSwapMomentum.Spend.SPEND) {
			consume(todo, target, true);
		}
	}

	private static void afterKill(ServerLevel level, Entity killer, LivingEntity victim) {
		if (!(killer instanceof ServerPlayer todo)
				|| CharacterSelectionManager.selected(todo) != JujutsuCharacter.TODO
				|| !todo.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM)) {
			return;
		}
		// The window was spent, so it is spent. No stagger: there is nobody left to interrupt.
		consume(todo, victim, false);
	}

	/**
	 * The single spend path for both entries.
	 *
	 * <p>The stagger is guarded on liveness because a Black Flash bonus hit can kill the target inside the
	 * same swing, leaving this holding a corpse. Staggering one writes an entry keyed to a UUID nothing
	 * will ever query again.
	 */
	private static void consume(ServerPlayer todo, LivingEntity target, boolean applyStagger) {
		todo.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
		if (applyStagger && target.isAlive()) {
			CombatStagger.GLOBAL.apply(target, todo.level().getGameTime(), TodoProfile.SWAP_MOMENTUM_STAGGER_TICKS);
		}
		Vec3 origin = BlackFlashStrike.impactOrigin(target);
		JujutsuNetworking.broadcastVfxCue(todo.level(), origin, TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS,
				new VfxCue(TodoVfxIds.MOMENTUM_STRIKE, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, 1,
						todo.level().getGameTime(), todo.getRandom().nextLong(), todo.getLookAngle()));
		JujutsuMod.LOGGER.debug("Todo swap momentum spent player={} target={}",
				todo.getGameProfile().getName(), target.getName().getString());
	}

	/** The player whose own melee this was, or null when the damage came from anything else. */
	private static ServerPlayer meleeAttacker(DamageSource source) {
		return source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player ? player : null;
	}
}
