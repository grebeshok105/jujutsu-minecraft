package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.MegumiVfxIds;

/** Server-authoritative, scan-ticked one-recipient healing and cleansing for Round Deer. */
final class MegumiDeerBrain {
	private static final List<Holder<MobEffect>> CLEANSE_CANDIDATES = List.of(
			MobEffects.BLINDNESS,
			MobEffects.POISON,
			MobEffects.WITHER,
			MobEffects.WEAKNESS,
			MobEffects.MINING_FATIGUE,
			MobEffects.HUNGER,
			MobEffects.LEVITATION,
			MobEffects.UNLUCK,
			MobEffects.BAD_OMEN,
			MobEffects.DARKNESS,
			MobEffects.NAUSEA);

	private MegumiDeerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiDeerEntity deer, long gameTime) {
		UUID ownerId = deer.ownerUuid();
		if (owner == null || ownerId == null || !owner.isAlive()
				|| !ownerId.equals(owner.getUUID()) || !deer.combatEnabled()
				|| gameTime < deer.nextPulseGameTime()
				|| !MegumiDeerPolicy.scanDue(gameTime, deer.nextScanGameTime())) {
			return;
		}

		// Advance only the scan clock here. An empty scan must not start pulse cooldown.
		deer.scheduleNextScan(gameTime + MegumiShikigamiProfile.DEER_SCAN_TICKS);
		List<LivingEntity> owned = new ArrayList<>();
		owned.add(owner);
		for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId)) {
			owned.add(body);
		}
		owned.addAll(MegumiSummonRuntime.livingDogs(level.getServer(), ownerId));

		List<MegumiDeerPolicy.RecipientFacts> facts = new ArrayList<>(owned.size());
		Map<UUID, LivingEntity> entitiesById = new HashMap<>(owned.size());
		for (LivingEntity candidate : owned) {
			boolean isOwner = candidate == owner;
			boolean isOwnedShikigami = candidate instanceof MegumiShikigamiEntity
					|| candidate instanceof MegumiDivineDogEntity;
			boolean ownershipMatches = isOwner
					? ownerId.equals(candidate.getUUID())
					: candidate instanceof MegumiShikigamiEntity body
							? ownerId.equals(body.ownerUuid())
							: candidate instanceof MegumiDivineDogEntity dog
									&& ownerId.equals(dog.ownerUuid());
			MegumiDeerPolicy.RecipientFacts recipient = new MegumiDeerPolicy.RecipientFacts(
					candidate.getUUID(),
					candidate.isAlive(),
					candidate.isRemoved(),
					candidate.level() == level,
					ownershipMatches,
					isOwner,
					isOwnedShikigami,
					candidate.getHealth(),
					candidate.getMaxHealth(),
					deer.distanceTo(candidate));
			facts.add(recipient);
			entitiesById.put(recipient.uuid(), candidate);
		}

		MegumiDeerPolicy.RecipientFacts selected = MegumiDeerPolicy.choose(facts).orElse(null);
		if (selected == null) {
			return;
		}
		LivingEntity recipient = entitiesById.get(selected.uuid());
		if (recipient == null) {
			return;
		}
		float amount = MegumiDeerPolicy.healAmount(recipient.getHealth(), recipient.getMaxHealth(),
				MegumiShikigamiProfile.DEER_HEAL_AMOUNT);
		if (amount <= 0.0f) {
			return;
		}

		recipient.heal(amount);
		boolean cleansed = cleanse(recipient);
		deer.beginPulse(gameTime);
		level.playSound(null, recipient.getX(), recipient.getY(), recipient.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8f, 0.92f);
		Vec3 anchorOffset = new Vec3(0.0, recipient.getBbHeight() * 0.5, 0.0);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.DEER_PULSE,
				recipient.position(), recipient.getId(), anchorOffset);
		if (cleansed) {
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.DEER_CLEANSE,
					recipient.position(), recipient.getId(), anchorOffset);
		}
	}

	private static boolean cleanse(LivingEntity recipient) {
		boolean cursedFearActive = recipient.hasEffect(JujutsuEffects.CURSED_FEAR);
		boolean removedAny = false;
		for (Holder<MobEffect> effect : CLEANSE_CANDIDATES) {
			if (MegumiDeerPolicy.cleanseable(effect, cursedFearActive) && recipient.hasEffect(effect)) {
				recipient.removeEffect(effect);
				removedAny = true;
			}
		}
		return removedAny;
	}
}
