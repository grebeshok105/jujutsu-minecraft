package jujutsu.mod.character.todo;

import net.minecraft.world.phys.Vec3;

/** Verifies that unsafe two-party preflight never creates a partial swap plan. */
public final class TodoSwapPlanTest {
	private TodoSwapPlanTest() {}

	public static void main(String[] args) {
		Vec3 todoDestination = new Vec3(3.0, 70.0, 3.0);
		Vec3 targetDestination = new Vec3(-2.0, 70.0, -2.0);
		assert TodoSwapPlan.preflight(todoDestination, targetDestination).isPresent()
				: "Both safe destinations must produce a committable plan";
		assert TodoSwapPlan.preflight(todoDestination, null).isEmpty()
				: "A missing target destination must cancel the entire swap";
		assert TodoSwapPlan.preflight(null, targetDestination).isEmpty()
				: "A missing Todo destination must cancel the entire swap";
		assert TodoSwapPlan.preflight(null, null).isEmpty()
				: "Two unsafe destinations must never create a partial plan";
		System.out.println("TodoSwapPlanTest passed");
	}
}
