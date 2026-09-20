package jujutsu.mcpdev;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Synthetic input for the dev lane (issue #109): the upstream client tools are read-only, so a
 * scripted run could never press, hold or release a key — and the quick selector's whole contract
 * is a hold gesture. This tool injects the same events GLFW would: a press is
 * {@code KeyMapping.click} + {@code KeyMapping.set(key, true)}, a release is
 * {@code KeyMapping.set(key, false)}, and a held key is re-asserted every client tick so it
 * survives the {@code KeyMapping.setAll()} a screen open performs.
 *
 * <p>Actions:
 * <ul>
 *   <li>{@code tap} — press and release inside one client tick (the sub-tick path);</li>
 *   <li>{@code press} / {@code release} — manual edges for scripted sequences;</li>
 *   <li>{@code hold} — press, keep down for {@code ticks} client ticks, release; the call returns
 *       after the release lands;</li>
 *   <li>{@code click} — dispatch {@code mouseClicked} to the current screen at scaled GUI
 *       coordinates (the strip's slot rects are GUI-space).</li>
 * </ul>
 *
 * <p>Integrated client only: the tool drives {@link Minecraft} in the same JVM. On a dedicated
 * server the client classes are absent and the tool fails fast instead of crashing the runtime.
 */
@McpTool(
		name = "jujutsu_input",
		description = "Injects synthetic client input: tap/press/release/hold a keybind by name, or click the current screen")
public final class JujutsuInputTool extends BaseTool {

	private static final int MIN_HOLD_TICKS = 1;
	private static final int MAX_HOLD_TICKS = 200;
	private static final long TICK_MS = 50L;
	private static final long TIMEOUT_MARGIN_MS = 10_000L;
	/** The selector keybind's translation key; overridable via the {@code key} argument. */
	private static final String DEFAULT_KEY = "key.jujutsumod.quick_selector";

	private static final JsonNode SCHEMA = Schemas.object()
			.required("action", Schemas.string("tap | press | release | hold | click"))
			.optional("key", Schemas.string("KeyMapping name; default " + DEFAULT_KEY))
			.optional("ticks", Schemas.integerBetween("hold length in client ticks", MIN_HOLD_TICKS, MAX_HOLD_TICKS))
			.optional("x", Schemas.number("click: scaled GUI x"))
			.optional("y", Schemas.number("click: scaled GUI y"))
			.optional("button", Schemas.integerBetween("click: mouse button (0=left)", 0, 7))
			.build();

	/** The key currently held down by this tool, or null. Re-asserted every client tick. */
	private static volatile KeyMapping heldKey;
	/** Client ticks remaining on a scripted hold; -1 when no hold is running. */
	private static volatile int holdTicksLeft = -1;
	/** Completed when a scripted hold's release lands. */
	private static volatile CompletableFuture<Void> holdDone;
	private static volatile boolean tickerRegistered;

	public JujutsuInputTool() {
		super("jujutsu_input");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		String action = r.requireString("action");
		String keyName = r.optString("key", DEFAULT_KEY);
		Minecraft client = clientOrFail();

		ObjectNode node = context.mapper().createObjectNode();
		switch (action) {
			case "tap" -> {
				// A one-tick hold: the press is seen down for exactly one client tick, then the
				// ticker releases it — under the 4-tick threshold, so the gesture resolves as a tap.
				KeyMapping mapping = requireMapping(keyName);
				ensureTicker();
				CompletableFuture<Void> done = new CompletableFuture<>();
				holdDone = done;
				await(client, () -> {
					mapping.setDown(true);
					heldKey = mapping;
					holdTicksLeft = 1;
				});
				awaitHold(done, 1);
				node.put("action", "tap").put("key", keyName);
			}
			case "press" -> {
				KeyMapping mapping = requireMapping(keyName);
				ensureTicker();
				await(client, () -> {
					mapping.setDown(true);
					heldKey = mapping;
				});
				node.put("action", "press").put("key", keyName);
			}
			case "release" -> {
				KeyMapping mapping = requireMapping(keyName);
				await(client, () -> {
					heldKey = null;
					holdTicksLeft = -1;
					mapping.setDown(false);
				});
				node.put("action", "release").put("key", keyName);
			}
			case "hold" -> {
				int ticks = r.optInt("ticks", 6);
				if (ticks < MIN_HOLD_TICKS || ticks > MAX_HOLD_TICKS) {
					throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
							"ticks must be between " + MIN_HOLD_TICKS + " and " + MAX_HOLD_TICKS);
				}
				KeyMapping mapping = requireMapping(keyName);
				ensureTicker();
				CompletableFuture<Void> done = new CompletableFuture<>();
				holdDone = done;
				await(client, () -> {
					mapping.setDown(true);
					heldKey = mapping;
					holdTicksLeft = ticks;
				});
				awaitHold(done, ticks);
				node.put("action", "hold").put("key", keyName).put("ticks", ticks);
			}
			case "click" -> {
				double x = r.optDouble("x", Double.NaN);
				double y = r.optDouble("y", Double.NaN);
				int button = r.optInt("button", 0);
				if (Double.isNaN(x) || Double.isNaN(y)) {
					throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "click needs x and y (scaled GUI coords)");
				}
				await(client, () -> {
					if (client.screen != null) {
						client.screen.mouseClicked(x, y, button);
						client.screen.mouseReleased(x, y, button);
					}
				});
				node.put("action", "click").put("x", x).put("y", y).put("button", button)
						.put("screen", client.screen == null ? "none" : client.screen.getClass().getSimpleName());
			}
			case "close_screen" -> {
				await(client, () -> {
					if (client.screen != null) {
						client.screen.onClose();
					}
				});
				node.put("action", "close_screen");
			}
			case "dump" -> {
				KeyMapping mapping = requireMapping(keyName);
				await(client, () -> {
					node.put("isDown", mapping.isDown())
							.put("unbound", mapping.isUnbound())
							.put("isDefault", mapping.isDefault())
							.put("saveString", mapping.saveString())
							.put("screen", client.screen == null ? "none" : client.screen.getClass().getSimpleName())
							.put("player", client.player != null);
					// clickCount is private; consumeClick() is the only reader, and it drains — so
					// probe non-destructively by counting how many consumeClick() calls return true,
					// then restoring them via click().
					int pending = 0;
					while (mapping.consumeClick()) {
						pending++;
					}
					for (int i = 0; i < pending; i++) {
						KeyMapping.click(mappingKey(mapping));
					}
					node.put("clickCount", pending);
					// Does the static MAP lookup resolve this mapping's key? If not, click()/set()
					// silently no-op and only setDown() works.
					KeyMapping.set(mappingKey(mapping), true);
					node.put("staticSetWorks", mapping.isDown());
					mapping.setDown(false);
				});
			}
			default -> throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"unknown action '" + action + "' (tap|press|release|hold|click|close_screen|dump)");
		}
		return ToolResult.ofToon(node);
	}

	/** The client singleton, or a clean tool error on a dedicated server. */
	private static Minecraft clientOrFail() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR, "no integrated client");
			}
			return client;
		} catch (NoClassDefFoundError e) {
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"jujutsu_input needs the integrated client (singleplayer/dev lane)", e);
		}
	}

	private static KeyMapping requireMapping(String name) {
		KeyMapping mapping = KeyMapping.get(name);
		if (mapping == null) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "no keybind named '" + name + "'");
		}
		return mapping;
	}

	private static com.mojang.blaze3d.platform.InputConstants.Key mappingKey(KeyMapping mapping) {
		// The bound key is private; saveString() is the public accessor ("key.keyboard.g" form).
		// getKey() alone is not enough: KeyMapping.MAP is keyed by the canonical per-type instance,
		// so re-resolve through the type's own factory or set()/click() silently no-op.
		com.mojang.blaze3d.platform.InputConstants.Key parsed =
				com.mojang.blaze3d.platform.InputConstants.getKey(mapping.saveString());
		return parsed.getType().getOrCreate(parsed.getValue());
	}

	/** Runs {@code body} on the client thread and waits for it. */
	private static void await(Minecraft client, Runnable body) {
		CompletableFuture<Void> done = new CompletableFuture<>();
		client.execute(() -> {
			try {
				body.run();
				done.complete(null);
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		});
		try {
			done.get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new McpException(ErrorCodes.MAIN_THREAD_TIMEOUT, "jujutsu_input: client thread never ran the action", e);
		}
	}

	/** Parks the HTTP thread until the scripted hold's release lands on the client thread. */
	private static void awaitHold(CompletableFuture<Void> done, int ticks) {
		try {
			done.get(ticks * TICK_MS + TIMEOUT_MARGIN_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			throw new McpException(ErrorCodes.MAIN_THREAD_TIMEOUT,
					"jujutsu_input hold timed out after " + ticks + " ticks");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR, "jujutsu_input interrupted", e);
		} catch (ExecutionException e) {
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"jujutsu_input hold failed: " + e.getCause(), e.getCause());
		}
	}

	/** Registers the per-tick hold keeper once. */
	private static void ensureTicker() {
		if (tickerRegistered) {
			return;
		}
		tickerRegistered = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeyMapping held = heldKey;
			if (held == null) {
				return;
			}
			// A screen open runs KeyMapping.setAll() which re-asserts only physically held keys —
			// a synthetic hold would read as released next tick without this re-assert.
			held.setDown(true);
			if (holdTicksLeft > 0 && --holdTicksLeft == 0) {
				heldKey = null;
				held.setDown(false);
				CompletableFuture<Void> done = holdDone;
				if (done != null) {
					done.complete(null);
				}
			}
		});
	}
}
