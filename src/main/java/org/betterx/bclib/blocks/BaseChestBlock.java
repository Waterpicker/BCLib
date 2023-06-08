package org.betterx.bclib.blocks;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.betterx.bclib.client.models.BasePatterns;
import org.betterx.bclib.client.models.ModelsHelper;
import org.betterx.bclib.client.models.PatternsHelper;
import org.betterx.bclib.interfaces.BlockModelProvider;
import org.betterx.bclib.registry.BaseBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class BaseChestBlock extends ChestBlock implements BlockModelProvider {
    private final Block parent;

    public BaseChestBlock(Block source) {
        super(Properties.copy(source).noOcclusion(), BaseBlockEntities.CHEST::get);
        this.parent = source;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return BaseBlockEntities.CHEST.get().create(blockPos, blockState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        List<ItemStack> drop = super.getDrops(state, builder);
        drop.add(new ItemStack(this.asItem()));
        return drop;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BlockModel getItemModel(ResourceLocation blockId) {
        Optional<String> pattern = PatternsHelper.createJson(BasePatterns.ITEM_CHEST, blockId);
        return ModelsHelper.fromPattern(pattern);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @Nullable BlockModel getBlockModel(ResourceLocation resourceLocation, BlockState blockState) {
        ResourceLocation parentId = Registry.BLOCK.getKey(parent);
        return ModelsHelper.createBlockEmpty(parentId);
    }
}
