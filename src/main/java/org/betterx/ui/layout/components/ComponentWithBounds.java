package org.betterx.ui.layout.components;

import org.betterx.ui.layout.values.Rectangle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@OnlyIn(Dist.CLIENT)
public interface ComponentWithBounds {
    Rectangle getRelativeBounds();
}
