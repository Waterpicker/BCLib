package org.betterx.bclib;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.betterx.bclib.api.v2.dataexchange.DataExchangeAPI;
import org.betterx.bclib.api.v2.dataexchange.handler.autosync.*;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;
import org.betterx.bclib.api.v2.generator.BCLibNetherBiomeSource;
import org.betterx.bclib.api.v2.generator.GeneratorOptions;
import org.betterx.bclib.api.v2.levelgen.LevelGenEvents;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeBuilder;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.api.v2.levelgen.structures.TemplatePiece;
import org.betterx.bclib.api.v2.levelgen.surface.rules.Conditions;
import org.betterx.bclib.api.v2.poi.PoiManager;
import org.betterx.bclib.api.v3.levelgen.features.blockpredicates.BlockPredicates;
import org.betterx.bclib.api.v3.levelgen.features.placement.PlacementModifiers;
import org.betterx.bclib.api.v3.tag.BCLBlockTags;
import org.betterx.bclib.commands.CommandRegistry;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.networking.VersionChecker;
import org.betterx.bclib.recipes.AnvilRecipe;
import org.betterx.bclib.recipes.CraftingRecipes;
import org.betterx.bclib.registry.BaseBlockEntities;
import org.betterx.bclib.registry.BaseRegistry;
import org.betterx.worlds.together.WorldsTogether;
import org.betterx.worlds.together.util.Logger;
import org.betterx.worlds.together.world.WorldConfig;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

import static org.betterx.bclib.BCLib.MOD_ID;

@Mod(MOD_ID)
public class BCLib {
    public static final String MOD_ID = "bclib";
    public static final Logger LOGGER = new Logger(MOD_ID);

    public static final boolean RUNS_NULLSCAPE = FabricLoader.getInstance()
                                                             .getModContainer("nullscape")
                                                             .isPresent();

    public BCLib() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        WorldsTogether.onInitialize();
        LevelGenEvents.register();
        BlockPredicates.ensureStaticInitialization();
        BCLBiomeRegistry.ensureStaticallyLoaded();
        BaseRegistry.register();
        GeneratorOptions.init();
        BaseBlockEntities.register(bus);
        BCLibEndBiomeSource.register(bus);
        BCLibNetherBiomeSource.register(bus);
        CraftingRecipes.init();
        WorldConfig.registerModCache(MOD_ID);
        DataExchangeAPI.registerMod(MOD_ID);
        AnvilRecipe.register();
        Conditions.registerAll();
        CommandRegistry.register();
        BCLBlockTags.ensureStaticallyLoaded();
        PoiManager.registerAll();

        DataExchangeAPI.registerDescriptors(List.of(
                        HelloClient.DESCRIPTOR,
                        HelloServer.DESCRIPTOR,
                        RequestFiles.DESCRIPTOR,
                        SendFiles.DESCRIPTOR,
                        Chunker.DESCRIPTOR
                )
        );

        BCLibPatch.register();
        TemplatePiece.ensureStaticInitialization();
        PlacementModifiers.ensureStaticInitialization();
        Configs.save();

        WorldsTogether.FORCE_SERVER_TO_BETTERX_PRESET = Configs.SERVER_CONFIG.forceBetterXPreset();
        VersionChecker.registerMod(MOD_ID);


        if (false && isDevEnvironment()) {
            BCLBiome theYellow = BCLBiomeBuilder
                    .start(makeID("the_yellow"))
                    .precipitation(Biome.Precipitation.NONE)
                    .temperature(1.0f)
                    .wetness(1.0f)
                    .fogColor(0xFFFF00)
                    .waterColor(0x777700)
                    .waterFogColor(0xFFFF00)
                    .skyColor(0xAAAA00)
                    .addNetherClimateParamater(-1, 1)
                    .surface(Blocks.YELLOW_CONCRETE)
                    .build();
            BiomeAPI.registerEndLandBiome(theYellow);

            BCLBiome theBlue = BCLBiomeBuilder
                    .start(makeID("the_blue"))
                    .precipitation(Biome.Precipitation.NONE)
                    .temperature(1.0f)
                    .wetness(1.0f)
                    .fogColor(0x0000FF)
                    .waterColor(0x000077)
                    .waterFogColor(0x0000FF)
                    .skyColor(0x0000AA)
                    .addNetherClimateParamater(-1, 1)
                    .surface(Blocks.LIGHT_BLUE_CONCRETE)
                    .build();
            BiomeAPI.registerEndLandBiome(theBlue);

            BCLBiome theGray = BCLBiomeBuilder
                    .start(makeID("the_gray"))
                    .precipitation(Biome.Precipitation.NONE)
                    .temperature(1.0f)
                    .wetness(1.0f)
                    .fogColor(0xFFFFFF)
                    .waterColor(0x777777)
                    .waterFogColor(0xFFFFFF)
                    .skyColor(0xAAAAAA)
                    .addNetherClimateParamater(-1, 1)
                    .surface(Blocks.GRAY_CONCRETE)
                    .build();
            BiomeAPI.registerEndVoidBiome(theGray);

            BCLBiome theOrange = BCLBiomeBuilder
                    .start(makeID("the_orange"))
                    .precipitation(Biome.Precipitation.NONE)
                    .temperature(1.0f)
                    .wetness(1.0f)
                    .fogColor(0xFF7700)
                    .waterColor(0x773300)
                    .waterFogColor(0xFF7700)
                    .skyColor(0xAA7700)
                    .addNetherClimateParamater(-1, 1.1f)
                    .surface(Blocks.ORANGE_CONCRETE)
                    .build();
            BiomeAPI.registerNetherBiome(theOrange);

            BCLBiome thePurple = BCLBiomeBuilder
                    .start(makeID("the_purple"))
                    .precipitation(Biome.Precipitation.NONE)
                    .temperature(1.0f)
                    .wetness(1.0f)
                    .fogColor(0xFF00FF)
                    .waterColor(0x770077)
                    .waterFogColor(0xFF00FF)
                    .skyColor(0xAA00AA)
                    .addNetherClimateParamater(-1.1f, 1)
                    .surface(Blocks.PURPLE_CONCRETE)
                    .build();
            BiomeAPI.registerNetherBiome(thePurple);
        }
    }

    public static boolean isDevEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static ResourceLocation makeID(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
