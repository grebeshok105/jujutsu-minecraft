package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Issue #80 render gate: non-subjects always draw, an absent local viewer fails open, and
 * the subject branch defers to {@code perceives} (source-pinned — a headless unit run has
 * no viewer to consult). Both curse renderers start their pass at the gate.
 */
final class CurseRenderGateTest {
	private static final Path GATE =
			Path.of("src/client/java/jujutsu/mod/client/render/cursedspirit/CurseRenderGate.java");
	private static final Path SPIRIT_RENDERER =
			Path.of("src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritRenderer.java");
	private static final Path ACID_RENDERER = Path.of(
			"src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritAcidSpitRenderer.java");

	@Test
	void nonSubjectAlwaysRenders() {
		assertTrue(CurseRenderGate.shouldRender(false));
		assertTrue(CurseRenderGate.shouldRender(false, null));
	}

	@Test
	void subjectWithAbsentViewerFailsOpen() {
		assertTrue(CurseRenderGate.shouldRender(true, null));
	}

	@Test
	void subjectBranchConsultsPerceives() throws Exception {
		String src = Files.readString(GATE);
		String body = src.substring(src.indexOf("shouldRender(boolean isCurseSubject,"));
		assertTrue(body.contains("viewer == null"), "fail-open on an absent viewer");
		assertTrue(body.contains("CursePerception.perceives(viewer)"),
				"subject branch defers to perceives");
	}

	@Test
	void spiritRendererStartsWithGate() throws Exception {
		assertTrue(firstStatement(Files.readString(SPIRIT_RENDERER)).contains(
				"CurseRenderGate.shouldRender"), "render must open with the gate");
	}

	@Test
	void acidSpitRendererStartsWithGate() throws Exception {
		String src = Files.readString(ACID_RENDERER);
		assertTrue(src.contains("state.curseSubject = CursePerception.isSubject(entity)"),
				"acid state carries the subject bit");
		assertTrue(firstStatement(src).contains("CurseRenderGate.shouldRender"),
				"acid render must open with the gate");
	}

	/** First non-comment statement of the file's render method. */
	private static String firstStatement(String src) {
		int body = src.indexOf('{', src.indexOf("public void render("));
		StringBuilder statement = new StringBuilder();
		boolean lineComment = false;
		boolean blockComment = false;
		for (int i = body + 1; i < src.length(); i++) {
			char ch = src.charAt(i);
			if (lineComment) {
				if (ch == '\n') {
					lineComment = false;
				}
				continue;
			}
			if (blockComment) {
				if (ch == '*' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
					blockComment = false;
					i++;
				}
				continue;
			}
			if (ch == '/' && i + 1 < src.length()) {
				if (src.charAt(i + 1) == '/') {
					lineComment = true;
					i++;
					continue;
				}
				if (src.charAt(i + 1) == '*') {
					blockComment = true;
					i++;
					continue;
				}
			}
			if (!Character.isWhitespace(ch)) {
				statement.append(ch);
				if (ch == ';' || ch == '}') {
					break;
				}
			}
		}
		return statement.toString();
	}
}
