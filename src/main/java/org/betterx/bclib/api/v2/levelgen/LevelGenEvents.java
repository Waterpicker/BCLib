package org.betterx.bclib.api.v2.levelgen;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.LifeCycleAPI;
import org.betterx.bclib.api.v2.dataexchange.DataExchangeAPI;
import org.betterx.bclib.api.v2.datafixer.DataFixerAPI;
import org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource;
import org.betterx.bclib.api.v2.generator.config.BCLEndBiomeSourceConfig;
import org.betterx.bclib.api.v2.levelgen.biomes.InternalBiomeAPI;
import org.betterx.bclib.api.v2.poi.PoiManager;
import org.betterx.bclib.registry.PresetsRegistry;
import org.betterx.worlds.together.tag.v3.TagManager;
import org.betterx.worlds.together.world.WorldConfig;
import org.betterx.worlds.together.world.event.WorldEvents;
import org.betterx.worlds.together.worldPreset.TogetherWorldPreset;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class LevelGenEvents {
    public static void setupWorld() {
        InternalBiomeAPI.prepareNewLevel();
        DataExchangeAPI.prepareServerside();
    }

    public static void register() {
        WorldEvents.BEFORE_WORLD_LOAD.on(LevelGenEvents::prepareWorld);
        WorldEvents.BEFORE_SERVER_WORLD_LOAD.on(LevelGenEvents::prepareServerWorld);

        WorldEvents.ON_WORLD_LOAD.on(LevelGenEvents::onWorldLoad);
        WorldEvents.WORLD_REGISTRY_READY.on(LevelGenEvents::onRegistryReady);
        WorldEvents.ON_FINALIZE_LEVEL_STEM.on(LevelGenEvents::finalizeStem);
        WorldEvents.ON_FINALIZED_WORLD_LOAD.on(LevelGenEvents::finalizedWorldLoad);

        WorldEvents.PATCH_WORLD.on(LevelGenEvents::patchExistingWorld);
        WorldEvents.ADAPT_WORLD_PRESET.on(LevelGenEvents::adaptWorldPresetSettings);

        WorldEvents.BEFORE_ADDING_TAGS.on(LevelGenEvents::appplyTags);
    }


    private static void appplyTags(
            String directory,
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> tagsMap
    ) {
        if (directory.equals(TagManager.BIOMES.directory)) {
            InternalBiomeAPI._runBiomeTagAdders();
        }
    }


    private static boolean patchExistingWorld(
            LevelStorageSource.LevelStorageAccess storageAccess,
            Consumer<Boolean> allDone
    ) {
        return DataFixerAPI.fixData(storageAccess, true, allDone);
    }

    private static Optional<Holder<WorldPreset>> adaptWorldPresetSettings(
            Optional<Holder<WorldPreset>> currentPreset,
            WorldGenSettings worldGenSettings
    ) {
        LevelStem endStem = worldGenSettings.dimensions().get(LevelStem.END);

        //We probably loaded a Datapack for the End
        if (!(endStem.generator().getBiomeSource() instanceof BCLibEndBiomeSource)) {
            if (currentPreset.isPresent()) {
                if (currentPreset.get().value() instanceof TogetherWorldPreset worldPreset) {
                    ResourceKey worldPresetKey = currentPreset.get().unwrapKey().orElse(null);

                    //user did not configure/change the Preset!
                    if (PresetsRegistry.BCL_WORLD.equals(worldPresetKey)
                            || PresetsRegistry.BCL_WORLD_17.equals(worldPresetKey)) {
                        BCLib.LOGGER.info("Detected Datapack for END.");

                        LevelStem configuredEndStem = worldPreset.getDimension(LevelStem.END);
                        if (configuredEndStem.generator().getBiomeSource() instanceof BCLibEndBiomeSource endSource) {
                            BCLib.LOGGER.info("Changing Default WorldPreset Settings for Datapack use.");

                            BCLEndBiomeSourceConfig inputConfig = endSource.getTogetherConfig();
                            endSource.setTogetherConfig(new BCLEndBiomeSourceConfig(
                                    inputConfig.mapVersion,
                                    BCLEndBiomeSourceConfig.EndBiomeGeneratorType.VANILLA,
                                    false,
                                    inputConfig.innerVoidRadiusSquared,
                                    inputConfig.centerBiomesSize,
                                    inputConfig.voidBiomesSize,
                                    inputConfig.landBiomesSize,
                                    inputConfig.barrensBiomesSize
                            ));
                        }
                    }
                }
            }
        }
        return currentPreset;
    }

    private static void onRegistryReady(RegistryAccess a) {
        InternalBiomeAPI.initRegistry(a);
    }

    private static void prepareWorld(
            LevelStorageSource.LevelStorageAccess storageAccess,
            Map<ResourceKey<LevelStem>, ChunkGenerator> dimensions,
            boolean isNewWorld
    ) {
        setupWorld();
        if (isNewWorld) {
            WorldConfig.saveFile(BCLib.MOD_ID);
            DataFixerAPI.initializePatchData();
        } else {
            LevelGenUtil.migrateGeneratorSettings();
        }
    }

    private static void prepareServerWorld(
            LevelStorageSource.LevelStorageAccess storageAccess,
            Map<ResourceKey<LevelStem>, ChunkGenerator> dimensions,
            boolean isNewWorld
    ) {
        setupWorld();

        if (isNewWorld) {
            WorldConfig.saveFile(BCLib.MOD_ID);
            DataFixerAPI.initializePatchData();
        } else {
            LevelGenUtil.migrateGeneratorSettings();
            DataFixerAPI.fixData(storageAccess, false, (didFix) -> {/* not called when showUI==false */});
        }
    }

    private static void onWorldLoad() {
        LifeCycleAPI._runBeforeLevelLoad();
    }

    private static void finalizeStem(
            WorldGenSettings settings,
            ResourceKey<LevelStem> dimension,
            LevelStem levelStem
    ) {
        InternalBiomeAPI.applyModifications(levelStem.generator().getBiomeSource(), dimension);
    }

    private static void finalizedWorldLoad(WorldGenSettings worldGenSettings) {
        PoiManager.updateStates();
    }
}
