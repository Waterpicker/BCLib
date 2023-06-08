package org.betterx.ui.layout.components;

import org.betterx.ui.layout.components.input.RelativeContainerEventHandler;
import org.betterx.ui.layout.components.render.ComponentRenderer;
import org.betterx.ui.layout.components.render.RenderHelper;
import org.betterx.ui.layout.values.Alignment;
import org.betterx.ui.layout.values.Rectangle;
import org.betterx.ui.layout.values.Value;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.events.GuiEventListener;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class Container extends LayoutComponent<Container.ContainerRenderer, Container> implements RelativeContainerEventHandler {
    public static class ContainerRenderer implements ComponentRenderer {
        Container linkedContainer;

        @Override
        public void renderInBounds(
                PoseStack stack,
                int mouseX,
                int mouseY,
                float deltaTicks,
                Rectangle bounds,
                Rectangle clipRect
        ) {
            if (linkedContainer != null) {
                if ((linkedContainer.backgroundColor & 0xFF000000) != 0)
                    GuiComponent.fill(stack, 0, 0, bounds.width, bounds.height, linkedContainer.backgroundColor);

                if ((linkedContainer.outlineColor & 0xFF000000) != 0)
                    RenderHelper.outline(stack, 0, 0, bounds.width, bounds.height, linkedContainer.outlineColor);
            }
        }
    }

    record Positional(int left, int top, LayoutComponent<?, ?> component) {
        public int getMaxX() {
            return left + component().getContentWidth();
        }

        public int getMaxY() {
            return top + component().getContentHeight();
        }
    }

    private final List<Positional> children = new LinkedList<>();
    boolean dragging = false;
    GuiEventListener focused = null;
    boolean visible = true;

    int paddingLeft, paddingTop, paddingRight, paddingBottom;

    int backgroundColor = 0;
    int outlineColor = 0;

    public Container(
            Value width,
            Value height
    ) {
        super(width, height, new ContainerRenderer());
        renderer.linkedContainer = this;
    }

    public Container addChild(LayoutComponent<?, ?> child) {
        children.add(new Positional(0, 0, child));
        return this;
    }

    public Container addChild(int left, int top, LayoutComponent<?, ?> child) {
        children.add(new Positional(left, top, child));
        return this;
    }

    public Container setVisible(boolean v) {
        this.visible = v;
        return this;
    }

    public boolean getVisible() {
        return this.visible;
    }

    public Container setBackgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public Container setOutlineColor(int color) {
        this.outlineColor = color;
        return this;
    }

    public int getOutlineColor() {
        return outlineColor;
    }

    public Container setPadding(int padding) {
        return setPadding(padding, padding, padding, padding);
    }

    public Container setPadding(int left, int top, int right, int bottom) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        return this;
    }

    @Override
    public int getContentWidth() {
        return children.stream()
                       .map(Positional::getMaxX)
                       .reduce(0, Math::max) + paddingLeft + paddingRight;
    }

    @Override
    public int getContentHeight() {
        return children.stream()
                       .map(Positional::getMaxY)
                       .reduce(0, Math::max) + paddingTop + paddingBottom;
    }

    @Override
    public Rectangle getInputBounds() {
        return relativeBounds;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children.stream().map(p -> p.component).toList();
    }

    @Override
    public boolean isDragging() {
        return dragging;
    }

    @Override
    public void setDragging(boolean bl) {
        dragging = bl;
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return focused;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener guiEventListener) {
        focused = guiEventListener;
    }

    @Override
    protected int updateContainerWidth(int containerWidth) {
        int myWidth = width.calculateOrFill(containerWidth);
        for (var child : children) {
            child.component.width.calculateOrFill(myWidth - (paddingLeft + paddingRight));
            child.component.updateContainerWidth(child.component.width.calculatedSize());
        }
        return myWidth;
    }

    @Override
    protected int updateContainerHeight(int containerHeight) {
        int myHeight = height.calculateOrFill(containerHeight);
        for (var child : children) {
            child.component.height.calculateOrFill(myHeight - (paddingTop + paddingBottom));
            child.component.updateContainerHeight(child.component.height.calculatedSize());
        }
        return myHeight;
    }

    @Override
    protected void renderInBounds(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float deltaTicks,
            Rectangle renderBounds,
            Rectangle clipRect
    ) {
        if (visible) {
            super.renderInBounds(poseStack, mouseX, mouseY, deltaTicks, renderBounds, clipRect);

            setClippingRect(clipRect);
            for (var child : children) {
                child.component.render(
                        poseStack, mouseX, mouseY, deltaTicks,
                        renderBounds, clipRect
                );
            }
            setClippingRect(null);
        }
    }

    @Override
    void setRelativeBounds(int left, int top) {
        super.setRelativeBounds(left, top);

        for (var child : children) {
            int childLeft = (relativeBounds.width - (paddingLeft + paddingRight)) - child.component.width.calculatedSize();
            if (child.component.hAlign == Alignment.MIN) childLeft = 0;
            else if (child.component.hAlign == Alignment.CENTER) childLeft /= 2;

            int childTop = (relativeBounds.height - (paddingTop + paddingBottom)) - child.component.height.calculatedSize();
            if (child.component.vAlign == Alignment.MIN) childTop = 0;
            else if (child.component.vAlign == Alignment.CENTER) childTop /= 2;

            child.component.setRelativeBounds(child.left + paddingLeft + childLeft, child.top + paddingTop + childTop);
        }
    }

    @Override
    public void updateScreenBounds(int worldX, int worldY) {
        super.updateScreenBounds(worldX, worldY);
        for (Positional p : children) {
            p.component.updateScreenBounds(p.left + screenBounds.left, p.top + screenBounds.top);
        }
    }

    @Override
    public void mouseMoved(double d, double e) {
        if (visible)
            RelativeContainerEventHandler.super.mouseMoved(d, e);
    }

    @Override
    public boolean mouseClicked(double d, double e, int i) {
        if (visible)
            return RelativeContainerEventHandler.super.mouseClicked(d, e, i);
        return false;
    }

    @Override
    public boolean mouseReleased(double d, double e, int i) {
        if (visible)
            return RelativeContainerEventHandler.super.mouseReleased(d, e, i);
        return false;
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f) {
        if (visible)
            return RelativeContainerEventHandler.super.mouseScrolled(d, e, f);
        return false;
    }

    @Override
    public boolean mouseDragged(double d, double e, int i, double f, double g) {
        if (visible)
            return RelativeContainerEventHandler.super.mouseDragged(d, e, i, f, g);
        return false;
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        if (visible) {
            boolean res = false;
            for (var child : children) {
                res |= child.component.isMouseOver(x, y);
            }

            return res || relativeBounds.contains(x, y);
        }
        return false;
    }

    public static Container create(LayoutComponent<?, ?> content) {
        return create(Value.fit(), Value.fit(), content);
    }

    public static Container create(Value width, Value height, LayoutComponent<?, ?> content) {
        Container c = new Container(width, height);
        c.addChild(content);
        return c;
    }
}
