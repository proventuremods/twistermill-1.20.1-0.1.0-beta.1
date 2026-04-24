package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.block.ModBlocks;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class TwisterSailBlock extends SailBlock {

    public static final EnumProperty<FrameMaterial> FRAME_MATERIAL =
            EnumProperty.create("frame_material", FrameMaterial.class);
    public static final EnumProperty<DyeColor> SAIL_COLOR =
            EnumProperty.create("sail_color", DyeColor.class);

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public TwisterSailBlock(Properties properties, boolean frame) {
        super(properties, frame, frame ? null : DyeColor.WHITE);
        BlockState defaultState = defaultBlockState()
                .setValue(FRAME_MATERIAL, FrameMaterial.TWISTER_PALE_PLANKS);

        if (!frame) {
            defaultState = defaultState.setValue(SAIL_COLOR, DyeColor.WHITE);
        }

        registerDefaultState(defaultState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRAME_MATERIAL);
        if (!frame) {
            builder.add(SAIL_COLOR);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(FACING, state.getValue(FACING).getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() && player.mayBuild()) {
            IPlacementHelper helper = PlacementHelpers.get(placementHelperId);
            if (helper.matchesItem(stack)) {
                helper.getOffset(player, level, state, pos, hit)
                        .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
                return InteractionResult.SUCCESS;
            }
        }

        if (stack.getItem() instanceof ShearsItem) {
            applyDye(state, level, pos, hit.getLocation(), null);
            return InteractionResult.SUCCESS;
        }

        if (frame) {
            return InteractionResult.PASS;
        }

        DyeColor color = DyeColor.getColor(stack);
        if (!player.isShiftKeyDown() && color != null) {
            applyDye(state, level, pos, hit.getLocation(), color);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void applyDye(BlockState state, Level world, BlockPos pos, Vec3 hit, @Nullable DyeColor color) {
        BlockState newState = (color == null
                ? ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get()
                : ModBlocks.TWISTER_SAIL_BLOCK.get()).defaultBlockState();

        newState = BlockHelper.copyProperties(state, newState);
        if (color != null && newState.hasProperty(SAIL_COLOR)) {
            newState = newState.setValue(SAIL_COLOR, color);
        }

        if (state != newState) {
            world.setBlockAndUpdate(pos, newState);
        }
    }

    public enum FrameMaterial implements StringRepresentable {
        TWISTER_PALE_PLANKS("twister_pale_planks"),
        ACACIA_PLANKS("acacia_planks"),
        BAMBOO_PLANKS("bamboo_planks"),
        BIRCH_PLANKS("birch_planks"),
        CHERRY_PLANKS("cherry_planks"),
        CRIMSON_PLANKS("crimson_planks"),
        DARK_OAK_PLANKS("dark_oak_planks"),
        JUNGLE_PLANKS("jungle_planks"),
        MANGROVE_PLANKS("mangrove_planks"),
        OAK_PLANKS("oak_planks"),
        SPRUCE_PLANKS("spruce_planks"),
        WARPED_PLANKS("warped_planks"),
        ACACIA_LOG("acacia_log"),
        BIRCH_LOG("birch_log"),
        CHERRY_LOG("cherry_log"),
        DARK_OAK_LOG("dark_oak_log"),
        JUNGLE_LOG("jungle_log"),
        MANGROVE_LOG("mangrove_log"),
        OAK_LOG("oak_log"),
        SPRUCE_LOG("spruce_log"),
        CRIMSON_STEM("crimson_stem"),
        WARPED_STEM("warped_stem"),
        STRIPPED_ACACIA_LOG("stripped_acacia_log"),
        STRIPPED_BIRCH_LOG("stripped_birch_log"),
        STRIPPED_CHERRY_LOG("stripped_cherry_log"),
        STRIPPED_DARK_OAK_LOG("stripped_dark_oak_log"),
        STRIPPED_JUNGLE_LOG("stripped_jungle_log"),
        STRIPPED_MANGROVE_LOG("stripped_mangrove_log"),
        STRIPPED_OAK_LOG("stripped_oak_log"),
        STRIPPED_SPRUCE_LOG("stripped_spruce_log"),
        STRIPPED_CRIMSON_STEM("stripped_crimson_stem"),
        STRIPPED_WARPED_STEM("stripped_warped_stem"),
        BLACK_WOOL("black_wool"),
        BLUE_WOOL("blue_wool"),
        BROWN_WOOL("brown_wool"),
        CYAN_WOOL("cyan_wool"),
        GRAY_WOOL("gray_wool"),
        GREEN_WOOL("green_wool"),
        LIGHT_BLUE_WOOL("light_blue_wool"),
        LIGHT_GRAY_WOOL("light_gray_wool"),
        LIME_WOOL("lime_wool"),
        MAGENTA_WOOL("magenta_wool"),
        ORANGE_WOOL("orange_wool"),
        PINK_WOOL("pink_wool"),
        PURPLE_WOOL("purple_wool"),
        RED_WOOL("red_wool"),
        WHITE_WOOL("white_wool"),
        YELLOW_WOOL("yellow_wool");

        private final String serializedName;

        FrameMaterial(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public static FrameMaterial fromBlock(Block block) {
            if (block == Blocks.ACACIA_PLANKS) return ACACIA_PLANKS;
            if (block == Blocks.BAMBOO_PLANKS) return BAMBOO_PLANKS;
            if (block == Blocks.BIRCH_PLANKS) return BIRCH_PLANKS;
            if (block == Blocks.CHERRY_PLANKS) return CHERRY_PLANKS;
            if (block == Blocks.CRIMSON_PLANKS) return CRIMSON_PLANKS;
            if (block == Blocks.DARK_OAK_PLANKS) return DARK_OAK_PLANKS;
            if (block == Blocks.JUNGLE_PLANKS) return JUNGLE_PLANKS;
            if (block == Blocks.MANGROVE_PLANKS) return MANGROVE_PLANKS;
            if (block == Blocks.OAK_PLANKS) return OAK_PLANKS;
            if (block == Blocks.SPRUCE_PLANKS) return SPRUCE_PLANKS;
            if (block == Blocks.WARPED_PLANKS) return WARPED_PLANKS;
            if (block == Blocks.ACACIA_LOG) return ACACIA_LOG;
            if (block == Blocks.BIRCH_LOG) return BIRCH_LOG;
            if (block == Blocks.CHERRY_LOG) return CHERRY_LOG;
            if (block == Blocks.DARK_OAK_LOG) return DARK_OAK_LOG;
            if (block == Blocks.JUNGLE_LOG) return JUNGLE_LOG;
            if (block == Blocks.MANGROVE_LOG) return MANGROVE_LOG;
            if (block == Blocks.OAK_LOG) return OAK_LOG;
            if (block == Blocks.SPRUCE_LOG) return SPRUCE_LOG;
            if (block == Blocks.CRIMSON_STEM) return CRIMSON_STEM;
            if (block == Blocks.WARPED_STEM) return WARPED_STEM;
            if (block == Blocks.STRIPPED_ACACIA_LOG) return STRIPPED_ACACIA_LOG;
            if (block == Blocks.STRIPPED_BIRCH_LOG) return STRIPPED_BIRCH_LOG;
            if (block == Blocks.STRIPPED_CHERRY_LOG) return STRIPPED_CHERRY_LOG;
            if (block == Blocks.STRIPPED_DARK_OAK_LOG) return STRIPPED_DARK_OAK_LOG;
            if (block == Blocks.STRIPPED_JUNGLE_LOG) return STRIPPED_JUNGLE_LOG;
            if (block == Blocks.STRIPPED_MANGROVE_LOG) return STRIPPED_MANGROVE_LOG;
            if (block == Blocks.STRIPPED_OAK_LOG) return STRIPPED_OAK_LOG;
            if (block == Blocks.STRIPPED_SPRUCE_LOG) return STRIPPED_SPRUCE_LOG;
            if (block == Blocks.STRIPPED_CRIMSON_STEM) return STRIPPED_CRIMSON_STEM;
            if (block == Blocks.STRIPPED_WARPED_STEM) return STRIPPED_WARPED_STEM;
            if (block == Blocks.BLACK_WOOL) return BLACK_WOOL;
            if (block == Blocks.BLUE_WOOL) return BLUE_WOOL;
            if (block == Blocks.BROWN_WOOL) return BROWN_WOOL;
            if (block == Blocks.CYAN_WOOL) return CYAN_WOOL;
            if (block == Blocks.GRAY_WOOL) return GRAY_WOOL;
            if (block == Blocks.GREEN_WOOL) return GREEN_WOOL;
            if (block == Blocks.LIGHT_BLUE_WOOL) return LIGHT_BLUE_WOOL;
            if (block == Blocks.LIGHT_GRAY_WOOL) return LIGHT_GRAY_WOOL;
            if (block == Blocks.LIME_WOOL) return LIME_WOOL;
            if (block == Blocks.MAGENTA_WOOL) return MAGENTA_WOOL;
            if (block == Blocks.ORANGE_WOOL) return ORANGE_WOOL;
            if (block == Blocks.PINK_WOOL) return PINK_WOOL;
            if (block == Blocks.PURPLE_WOOL) return PURPLE_WOOL;
            if (block == Blocks.RED_WOOL) return RED_WOOL;
            if (block == Blocks.WHITE_WOOL) return WHITE_WOOL;
            if (block == Blocks.YELLOW_WOOL) return YELLOW_WOOL;

            return null;
        }
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof SailBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof SailBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                    pos,
                    ray.getLocation(),
                    state.getValue(SailBlock.FACING).getAxis(),
                    direction -> world.getBlockState(pos.relative(direction)).canBeReplaced()
            );

            if (directions.isEmpty())
                return PlacementOffset.fail();

            return PlacementOffset.success(
                    pos.relative(directions.get(0)),
                    placedState -> placedState.setValue(FACING, state.getValue(FACING))
            );
        }
    }
}
