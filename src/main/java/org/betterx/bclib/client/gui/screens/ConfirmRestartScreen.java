package org.betterx.bclib.client.gui.screens;

import org.betterx.ui.layout.components.LayoutComponent;
import org.betterx.ui.layout.components.VerticalStack;
import org.betterx.ui.layout.values.Value;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;


@OnlyIn(Dist.CLIENT)
public class ConfirmRestartScreen extends BCLibLayoutScreen {
    private final Component description;
    private final ConfirmRestartScreen.Listener listener;

    public ConfirmRestartScreen(ConfirmRestartScreen.Listener listener) {
        this(listener, null);
    }

    public ConfirmRestartScreen(ConfirmRestartScreen.Listener listener, Component message) {
        super(Component.translatable("title.bclib.confirmrestart"));

        this.description = message == null ? Component.translatable("message.bclib.confirmrestart") : message;
        this.listener = listener;
    }

    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected LayoutComponent<?, ?> initContent() {
        VerticalStack grid = new VerticalStack(fill(), fill());
        grid.addFiller();
        grid.addMultilineText(Value.relative(0.9), fit(), this.description).centerHorizontal();
        grid.addSpacer(10);
        grid.addButton(fit(), fit(), CommonComponents.GUI_PROCEED)
            .onPress((button) -> listener.proceed())
            .centerHorizontal();
        grid.addFiller();

        return grid;
    }

    @OnlyIn(Dist.CLIENT)
    public interface Listener {
        void proceed();
    }
}
