package jujutsu.mod.debug;

public final class HairpinDebugLogTest {
	private HairpinDebugLogTest() {}

	public static void main(String[] args) {
		HairpinDebugLog.setEnabled(false);
		assert !HairpinDebugLog.isEnabled();
		HairpinDebugLog.setEnabled(true);
		assert HairpinDebugLog.isEnabled();
		HairpinDebugLog.setEnabled(false);
		assert !HairpinDebugLog.isEnabled();
		System.out.println("HairpinDebugLogTest passed");
	}
}
