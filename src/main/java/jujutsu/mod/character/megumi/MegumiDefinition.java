package jujutsu.mod.character.megumi;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.cursedspirit.perception.PerceptionFlags;
import jujutsu.mod.registry.JujutsuEntities;

/** Megumi's server definition; gameplay hooks are added with the Divine Dog runtime. */
public final class MegumiDefinition implements CharacterDefinition {
	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.MEGUMI;
	}

	@Override
	public PerceptionFlags cursePerception() {
		return PerceptionFlags.PERCEIVER;
	}

	@Override
	public AbilityResult tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		return MegumiAbilityRouter.tryCast(player, slot, notify);
	}

	@Override
	public void registerServerHooks() {
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_DIVINE_DOG,
				createDivineDogAttributes());
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_NUE,
				MegumiNueEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_TOAD,
				MegumiToadEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_MAX_ELEPHANT,
				MegumiElephantEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(JujutsuEntities.MEGUMI_RABBIT,
				MegumiRabbitEntity.createAttributes());
		MegumiSummonRuntime.register();
		MegumiShikigamiRuntime.register();
		// The coordinator runs after both pack runtimes: it reads the marks their reconcile and
		// retaliation passes settled this tick, so registration order is load-bearing.
		MegumiPackCoordinator.register();
		MegumiPartialRuntime.register();
		MegumiNueWings.register();
		MegumiShadowTrapRuntime.register();
		MegumiShadowMoveRuntime.register();
		MegumiShadowDropRuntime.register();
	}

	static AttributeSupplier.Builder createDivineDogAttributes() {
		return Wolf.createAttributes()
				.add(Attributes.MAX_HEALTH, MegumiProfile.DOG_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiProfile.DOG_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiProfile.DOG_MOVEMENT_SPEED);
	}

	@Override
	public boolean selectShikigami(ServerPlayer player, String shikigamiId) {
		MegumiShikigami type;
		try {
			type = MegumiShikigami.byId(shikigamiId);
		} catch (IllegalArgumentException unknown) {
			// The client sent an id this roster does not have. Refuse rather than guess: the strip's
			// optimistic marker is corrected by the next snapshot.
			return false;
		}
		// A cooling type is not selectable. Refusing here — and not by ignoring the packet — is what
		// keeps the server the only authority on availability; the client's own click gate is a mirror.
		if (MegumiShikigamiCooldowns.isCooling(player.getUUID(), type, player.level().getGameTime())) {
			return false;
		}
		// Selection is free and non-destructive: it never starts a cooldown and never sweeps a pack,
		// which is exactly why an already-summoned type stays selectable (design spec: "summoned" is a
		// marker, not a block, and the summoned set is a separate concept from the active one).
		// A repeat of the current selection is a no-op for the client: skipping the push keeps a
		// spammed click from echoing a full snapshot back for nothing.
		if (MegumiShikigamiSelection.selected(player.getUUID()) == type) {
			return true;
		}
		MegumiShikigamiSelection.set(player.getUUID(), type);
		MegumiShikigamiSync.push(player);
		return true;
	}

	@Override
	public void onSelected(ServerPlayer player) {
		MegumiShikigamiSync.push(player);
	}

	@Override
	public void onDeselected(ServerPlayer player, JujutsuCharacter incoming) {
		MegumiSummonRuntime.teardown(player.getServer(), player.getUUID(),
				MegumiSummonRuntime.TeardownReason.DESELECTED);
		MegumiShikigamiRuntime.teardown(player.getServer(), player.getUUID(),
				MegumiShikigamiRuntime.TeardownReason.DESELECTED);
		MegumiShadowTrapRuntime.clear(player.getServer(), player.getUUID(), true);
		MegumiShadowDropRuntime.clear(player.getServer(), player.getUUID(), true);
		MegumiShadowMoveRuntime.teardown(player.getServer(), player.getUUID());
		MegumiPartialRuntime.teardown(player.getServer(), player.getUUID());
		// The teardown above charges the swept type its own cooldown. The roster ledger is dropped only
		// on a real vessel change — the roster's clean slate. Re-confirming Megumi keeps it, so the
		// selector still shows the teardown-armed cooldowns instead of lying READY while the shared
		// PRIMARY (which survives a re-confirm, issue #84) would refuse the summon anyway.
		if (incoming != JujutsuCharacter.MEGUMI) {
			MegumiSummonCooldowns.clear(player.getUUID());
			MegumiShikigamiCooldowns.clear(player.getUUID());
		}
	}
}
