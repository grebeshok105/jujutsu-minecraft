package jujutsu.mod.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.character.CharacterCombatModifiers;

public final class CombatStagger {
	public static final CombatStagger GLOBAL = new CombatStagger();
	private final Map<UUID, Long> until = new ConcurrentHashMap<>();

	public void apply(UUID entityId, long gameTime, int ticks) {
		if (entityId != null && ticks > 0) until.merge(entityId, gameTime + ticks, Math::max);
	}

	public void apply(LivingEntity entity, long gameTime, int ticks) {
		apply(entity.getUUID(), gameTime, CharacterCombatModifiers.adjustedStaggerTicks(entity, ticks));
		entity.stopUsingItem();
		entity.setDeltaMovement(entity.getDeltaMovement().scale(0.15));
		hasImpulse(entity);
	}

	public boolean isStaggered(UUID entityId, long gameTime) {
		Long end = until.get(entityId);
		if (end == null) return false;
		if (gameTime >= end) {
			until.remove(entityId, end);
			return false;
		}
		return true;
	}

	private static void hasImpulse(LivingEntity entity) {
		entity.hurtMarked = true;
	}
}
