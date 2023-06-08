package org.betterx.bclib.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.betterx.bclib.BCLib;
import org.betterx.bclib.blockentities.*;
import org.betterx.bclib.blockentities.DynamicBlockEntityType.BlockEntitySupplier;
import org.betterx.bclib.blocks.BaseBarrelBlock;
import org.betterx.bclib.blocks.BaseChestBlock;
import org.betterx.bclib.blocks.BaseFurnaceBlock;
import org.betterx.bclib.blocks.BaseSignBlock;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BaseBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BASE_BLOCK_ENTITIES = DeferredRegister.create(Registry.BLOCK_ENTITY_TYPE_REGISTRY, BCLib.MOD_ID);

    public static final RegistryObject<DynamicBlockEntityType<BaseChestBlockEntity>> CHEST = registerBlockEntityType(BCLib.makeID(
            "chest"), BaseChestBlockEntity::new);
    public static final RegistryObject<DynamicBlockEntityType<BaseBarrelBlockEntity>> BARREL = registerBlockEntityType(BCLib.makeID(
            "barrel"), BaseBarrelBlockEntity::new);
    public static final RegistryObject<DynamicBlockEntityType<BaseSignBlockEntity>> SIGN = registerBlockEntityType(
            BCLib.makeID("sign"),
            BaseSignBlockEntity::new
    );
    public static final RegistryObject<DynamicBlockEntityType<BaseFurnaceBlockEntity>> FURNACE = registerBlockEntityType(BCLib.makeID(
            "furnace"), BaseFurnaceBlockEntity::new);

    public static <T extends BlockEntity> RegistryObject<DynamicBlockEntityType<T>> registerBlockEntityType(
            ResourceLocation typeId,
            BlockEntitySupplier<? extends T> supplier
    ) {
        return BaseBlockEntities.BASE_BLOCK_ENTITIES.register(typeId, () -> new DynamicBlockEntityType<>(supplier));
    }

    public static void register(IEventBus bus) {
        BASE_BLOCK_ENTITIES.register(bus);
    }

    public static Block[] getChests() {
        return Registry.BLOCK
                .stream()
                .filter(block -> block instanceof BaseChestBlock)
                .toArray(Block[]::new);
    }

    public static Block[] getBarrels() {
        return Registry.BLOCK
                .stream()
                .filter(block -> block instanceof BaseBarrelBlock)
                .toArray(Block[]::new);
    }

    public static Block[] getSigns() {
        return Registry.BLOCK
                .stream()
                .filter(block -> block instanceof BaseSignBlock)
                .toArray(Block[]::new);
    }

    public static Block[] getFurnaces() {
        return Registry.BLOCK
                .stream()
                .filter(block -> block instanceof BaseFurnaceBlock)
                .toArray(Block[]::new);
    }
}
