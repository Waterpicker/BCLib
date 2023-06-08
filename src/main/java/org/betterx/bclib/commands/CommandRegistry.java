package org.betterx.bclib.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.betterx.bclib.util.BlocksHelper;
import org.betterx.worlds.together.tag.v3.CommonBlockTags;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CommandRegistry {
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(new Consumer<RegisterCommandsEvent>() {
            @Override
            public void accept(RegisterCommandsEvent event) {
                register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
            }
        });
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext commandBuildContext,
            Commands.CommandSelection commandSelection
    ) {
        dispatcher.register(
                Commands.literal("bclib")
                        .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.literal("request_garbage_collection")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .executes(ctx -> requestGC(ctx))
                        )
                        .then(Commands.literal("dump_datapack")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .executes(ctx -> DumpDatapack.dumpDatapack(ctx))
                        )
                        .then(Commands.literal("print")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .then(Commands.literal("dimensions")
                                                    .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                                    .executes(ctx -> PrintInfo.printDimensions(ctx))
                                      ).then(Commands.literal("updates")
                                                     .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                                     .executes(ctx -> PrintInfo.printUpdates(ctx, true))
                                )
                        )
                        .then(Commands.literal("debug_ore")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .executes(ctx -> revealOre(ctx))
                        )
                        .then(Commands.literal("sliceZ")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .executes(ctx -> slice(ctx, true))
                        )
                        .then(Commands.literal("sliceX")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                      .executes(ctx -> slice(ctx, false))
                        )
        );
    }

    private static int requestGC(CommandContext<CommandSourceStack> ctx) {
        System.gc();
        return Command.SINGLE_SUCCESS;
    }

    private static final Map<Holder<Biome>, BlockState> biomeMap = new HashMap<>();
    private static int biomeMapIdx = 0;
    private static final BlockState[] states = {
            Blocks.RED_STAINED_GLASS.defaultBlockState(),
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
            Blocks.LIME_STAINED_GLASS.defaultBlockState(),
            Blocks.PINK_STAINED_GLASS.defaultBlockState(),
            Blocks.GREEN_STAINED_GLASS.defaultBlockState(),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
            Blocks.BLACK_STAINED_GLASS.defaultBlockState(),
            Blocks.ORANGE_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
    };
    private static final BlockState[] states2 = {
            Blocks.RED_CONCRETE.defaultBlockState(),
            Blocks.BLUE_CONCRETE.defaultBlockState(),
            Blocks.YELLOW_CONCRETE.defaultBlockState(),
            Blocks.LIME_CONCRETE.defaultBlockState(),
            Blocks.PINK_CONCRETE.defaultBlockState(),
            Blocks.GREEN_CONCRETE.defaultBlockState(),
            Blocks.WHITE_CONCRETE.defaultBlockState(),
            Blocks.BLACK_CONCRETE.defaultBlockState(),
            Blocks.ORANGE_CONCRETE.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState()
    };

    private static int revealOre(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final Vec3 pos = source.getPosition();

        MutableBlockPos bp = new MutableBlockPos();
        BlockState state;
        BlockState fillState;
        final BlockState AIR = Blocks.AIR.defaultBlockState();

        for (int y = 1; y < level.getHeight(); y++) {
            bp.setY(y);
            for (int x = -64; x < 64; x++) {
                bp.setX((int) pos.x + x);
                for (int z = -64; z < 64; z++) {
                    bp.setZ((int) pos.z + z);
                    if (y == 1) {
                        Holder<Biome> b = level.getBiome(bp);
                        fillState = biomeMap.computeIfAbsent(b, (bb) -> {
                            biomeMapIdx = (biomeMapIdx + 1) % states.length;
                            return states[biomeMapIdx];
                        });
                    } else {
                        fillState = AIR;
                    }

                    state = level.getBlockState(bp);
                    if (y == 1 || !state.is(Blocks.AIR)) {
                        if (!(state.is(CommonBlockTags.NETHER_ORES)
                                || state.is(CommonBlockTags.END_ORES)
                                || state.is(BlockTags.COAL_ORES)
                                || state.is(BlockTags.COPPER_ORES)
                                || state.is(BlockTags.DIAMOND_ORES)
                                || state.is(BlockTags.EMERALD_ORES)
                                || state.is(BlockTags.GOLD_ORES)
                                || state.is(BlockTags.IRON_ORES)
                                || state.is(BlockTags.LAPIS_ORES)
                                || state.is(BlockTags.REDSTONE_ORES)
                                || state.is(Blocks.NETHER_QUARTZ_ORE)
                                || state.is(Blocks.NETHER_GOLD_ORE)
                                || state.is(Blocks.ANCIENT_DEBRIS))) {
                            BlocksHelper.setWithoutUpdate(level, bp, fillState);
                        }
                    }
                }
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int slice(CommandContext<CommandSourceStack> ctx, boolean constX) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final Vec3 pos = source.getPosition();

        BlockState AIR = Blocks.AIR.defaultBlockState();
        MutableBlockPos bp = new MutableBlockPos();
        BlockState state;
        BlockState fillState;


        for (int y = 1; y < level.getHeight(); y++) {
            bp.setY(y);
            for (int x = constX ? 0 : -64; x < 64; x++) {
                bp.setX((int) pos.x + x);
                for (int z = constX ? -64 : 0; z < 64; z++) {
                    bp.setZ((int) pos.z + z);
                    if (y == 1) {
                        Holder<Biome> b = level.getBiome(bp);
                        fillState = biomeMap.computeIfAbsent(b, (bb) -> {
                            biomeMapIdx = (biomeMapIdx + 1) % states.length;
                            return states[biomeMapIdx];
                        });
                    } else {
                        fillState = AIR;
                    }

                    BlocksHelper.setWithoutUpdate(level, bp, fillState);
                }
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int findSurface(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        Vec3 pos = source.getPosition();
        final ServerLevel level = source.getLevel();
        MutableBlockPos mPos = new BlockPos(pos).mutable();
        System.out.println("Staring at: " + mPos + " -> " + level.getBlockState(mPos));
        boolean found = org.betterx.bclib.util.BlocksHelper.findSurroundingSurface(
                level,
                mPos,
                Direction.DOWN,
                12,
                state -> BlocksHelper.isTerrain(state)
        );
        System.out.println("Ending at: " + mPos + " -> " + level.getBlockState(mPos) + " = " + found);
        org.betterx.bclib.util.BlocksHelper.setWithoutUpdate(level, new BlockPos(pos), Blocks.YELLOW_CONCRETE);
        org.betterx.bclib.util.BlocksHelper.setWithoutUpdate(level, mPos, Blocks.LIGHT_BLUE_CONCRETE);
        return Command.SINGLE_SUCCESS;
    }
}

