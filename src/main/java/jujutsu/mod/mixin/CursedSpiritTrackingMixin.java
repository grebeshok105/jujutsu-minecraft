package jujutsu.mod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * Issue #80, tracking filter (Step 7): a curse subject is never tracked by a non-perceiving
 * player — no spawn packet, no entity events, no client-side body to push against.
 *
 * <p>Why a mixin: {@code EntityTrackingEvents} are void notifications fired after the spawn
 * packet, so no Fabric event can cancel tracking (probe 1a). The injection mirrors what the
 * vanilla method itself does on the untracked path — {@code removePlayer} drops the connection
 * from {@code seenBy} and sends the remove packet only if it was tracked — then cancels before
 * vanilla can re-add. The reverse transition needs no code: once this stops cancelling, the
 * vanilla path re-adds and re-sends the spawn packet within its normal tick.
 *
 * <p>Method descriptor (probe 1a, javap-verified on 1.21.8):
 * {@code updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V}.
 *
 * <p>The target is a <em>string</em>, not a class literal: {@code ChunkMap$TrackedEntity} is
 * package-private in 1.21.8, so naming it in Java would not compile. Mixin resolves the name at
 * load time and fails loudly if it ever moves.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class CursedSpiritTrackingMixin {
	@Shadow
	@Final
	private Entity entity;

	@Shadow
	public abstract void removePlayer(ServerPlayer player);

	@Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$hideSubjectFromNonPerceiver(ServerPlayer player, CallbackInfo ci) {
		if (player != null && entity != null && entity != player
				&& CursePerception.isSubject(entity) && !CursePerception.perceives(player)) {
			removePlayer(player);
			ci.cancel();
		}
	}
}
