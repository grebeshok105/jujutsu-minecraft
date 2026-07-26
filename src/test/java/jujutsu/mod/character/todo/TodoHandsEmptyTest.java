package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures every Todo clap keeps the empty-hands authority gate, through the one shared gate. */
public final class TodoHandsEmptyTest {
	private TodoHandsEmptyTest() {}

	public static void main(String[] args) throws Exception {
		String gates = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoSwapGates.java"));
		assert gates.contains("isEmptyHand(todo.getMainHandItem())") : "Main hand must be checked before a clap";
		assert gates.contains("isEmptyHand(todo.getOffhandItem())") : "Off hand must be checked before a clap";
		// Both casts must read the same gate, or the feint becomes distinguishable by what it refuses.
		for (String runtime : new String[] {"TodoBoogieWoogieRuntime", "TodoFakeClapRuntime"}) {
			String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/" + runtime + ".java"));
			assert source.contains("TodoSwapGates.evaluate(todo)")
					: runtime + " must gate on the shared clap gate instead of its own copy of the checks";
			assert source.contains("message.jujutsumod.todo.boogie.hands_full")
					: runtime + " hands-full reject must notify the player";
			assert !source.contains("getMainHandItem()") && !source.contains("getOffhandItem()")
					: runtime + " must not re-check hands locally; the shared gate owns that policy";
		}
		System.out.println("TodoHandsEmptyTest passed");
	}
}
