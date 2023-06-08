package org.betterx.bclib.api.v2.generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.config.BCLEndBiomeSourceConfig;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiome;
import org.betterx.bclib.api.v2.levelgen.biomes.BCLBiomeRegistry;
import org.betterx.bclib.api.v2.levelgen.biomes.BiomeAPI;
import org.betterx.bclib.api.v2.levelgen.biomes.InternalBiomeAPI;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.interfaces.BiomeMap;
import org.betterx.worlds.together.biomesource.BiomeSourceWithConfig;
import org.betterx.worlds.together.biomesource.ReloadableBiomeSource;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BCLibEndBiomeSource extends BCLBiomeSource implements BiomeSourceWithConfig<BCLibEndBiomeSource, BCLEndBiomeSourceConfig>, ReloadableBiomeSource {
    public static Codec<BCLibEndBiomeSource> CODEC
            = RecordCodecBuilder.create((instance) -> instance.group(
                                                                      RegistryOps
                                                                              .retrieveRegistry(Registry.BIOME_REGISTRY)
                                                                              .forGetter((theEndBiomeSource) -> theEndBiomeSource.biomeRegistry),
                                                                      Codec
                                                                              .LONG
                                                                              .fieldOf("seed")
                                                                              .stable()
                                                                              .forGetter(source -> source.currentSeed),
                                                                      BCLEndBiomeSourceConfig
                                                                              .CODEC
                                                                              .fieldOf("config")
                                                                              .orElse(BCLEndBiomeSourceConfig.DEFAULT)
                                                                              .forGetter(o -> o.config)
                                                              )
                                                              .apply(
                                                                      instance,
                                                                      instance.stable(BCLibEndBiomeSource::new)
                                                              )
    );
    private final Point pos;
    private BiomeMap mapLand;
    private BiomeMap mapVoid;
    private BiomeMap mapCenter;
    private BiomeMap mapBarrens;

    private BiomePicker endLandBiomePicker;
    private BiomePicker endVoidBiomePicker;
    private BiomePicker endCenterBiomePicker;
    private BiomePicker endBarrensBiomePicker;
    private List<BiomeDecider> deciders;

    private BCLEndBiomeSourceConfig config;

    public BCLibEndBiomeSource(Registry<Biome> biomeRegistry, long seed, BCLEndBiomeSourceConfig config) {
        this(biomeRegistry, seed, config, true);
    }

    public BCLibEndBiomeSource(Registry<Biome> biomeRegistry, BCLEndBiomeSourceConfig config) {
        this(biomeRegistry, 0, config, false);
    }

    private BCLibEndBiomeSource(
            Registry<Biome> biomeRegistry,
            long seed,
            BCLEndBiomeSourceConfig config,
            boolean initMaps
    ) {
        this(biomeRegistry, getBiomes(biomeRegistry), seed, config, initMaps);
    }

    private BCLibEndBiomeSource(
            Registry<Biome> biomeRegistry,
            List<Holder<Biome>> list,
            long seed,
            BCLEndBiomeSourceConfig config,
            boolean initMaps
    ) {
        super(biomeRegistry, list, seed);
        this.config = config;
        rebuildBiomePickers();

        this.pos = new Point();

        if (initMaps) {
            initMap(seed);
        }
    }

    @NotNull
    private void rebuildBiomePickers() {
        var includeMap = Configs.BIOMES_CONFIG.getBiomeIncludeMap();
        var excludeList = Configs.BIOMES_CONFIG.getExcludeMatching(BiomeAPI.BiomeType.END);

        this.deciders = BiomeDecider.DECIDERS.stream()
                                             .filter(d -> d.canProvideFor(this))
                                             .map(d -> d.createInstance(this))
                                             .toList();

        this.endLandBiomePicker = new BiomePicker(biomeRegistry);
        this.endVoidBiomePicker = new BiomePicker(biomeRegistry);
        this.endCenterBiomePicker = new BiomePicker(biomeRegistry);
        this.endBarrensBiomePicker = new BiomePicker(biomeRegistry);
        Map<BiomeAPI.BiomeType, BiomePicker> pickerMap = new HashMap<>();
        pickerMap.put(BiomeAPI.BiomeType.END_LAND, endLandBiomePicker);
        pickerMap.put(BiomeAPI.BiomeType.END_VOID, endVoidBiomePicker);
        pickerMap.put(BiomeAPI.BiomeType.END_CENTER, endCenterBiomePicker);
        pickerMap.put(BiomeAPI.BiomeType.END_BARRENS, endBarrensBiomePicker);


        this.possibleBiomes().forEach(biome -> {
            ResourceKey<Biome> key = biome.unwrapKey().orElseThrow();
            ResourceLocation biomeID = key.location();
            String biomeStr = biomeID.toString();
            //exclude everything that was listed
            if (excludeList != null && excludeList.contains(biomeStr)) return;
            if (!biome.isBound()) {
                BCLib.LOGGER.warning("Biome " + biomeStr + " is requested but not yet bound.");
                return;
            }
            final BCLBiome bclBiome;
            if (!BiomeAPI.hasBiome(biomeID)) {
                bclBiome = new BCLBiome(biomeID, biome.value(), BiomeAPI.BiomeType.END_LAND);
                InternalBiomeAPI.registerBCLBiomeData(bclBiome);
            } else {
                bclBiome = BiomeAPI.getBiome(biomeID);
            }


            if (bclBiome != null || bclBiome != BCLBiomeRegistry.EMPTY_BIOME) {
                if (bclBiome.getParentBiome() == null) {
                    //ignore small islands when void biomes are disabled
                    if (!config.withVoidBiomes) {
                        if (biomeID.equals(Biomes.SMALL_END_ISLANDS.location())) {
                            return;
                        }
                    }

                    //force include biomes
                    boolean didForceAdd = false;
                    for (var entry : pickerMap.entrySet()) {
                        var includeList = includeMap == null ? null : includeMap.get(entry.getKey());
                        if (includeList != null && includeList.contains(biomeStr)) {
                            entry.getValue().addBiome(bclBiome);
                            didForceAdd = true;
                        }
                    }

                    if (!didForceAdd) {
                        if (biomeID.equals(BCLBiomeRegistry.EMPTY_BIOME.getID())
                                || bclBiome.getIntendedType().is(BiomeAPI.BiomeType.END_IGNORE)) {
                            //we should not add this biome anywhere, so just ignore it
                        } else {
                            didForceAdd = false;
                            for (BiomeDecider decider : deciders) {
                                if (decider.addToPicker(bclBiome)) {
                                    didForceAdd = true;
                                    break;
                                }
                            }
                            if (!didForceAdd) {
                                if (bclBiome.getIntendedType().is(BiomeAPI.BiomeType.END_CENTER)
                                        || TheEndBiomesHelper.canGenerateAsMainIslandBiome(key)) {
                                    endCenterBiomePicker.addBiome(bclBiome);
                                } else if (bclBiome.getIntendedType().is(BiomeAPI.BiomeType.END_LAND)
                                        || TheEndBiomesHelper.canGenerateAsHighlandsBiome(key)) {
                                    if (!config.withVoidBiomes) endVoidBiomePicker.addBiome(bclBiome);
                                    endLandBiomePicker.addBiome(bclBiome);
                                } else if (bclBiome.getIntendedType().is(BiomeAPI.BiomeType.END_BARRENS)
                                        || TheEndBiomesHelper.canGenerateAsEndBarrens(key)) {
                                    endBarrensBiomePicker.addBiome(bclBiome);
                                } else if (bclBiome.getIntendedType().is(BiomeAPI.BiomeType.END_VOID)
                                        || TheEndBiomesHelper.canGenerateAsSmallIslandsBiome(key)) {
                                    endVoidBiomePicker.addBiome(bclBiome);
                                } else {
                                    if ( Configs.MAIN_CONFIG.verboseLogging()) {
                                        BCLib.LOGGER.info("Found End Biome " + biomeStr + " that was not registers with fabric or bclib. Assuming end-land Biome...");
                                    }
                                    endLandBiomePicker.addBiome(bclBiome);
                                }
                            }
                        }
                    }
                }
            }
        });

        endLandBiomePicker.rebuild();
        endVoidBiomePicker.rebuild();
        endBarrensBiomePicker.rebuild();
        endCenterBiomePicker.rebuild();

        for (BiomeDecider decider : deciders) {
            decider.rebuild();
        }

        if (endVoidBiomePicker.isEmpty()) {
            BCLib.LOGGER.info("No Void Biomes found. Disabling by using barrens");
            endVoidBiomePicker = endBarrensBiomePicker;
        }
        if (endBarrensBiomePicker.isEmpty()) {
            BCLib.LOGGER.info("No Barrens Biomes found. Disabling by using land Biomes");
            endBarrensBiomePicker = endLandBiomePicker;
            endVoidBiomePicker = endLandBiomePicker;
        }
        if (endCenterBiomePicker.isEmpty()) {
            BCLib.LOGGER.warning("No Center Island Biomes found. Forcing use of vanilla center.");
            endCenterBiomePicker.addBiome(BiomeAPI.THE_END);
            endCenterBiomePicker.rebuild();
            if (endCenterBiomePicker.isEmpty()) {
                BCLib.LOGGER.error("Unable to force vanilla central Island. Falling back to land Biomes...");
                endCenterBiomePicker = endLandBiomePicker;
            }
        }
    }

    protected BCLBiomeSource cloneForDatapack(Set<Holder<Biome>> datapackBiomes) {
        datapackBiomes.addAll(getNonVanillaBiomes(this.biomeRegistry));
        datapackBiomes.addAll(possibleBiomes().stream()
                                              .filter(h -> !h.unwrapKey()
                                                             .orElseThrow()
                                                             .location()
                                                             .getNamespace()
                                                             .equals("minecraft"))
                                              .toList());

        return new BCLibEndBiomeSource(
                this.biomeRegistry,
                datapackBiomes.stream()
                              .filter(b -> b.isValidInRegistry(biomeRegistry) && b.unwrapKey()
                                                                                  .orElse(null) != BCLBiomeRegistry.EMPTY_BIOME.getBiomeKey())
                              .toList(),
                this.currentSeed,
                this.config,
                true
        );
    }

    private static List<Holder<Biome>> getNonVanillaBiomes(Registry<Biome> biomeRegistry) {
        return getBiomes(
                biomeRegistry,
                Configs.BIOMES_CONFIG.getExcludeMatching(BiomeAPI.BiomeType.END),
                Configs.BIOMES_CONFIG.getIncludeMatching(BiomeAPI.BiomeType.END),
                BCLibEndBiomeSource::isValidNonVanillaEndBiome
        );
    }

    private static List<Holder<Biome>> getBiomes(Registry<Biome> biomeRegistry) {
        return getBiomes(
                biomeRegistry,
                Configs.BIOMES_CONFIG.getExcludeMatching(BiomeAPI.BiomeType.END),
                Configs.BIOMES_CONFIG.getIncludeMatching(BiomeAPI.BiomeType.END),
                BCLibEndBiomeSource::isValidEndBiome
        );
    }


    private static boolean isValidEndBiome(Holder<Biome> biome, ResourceLocation location) {
        if (BiomeAPI.wasRegisteredAs(location, BiomeAPI.BiomeType.END_IGNORE)) return false;

        return biome.is(BiomeTags.IS_END) ||
                BiomeAPI.wasRegisteredAsEndBiome(location) ||
                TheEndBiomesHelper.canGenerateInEnd(biome.unwrapKey().orElse(null));
    }

    private static boolean isValidNonVanillaEndBiome(Holder<Biome> biome, ResourceLocation location) {
        if (BiomeAPI.wasRegisteredAs(location, BiomeAPI.BiomeType.END_IGNORE) || biome.unwrapKey()
                                                                                      .orElseThrow()
                                                                                      .location()
                                                                                      .getNamespace()
                                                                                      .equals("minecraft"))
            return false;

        return biome.is(BiomeTags.IS_END) ||
                BiomeAPI.wasRegisteredAsEndBiome(location) ||
                TheEndBiomesHelper.canGenerateInEnd(biome.unwrapKey().orElse(null));
    }

    public static void register(IEventBus bus) {
        DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registry.BIOME_SOURCE_REGISTRY, BCLib.MOD_ID);
        BIOME_SOURCES.register("end_biome_source", () -> CODEC);
        BIOME_SOURCES.register(bus);
    }

    @Override
    protected void onInitMap(long seed) {
        for (BiomeDecider decider : deciders) {
            decider.createMap((picker, size) -> config.mapVersion.mapBuilder.create(
                    seed,
                    size <= 0 ? config.landBiomesSize : size,
                    picker
            ));
        }
        this.mapLand = config.mapVersion.mapBuilder.create(
                seed,
                config.landBiomesSize,
                endLandBiomePicker
        );

        this.mapVoid = config.mapVersion.mapBuilder.create(
                seed,
                config.voidBiomesSize,
                endVoidBiomePicker
        );

        this.mapCenter = config.mapVersion.mapBuilder.create(
                seed,
                config.centerBiomesSize,
                endCenterBiomePicker
        );

        this.mapBarrens = config.mapVersion.mapBuilder.create(
                seed,
                config.barrensBiomesSize,
                endBarrensBiomePicker
        );
    }

    @Override
    protected void onHeightChange(int newHeight) {

    }

    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, Climate.@NotNull Sampler sampler) {
        if (mapLand == null || mapVoid == null || mapCenter == null || mapBarrens == null)
            return this.possibleBiomes().stream().findFirst().orElseThrow();

        int posX = QuartPos.toBlock(biomeX);
        int posY = QuartPos.toBlock(biomeY);
        int posZ = QuartPos.toBlock(biomeZ);

        long dist = Math.abs(posX) + Math.abs(posZ) > (long) config.innerVoidRadiusSquared
                ? ((long) config.innerVoidRadiusSquared + 1)
                : (long) posX * (long) posX + (long) posZ * (long) posZ;


        if ((biomeX & 63) == 0 || (biomeZ & 63) == 0) {
            mapLand.clearCache();
            mapVoid.clearCache();
            mapCenter.clearCache();
            mapVoid.clearCache();
            for (BiomeDecider decider : deciders) {
                decider.clearMapCache();
            }
        }

        BiomeAPI.BiomeType suggestedType;


        int x = (SectionPos.blockToSectionCoord(posX) * 2 + 1) * 8;
        int z = (SectionPos.blockToSectionCoord(posZ) * 2 + 1) * 8;
        double d = sampler.erosion().compute(new DensityFunction.SinglePointContext(x, posY, z));
        if (dist <= (long) config.innerVoidRadiusSquared) {
            suggestedType = BiomeAPI.BiomeType.END_CENTER;
        } else {
            if (d > 0.25) {
                suggestedType = BiomeAPI.BiomeType.END_LAND; //highlands
            } else if (d >= -0.0625) {
                suggestedType = BiomeAPI.BiomeType.END_LAND; //midlands
            } else {
                suggestedType = d < -0.21875
                        ? BiomeAPI.BiomeType.END_VOID //small islands
                        : (config.withVoidBiomes
                                ? BiomeAPI.BiomeType.END_BARRENS
                                : BiomeAPI.BiomeType.END_LAND); //barrens
            }
        }

        final BiomeAPI.BiomeType originalType = suggestedType;
        for (BiomeDecider decider : deciders) {
            suggestedType = decider
                    .suggestType(originalType, suggestedType, d, maxHeight, posX, posY, posZ, biomeX, biomeY, biomeZ);
        }


        BiomePicker.ActualBiome result;
        for (BiomeDecider decider : deciders) {
            if (decider.canProvideBiome(suggestedType)) {
                result = decider.provideBiome(suggestedType, posX, posY, posZ);
                if (result != null) return result.biome;
            }
        }

        if (suggestedType.is(BiomeAPI.BiomeType.END_CENTER)) return mapCenter.getBiome(posX, posY, posZ).biome;
        if (suggestedType.is(BiomeAPI.BiomeType.END_VOID)) return mapVoid.getBiome(posX, posY, posZ).biome;
        if (suggestedType.is(BiomeAPI.BiomeType.END_BARRENS)) return mapBarrens.getBiome(posX, posY, posZ).biome;
        return mapLand.getBiome(posX, posY, posZ).biome;
    }


    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "\nBCLib - The End BiomeSource (" + Integer.toHexString(hashCode()) + ")" +
                "\n    biomes     = " + possibleBiomes().size() +
                "\n    namespaces = " + getNamespaces() +
                "\n    seed       = " + currentSeed +
                "\n    height     = " + maxHeight +
                "\n    deciders   = " + deciders.size() +
                "\n    config     = " + config;
    }

    @Override
    public BCLEndBiomeSourceConfig getTogetherConfig() {
        return config;
    }

    @Override
    public void setTogetherConfig(BCLEndBiomeSourceConfig newConfig) {
        this.config = newConfig;
        this.initMap(currentSeed);
    }

    @Override
    public void reloadBiomes() {
        rebuildBiomePickers();
        this.initMap(currentSeed);
    }
}
