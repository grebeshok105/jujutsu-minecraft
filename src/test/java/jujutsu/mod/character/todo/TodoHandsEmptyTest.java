package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures Boogie Woogie keeps the empty-hands authority gate. */
public final class TodoHandsEmptyTest {
	private TodoHandsEmptyTest() {}

	public static void main(String[] args) throws Exception {
		Path runtime = Path.of("src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java");
		String source = Files.readString(runtime);
		assert source.contains("isEmptyHand(todo.getMainHandItem())") : "Main hand must be checked before swap";
		assert source.contains("isEmptyHand(todo.getOffhandItem())") : "Off hand must be checked before swap";
		assert source.contains("message.jujutsumod.todo.boogie.hands_full") : "Hands-full reject must notify the player";
		System.out.println("TodoHandsEmptyTest passed");
	}
}
