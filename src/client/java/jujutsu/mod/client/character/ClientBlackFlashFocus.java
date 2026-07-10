package jujutsu.mod.client.character;

public final class ClientBlackFlashFocus {
	private static boolean focused;
	private ClientBlackFlashFocus() {}
	public static boolean focused() { return focused; }
	public static void apply(boolean value) { focused = value; }
	public static void clear() { focused = false; }
}
