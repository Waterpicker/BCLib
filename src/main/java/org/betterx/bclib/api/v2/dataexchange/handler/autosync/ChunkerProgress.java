package org.betterx.bclib.api.v2.dataexchange.handler.autosync;

import org.betterx.bclib.client.gui.screens.ProgressScreen;

import net.minecraft.util.ProgressListener;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@OnlyIn(Dist.CLIENT)
public class ChunkerProgress {
    private static ProgressScreen progressScreen;

    @OnlyIn(Dist.CLIENT)
    public static void setProgressScreen(ProgressScreen scr) {
        progressScreen = scr;
    }

    @OnlyIn(Dist.CLIENT)
    public static ProgressScreen getProgressScreen() {
        return progressScreen;
    }

    @OnlyIn(Dist.CLIENT)
    public static ProgressListener getProgressListener() {
        return progressScreen;
    }
}
