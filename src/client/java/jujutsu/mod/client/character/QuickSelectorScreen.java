package jujutsu.mod.client.character;

/**
 * Marker for the vessel quick-selector overlay screen (issue #109).
 *
 * <p>Shared code that needs to know "is the open screen a quick selector" (the debug toggle, the
 * keybind handler) tests this interface instead of naming a vessel's screen class — the seam rule is
 * that shared code asks the vessel, never which character it is.
 */
public interface QuickSelectorScreen {
}
