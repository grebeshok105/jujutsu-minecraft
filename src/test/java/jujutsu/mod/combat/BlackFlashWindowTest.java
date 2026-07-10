package jujutsu.mod.combat;

import java.util.UUID;

public final class BlackFlashWindowTest {
	private BlackFlashWindowTest() {}

	public static void main(String[] args) {
		UUID target = UUID.randomUUID();
		BlackFlashWindow window = new BlackFlashWindow(target, BlackFlashImpact.HAMMER, 100L, 102L, 8.0f);
		assert !window.accepts(99L);
		assert window.accepts(100L);
		assert window.accepts(102L);
		assert !window.accepts(103L);
		assert window.bonusDamage(1.75f) == 6.0f;
		assert window.consume(101L);
		assert !window.consume(101L) : "one impact can produce at most one Black Flash";
		CombatStagger stagger = new CombatStagger();
		stagger.apply(target, 50L, 8);
		assert stagger.isStaggered(target, 57L);
		assert !stagger.isStaggered(target, 58L);
		System.out.println("BlackFlashWindowTest passed");
	}
}
