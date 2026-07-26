package jujutsu.mod.character.todo;

/** Ensures swaps reject every transport state that could create rider or leash desync. */
public final class TodoTargetSafetyTest {
	private TodoTargetSafetyTest() {}

	public static void main(String[] args) {
		assert !TodoTargetSafety.hasUnsafeTransportState(false, false, false)
				: "An independent living target must remain eligible";
		assert TodoTargetSafety.hasUnsafeTransportState(true, false, false)
				: "Passengers must not be moved independently";
		assert TodoTargetSafety.hasUnsafeTransportState(false, true, false)
				: "Vehicles with riders must not be moved independently";
		assert TodoTargetSafety.hasUnsafeTransportState(false, false, true)
				: "Leashed entities must not be moved independently";
		assert TodoTargetSafety.hasUnsafeTransportState(true, true, true)
				: "Combined transport state must remain rejected";
		System.out.println("TodoTargetSafetyTest passed");
	}
}
