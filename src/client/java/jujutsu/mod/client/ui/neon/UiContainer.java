package jujutsu.mod.client.ui.neon;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;

public class UiContainer extends UiComponent {
    protected final List<UiComponent> children = new ArrayList<>();

    public UiContainer add(UiComponent child) {
        children.add(child);
        child.setParent(this);
        return this;
    }

    public List<UiComponent> children() { return children; }

    public void layout() {
        for (UiComponent child : children) {
            if (child instanceof UiContainer c) c.layout();
        }
    }

    @Override
    public void tick(float deltaTicks) {
        super.tick(deltaTicks);
        for (UiComponent child : children) {
            if (child.isVisible()) child.tick(deltaTicks);
        }
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!visible) return;
        for (UiComponent child : children) {
            if (child.isVisible()) child.renderSurface(ctx);
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!visible) return;
        for (UiComponent child : children) {
            if (child.isVisible()) child.renderText(ctx);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Open dropdowns win hit-testing even when the popup covers later siblings.
        if (dispatchOpenDropdownClick(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiComponent child = children.get(i);
            if (child.isVisible() && child.contains(mouseX, mouseY) && child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchOpenDropdownClick(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UiComponent child = children.get(i);
            if (!child.isVisible()) continue;
            if (child instanceof NeonDropdown d && d.isOpen()) {
                if (d.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            } else if (child instanceof UiContainer c && c.dispatchOpenDropdownClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UiComponent child = children.get(i);
            if (child.isVisible() && child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UiComponent child = children.get(i);
            if (child.isVisible() && child.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    protected UiComponent childAt(double mx, double my) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UiComponent child = children.get(i);
            if (child.isVisible() && child.contains(mx, my)) return child;
        }
        return null;
    }
}
