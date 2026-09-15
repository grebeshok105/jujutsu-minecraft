package jujutsu.mod.client.render.cursedspirit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * Issue #80, client render gate (Step 10): a curse subject renders for perceivers only.
 *
 * <p>The rule keys on {@link CursePerception#isSubject}, never on {@code instanceof} — future
 * curse projectiles and zones inherit it through their own renderers without gate changes. The
 * server stays authoritative (tracking filter, damage gates); this only decides what the local
 * client draws. Fail-open on a missing local player.
 */
public final class CurseRenderGate {
	private CurseRenderGate() {}

	public static boolean shouldRender(Entity entity) {
		return shouldRender(entity != null && CursePerception.isSubject(entity));
	}

	public static boolean shouldRender(boolean isCurseSubject) {
		if (!isCurseSubject) {
			return true;
		}
		return shouldRender(isCurseSubject, Minecraft.getInstance().player);
	}

	/**
	 * Inverse of {@link #shouldRender(boolean)} for the dispatcher-side leak paths:
	 * the entity shadow and the F3+B hitbox are drawn by {@code EntityRenderDispatcher}
	 * past a renderer's early return, so they need a positive "hidden" decision, not a
	 * skipped draw. Same single rule — a curse subject a non-perceiver must not see.
	 */
	public static boolean hiddenFromView(Entity entity) {
		return !shouldRender(entity);
	}

	public static boolean hiddenFromView(boolean isCurseSubject) {
		return !shouldRender(isCurseSubject);
	}

	/**
	 * Testable seam: a missing local viewer fails open, otherwise
	 * {@link CursePerception#perceives} decides. The client entry above only resolves
	 * the viewer; this overload owns the rule and unit-tests without a client.
	 */
	static boolean shouldRender(boolean isCurseSubject, Player viewer) {
		if (!isCurseSubject) {
			return true;
		}
		return viewer == null || CursePerception.perceives(viewer);
	}
}
