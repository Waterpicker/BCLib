package org.betterx.bclib.blocks;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BaseComposterBlock extends ComposterBlock implements BlockModelProvider {
    public BaseComposterBlock(Block source) {
        super(Properties.copy(source));
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
        Optional<String> pattern = PatternsHelper.createJson(BasePatterns.BLOCK_COMPOSTER, blockId);
        return ModelsHelper.fromPattern(pattern);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public UnbakedModel getModelVariant(
            ResourceLocation stateId,
            BlockState blockState,
            Map<ResourceLocation, UnbakedModel> modelCache
    ) {
        ResourceLocation modelId = new ResourceLocation(stateId.getNamespace(), "block/" + stateId.getPath());
        registerBlockModel(stateId, modelId, blockState, modelCache);

        ModelsHelper.MultiPartBuilder builder = ModelsHelper.MultiPartBuilder.create(stateDefinition);
        LEVEL.getPossibleValues().forEach(level -> {
            if (level > 0) {
                ResourceLocation contentId;
                if (level > 7) {
                    contentId = new ResourceLocation("block/composter_contents_ready");
                } else {
                    contentId = new ResourceLocation("block/composter_contents" + level);
                }
                builder.part(contentId).setCondition(state -> state.getValue(LEVEL).equals(level)).add();
            }
        });
        builder.part(modelId).add();

        return builder.build();
    }
}
