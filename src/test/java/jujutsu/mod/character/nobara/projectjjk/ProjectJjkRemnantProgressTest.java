package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;

public final class ProjectJjkRemnantProgressTest {
	private ProjectJjkRemnantProgressTest() {}

	public static void main(String[] args) {
		UUID casterA = UUID.randomUUID();
		UUID casterB = UUID.randomUUID();
		UUID targetA = UUID.randomUUID();
		UUID targetB = UUID.randomUUID();
		ProjectJjkRemnantProgress progress = new ProjectJjkRemnantProgress(2);

		assert !progress.recordHit(casterA, targetA) : "first hit should not emit a remnant";
		assert progress.recordHit(casterA, targetA) : "second hit should emit a remnant";
		assert !progress.recordHit(casterA, targetA) : "emission should reset that caster-target pair";
		assert !progress.recordHit(casterB, targetA) : "casters should track progress independently";
		assert !progress.recordHit(casterA, targetB) : "targets should track progress independently";

		progress.clearCaster(casterA);
		assert !progress.recordHit(casterA, targetA) : "clearCaster should remove every target for that caster";

		progress.clear();
		assert !progress.recordHit(casterA, targetA) : "clear should remove caster A progress";
		assert !progress.recordHit(casterB, targetA) : "clear should remove caster B progress";

		System.out.println("ProjectJjkRemnantProgressTest passed");
	}
}
