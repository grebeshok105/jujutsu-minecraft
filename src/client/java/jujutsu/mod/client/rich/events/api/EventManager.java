package jujutsu.mod.client.rich.events.api;

/** No-op event bus for the stripped Rich GUI port. */
public final class EventManager {
	public void register(Object listener) {}

	public void unregister(Object listener) {}

	public static void callEvent(Object event) {}
}
