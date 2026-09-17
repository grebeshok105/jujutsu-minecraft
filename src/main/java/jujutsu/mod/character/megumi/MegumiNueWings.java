package jujutsu.mod.character.megumi;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Nue's partial wings (issue #108): the {@code MEGUMI_NUE_WINGS} marker grants vanilla fall-flying
 * (glide physics, inertia, dive-conversion — all of §23 for free) and vetoes fall damage for as
 * long as it lasts.
 *
 * <p><b>Why CUSTOM and never ALLOW.</b> {@code EntityElytraEvents.ALLOW} is a veto: fabric feeds
 * every living entity through it and a single {@code false} blocks elytra flight, so registering it
 * would silently disable vanilla elytra for every player in the world. {@code CUSTOM} only ever
 * <em>grants</em> the glide to carriers; non-carriers fall through to the vanilla equipment check.
 *
 * <p><b>Nothing here starts the glide.</b> The event only permits it — the flag's sole setter is
 * {@code Player.startFallFlying()}, which the partial runtime re-asserts every tick while the wings
 * are active and the owner is airborne.
 */
public final class MegumiNueWings {
	private MegumiNueWings() {}

	public static void register() {
		EntityElytraEvents.CUSTOM.register((entity, tickElytra) -> entity.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MegumiNueWings::allowDamage);
	}

	/**
	 * D15: fall damage is absent strictly while the wings are out. The marker is removed by the
	 * partial runtime on landing and on every teardown path, so a manual mid-air cancel restores
	 * ordinary fall damage from the next damage check on.
	 */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS) && source.is(DamageTypeTags.IS_FALL));
	}

	/** The unfold beat at the owner: one anchored cue, broadcast on the same radius as every Megumi cue. */
	public static void playUnfoldCue(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ServerLevel level = player.level();
		MegumiShikigamiRuntime.broadcastCue(level, player, MegumiVfxIds.NUE_PARTIAL_WINGS,
				player.position(), player.getId(), Vec3.ZERO);
	}
}
