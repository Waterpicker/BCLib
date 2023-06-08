package org.betterx.ui.layout.components.input;

import org.betterx.ui.layout.values.Rectangle;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public interface RelativeContainerEventHandler extends ContainerEventHandler {
    Rectangle getInputBounds();

    default Optional<GuiEventListener> getChildAt(double d, double e) {
        Rectangle r = getInputBounds();
        return ContainerEventHandler.super.getChildAt(d, e);
    }

    default boolean mouseClicked(double d, double e, int i) {
        if (getFocused() != null) {
            //getFocused().mouseClicked(d, e, i);
        }
        Rectangle r = getInputBounds();
        return ContainerEventHandler.super.mouseClicked(d - r.left, e - r.top, i);
    }

    default boolean mouseReleased(double d, double e, int i) {
        Rectangle r = getInputBounds();
        return ContainerEventHandler.super.mouseReleased(d - r.left, e - r.top, i);
    }

    default boolean mouseDragged(double d, double e, int i, double f, double g) {
        Rectangle r = getInputBounds();
        return ContainerEventHandler.super.mouseDragged(d - r.left, e - r.top, i, f - r.left, g - r.top);
    }

    default boolean mouseScrolled(double d, double e, double f) {
        Rectangle r = getInputBounds();
        return ContainerEventHandler.super.mouseScrolled(d - r.left, e - r.top, f);
    }

    default boolean isMouseOver(double x, double y) {
        Rectangle r = getInputBounds();
        boolean res = false;
        for (GuiEventListener c : children()) {
            res |= c.isMouseOver(x - r.left, y - r.top);
        }

        return res || r.contains(x, y);
    }
}

