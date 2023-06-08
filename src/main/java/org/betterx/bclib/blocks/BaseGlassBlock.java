package org.betterx.bclib.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.betterx.bclib.client.render.BCLRenderLayer;
import org.betterx.bclib.interfaces.RenderLayerProvider;
import org.betterx.bclib.interfaces.tools.AddMineablePickaxe;

import java.util.Collections;
import java.util.List;

public class BaseGlassBlock extends BaseBlockNotFull implements AddMineablePickaxe, RenderLayerProvider {
    public BaseGlassBlock(Block block) {
        this(block, 0.3f);
    }

    public BaseGlassBlock(Block block, float resistance) {
        super(Properties.copy(block)
                                 .explosionResistance(resistance)
                                 .noOcclusion()
                                 .isSuffocating((arg1, arg2, arg3) -> false)
                                 .isViewBlocking((arg1, arg2, arg3) -> false));
    }

    @OnlyIn(Dist.CLIENT)
    public float getShadeBrightness(BlockState state, BlockGetter view, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter view, BlockPos pos) {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean skipRendering(BlockState state, BlockState neighbor, Direction facing) {
        return neighbor.getBlock() == this || super.skipRendering(state, neighbor, facing);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        ItemStack tool = builder.getParameter(LootContextParams.TOOL);
        if (tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return Collections.singletonList(new ItemStack(this));
        }
        return Collections.emptyList();
    }

    @Override
    public BCLRenderLayer getRenderLayer() {
        return BCLRenderLayer.TRANSLUCENT;
    }
}
