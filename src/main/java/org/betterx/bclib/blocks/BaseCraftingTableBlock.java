package org.betterx.bclib.blocks;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.betterx.bclib.client.models.BasePatterns;
import org.betterx.bclib.client.models.ModelsHelper;
import org.betterx.bclib.client.models.PatternsHelper;
import org.betterx.bclib.interfaces.BlockModelProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class BaseCraftingTableBlock extends CraftingTableBlock implements BlockModelProvider {
    public BaseCraftingTableBlock(Block source) {
        this(Properties.copy(source));
    }

    public BaseCraftingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        return Collections.singletonList(new ItemStack(this.asItem()));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BlockModel getItemModel(ResourceLocation resourceLocation) {
        return getBlockModel(resourceLocation, defaultBlockState());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @Nullable BlockModel getBlockModel(ResourceLocation blockId, BlockState blockState) {
        String blockName = blockId.getPath();
        Optional<String> pattern = PatternsHelper.createJson(BasePatterns.BLOCK_SIDED, new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;

            {
                put("%modid%", blockId.getNamespace());
                put("%particle%", blockName + "_front");
                put("%down%", blockName + "_bottom");
                put("%up%", blockName + "_top");
                put("%north%", blockName + "_front");
                put("%south%", blockName + "_side");
                put("%west%", blockName + "_front");
                put("%east%", blockName + "_side");
            }
        });
        return ModelsHelper.fromPattern(pattern);
    }
}
