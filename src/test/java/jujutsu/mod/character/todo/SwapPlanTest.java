package jujutsu.mod.character.todo;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Pure all-or-nothing preflight checks for the unified node plan. */
public final class SwapPlanTest {
	private SwapPlanTest() {}

	public static void main(String[] args) {
		Vec3 first = new Vec3(3.0, 70.0, 3.0);
		Vec3 second = new Vec3(-2.0, 70.0, -2.0);
		SwapMove firstMove = new SwapMove(null, first);
		SwapMove secondMove = new SwapMove(null, second);
		assert SwapPlan.preflight(List.of(firstMove, secondMove)).isPresent()
				: "two known destinations must produce a plan";
		assert SwapPlan.preflight(List.of(new SwapMove(null, first), new SwapMove(null, null))).isEmpty()
				: "one missing destination must cancel the whole plan";
		assert SwapPlan.preflight(List.of(new SwapMove(null, null), new SwapMove(null, second))).isEmpty()
				: "a missing first destination must cancel the whole plan";
		assert SwapPlan.preflight(List.of(new SwapMove(null, null), new SwapMove(null, null))).isEmpty()
				: "two missing destinations must never create a partial plan";
		System.out.println("SwapPlanTest passed");
	}
}

// Red: before the rework, `TodoSwapPlan.preflight(null, second)` was the only preflight API;
// the command `./gradlew.bat testTodoSwapPlan` failed once the old plan was removed.
// Run: ./gradlew.bat testTodoSwapPlan
// Expected: SwapPlanTest passed
