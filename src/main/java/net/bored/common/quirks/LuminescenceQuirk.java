package net.bored.common.quirks;

import net.bored.api.QuirkSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class LuminescenceQuirk extends QuirkSystem.Quirk {
    public static final Identifier ID = new Identifier("plusultra", "luminescence");

    public LuminescenceQuirk() { super(ID); }

    @Override
    public int getIconColor() { return 0xFFFFFF00; } // Yellow

    @Override
    public void registerAbilities() {
        // No active abilities, purely passive
    }

    @Override
    public void onUpdate(LivingEntity entity, QuirkSystem.QuirkData data, QuirkSystem.QuirkData.QuirkInstance instance) {
        if (entity.getWorld().isClient) return; // Server-side block manipulation only

        ServerWorld world = (ServerWorld) entity.getWorld();
        BlockPos currentPos = entity.getBlockPos();

        // Retrieve the last known position of the light
        BlockPos lastPos = null;
        if (data.runtimeTags.containsKey("LUM_LAST_X")) {
            try {
                int x = Integer.parseInt(data.runtimeTags.get("LUM_LAST_X"));
                int y = Integer.parseInt(data.runtimeTags.get("LUM_LAST_Y"));
                int z = Integer.parseInt(data.runtimeTags.get("LUM_LAST_Z"));
                lastPos = new BlockPos(x, y, z);
            } catch (NumberFormatException e) {
                // Corrupt tag, ignore
            }
        }

        // If we moved, or if this is the first tick
        if (lastPos == null || !lastPos.equals(currentPos)) {
            // 1. Clean up the old light block
            if (lastPos != null) {
                // Only remove if it is actually a light block (don't delete other blocks if user teleported weirdly)
                BlockState oldState = world.getBlockState(lastPos);
                if (oldState.isOf(Blocks.LIGHT)) {
                    world.setBlockState(lastPos, Blocks.AIR.getDefaultState());
                }
            }

            // 2. Place new light block if space is empty
            if (canPlaceLight(world, currentPos)) {
                world.setBlockState(currentPos, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 15));

                // Update tags
                data.runtimeTags.put("LUM_LAST_X", String.valueOf(currentPos.getX()));
                data.runtimeTags.put("LUM_LAST_Y", String.valueOf(currentPos.getY()));
                data.runtimeTags.put("LUM_LAST_Z", String.valueOf(currentPos.getZ()));
            } else {
                // If we can't place light here (e.g. inside a block), try the head position
                BlockPos headPos = currentPos.up();
                if (canPlaceLight(world, headPos)) {
                    world.setBlockState(headPos, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 15));

                    data.runtimeTags.put("LUM_LAST_X", String.valueOf(headPos.getX()));
                    data.runtimeTags.put("LUM_LAST_Y", String.valueOf(headPos.getY()));
                    data.runtimeTags.put("LUM_LAST_Z", String.valueOf(headPos.getZ()));
                } else {
                    // Couldn't place light anywhere valid, clear tags so we don't try to remove a random block later
                    data.runtimeTags.remove("LUM_LAST_X");
                    data.runtimeTags.remove("LUM_LAST_Y");
                    data.runtimeTags.remove("LUM_LAST_Z");
                }
            }
        } else {
            // We haven't moved. Ensure the light is still there (in case it was overwritten or broken)
            if (data.runtimeTags.containsKey("LUM_LAST_X")) {
                if (canPlaceLight(world, currentPos)) {
                    world.setBlockState(currentPos, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 15));
                }
            }
        }
    }

    private boolean canPlaceLight(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || (state.isOf(Blocks.LIGHT) && state.get(LightBlock.LEVEL_15) < 15);
    }

    @Override
    public void onRemove(LivingEntity entity, QuirkSystem.QuirkData data) {
        if (entity.getWorld().isClient) return;
        ServerWorld world = (ServerWorld) entity.getWorld();

        // Clean up light on quirk removal
        if (data.runtimeTags.containsKey("LUM_LAST_X")) {
            try {
                int x = Integer.parseInt(data.runtimeTags.get("LUM_LAST_X"));
                int y = Integer.parseInt(data.runtimeTags.get("LUM_LAST_Y"));
                int z = Integer.parseInt(data.runtimeTags.get("LUM_LAST_Z"));
                BlockPos lastPos = new BlockPos(x, y, z);

                if (world.getBlockState(lastPos).isOf(Blocks.LIGHT)) {
                    world.setBlockState(lastPos, Blocks.AIR.getDefaultState());
                }
            } catch (NumberFormatException e) {}

            data.runtimeTags.remove("LUM_LAST_X");
            data.runtimeTags.remove("LUM_LAST_Y");
            data.runtimeTags.remove("LUM_LAST_Z");
        }
    }
}