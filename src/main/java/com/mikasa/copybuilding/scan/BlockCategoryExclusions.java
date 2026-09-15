package com.mikasa.copybuilding.scan;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built-in category exclusions for scanning.
 * Author: Mikasa
 */
public final class BlockCategoryExclusions {
	private BlockCategoryExclusions() {
	}

	public static boolean isGrass(BlockState state) {
		if (state.getBlock().builtInRegistryHolder().is(BlockTags.GRASS_BLOCKS)) {
			return true;
		}
		var block = state.getBlock();
		return block == Blocks.SHORT_GRASS
				|| block == Blocks.TALL_GRASS
				|| block == Blocks.FERN
				|| block == Blocks.LARGE_FERN
				|| block == Blocks.SHORT_DRY_GRASS
				|| block == Blocks.TALL_DRY_GRASS
				|| block == Blocks.SEAGRASS
				|| block == Blocks.TALL_SEAGRASS;
	}

	public static boolean isFlower(BlockState state) {
		var holder = state.getBlock().builtInRegistryHolder();
		return holder.is(BlockTags.FLOWERS)
				|| holder.is(BlockTags.SMALL_FLOWERS)
				|| holder.is(BlockTags.FLOWER_POTS);
	}

	public static boolean isDirt(BlockState state) {
		return state.getBlock().builtInRegistryHolder().is(BlockTags.DIRT);
	}

	public static boolean isWater(BlockState state) {
		var block = state.getBlock();
		if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.KELP || block == Blocks.KELP_PLANT) {
			return true;
		}
		return state.getFluidState().is(FluidTags.WATER);
	}
}
