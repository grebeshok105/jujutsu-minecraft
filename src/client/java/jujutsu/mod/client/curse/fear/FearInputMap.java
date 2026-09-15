package jujutsu.mod.client.curse.fear;

import java.util.Map;

/**
 * Pure fear key remap (Block 3, Step 7). Input is a raw GLFW key code as delivered to
 * {@code KeyboardHandler.keyPress}; output is the code the game acts on.
 *
 * <p>Matrix (input → layer → behaviour under fear):
 *
 * <table>
 * <tr><th>In</th><th>Layer</th><th>Under fear</th></tr>
 * <tr><td>W (87) / S (83)</td><td>KeyMapping.set → movement</td><td>swapped: forward reads as back</td></tr>
 * <tr><td>A (65) / D (68)</td><td>KeyMapping.set → movement</td><td>swapped: strafe mirrored</td></tr>
 * <tr><td>Space (32) / Shift (340, 344)</td><td>KeyMapping.set → jump/sneak</td><td>swapped both ways; both Shifts pair with Space</td></tr>
 * <tr><td>1..9 (49..57)</td><td>KeyMapping.click → hotbar</td><td>mirrored: 1↔9, 2↔8, 3↔7, 4↔6, 5 stays</td></tr>
 * <tr><td>F (82) / Q (81)</td><td>KeyMapping.click → swap hands / drop</td><td>swapped</td></tr>
 * <tr><td>text (charTyped path)</td><td>EditBox / chat</td><td>never remapped: chat, anvils and signs keep working</td></tr>
 * <tr><td>mouse cursor (mouseMoved)</td><td>Screen hover/drag</td><td>never remapped: hit-testing must stay under the hand</td></tr>
 * <tr><td>mouse buttons/wheel</td><td>Screen.mouseClicked/mouseScrolled</td><td>remapped (LMB↔RMB, wheel sign): GUI chaos is the design</td></tr>
 * <tr><td>everything else</td><td>—</td><td>identity: inventory, Esc, debug keys untouched</td></tr>
 * </table>
 *
 * <p>The map is an involution on every remapped pair except Shift (both Shifts map onto
 * Space's pair; Space maps back onto left Shift). Codes are raw GLFW values — a stable ABI
 * that never changes between Minecraft versions. Rebound keys are out of scope: fear
 * mirrors physical positions, not bindings.
 */
public final class FearInputMap {
	public static final int KEY_W = 87;
	public static final int KEY_A = 65;
	public static final int KEY_S = 83;
	public static final int KEY_D = 68;
	public static final int KEY_SPACE = 32;
	public static final int KEY_LEFT_SHIFT = 340;
	public static final int KEY_RIGHT_SHIFT = 344;
	public static final int KEY_Q = 81;
	public static final int KEY_F = 82;

	private static final Map<Integer, Integer> REMAP = Map.ofEntries(
			Map.entry(KEY_W, KEY_S),
			Map.entry(KEY_S, KEY_W),
			Map.entry(KEY_A, KEY_D),
			Map.entry(KEY_D, KEY_A),
			Map.entry(KEY_SPACE, KEY_LEFT_SHIFT),
			Map.entry(KEY_LEFT_SHIFT, KEY_SPACE),
			Map.entry(KEY_RIGHT_SHIFT, KEY_SPACE),
			Map.entry(49, 57),
			Map.entry(57, 49),
			Map.entry(50, 56),
			Map.entry(56, 50),
			Map.entry(51, 55),
			Map.entry(55, 51),
			Map.entry(52, 54),
			Map.entry(54, 52),
			Map.entry(KEY_Q, KEY_F),
			Map.entry(KEY_F, KEY_Q));

	private FearInputMap() {
	}

	/** Remapped code, or the input unchanged when fear leaves that key alone. */
	public static int remapKey(int glfwKey) {
		return REMAP.getOrDefault(glfwKey, glfwKey);
	}

	/**
	 * Mouse look delta under fear: negated while the inversion is active, the input
	 * unchanged otherwise. Applies to the yaw/pitch deltas handed to
	 * {@code LocalPlayer.turn(DD)V} — both axes mirror together.
	 */
	public static double lookDelta(double delta, boolean fearActive) {
		return fearActive ? -delta : delta;
	}
}
