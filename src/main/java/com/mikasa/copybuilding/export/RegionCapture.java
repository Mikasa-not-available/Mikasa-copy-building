package com.mikasa.copybuilding.export;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory captured region (relative coordinates). Thread-safe for scan worker + UI.
 * Author: Mikasa
 */
public final class RegionCapture {
	private final BlockPos origin;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final Map<String, Integer> paletteIndex = new LinkedHashMap<>();
	private final List<PaletteEntry> palette = new ArrayList<>();
	private final List<CapturedBlock> blocks = new ArrayList<>();

	public RegionCapture(BlockPos origin, int sizeX, int sizeY, int sizeZ) {
		this.origin = origin.immutable();
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
	}

	public BlockPos origin() {
		return origin;
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

	public synchronized List<PaletteEntry> palette() {
		return List.copyOf(palette);
	}

	public synchronized List<CapturedBlock> blocks() {
		return List.copyOf(blocks);
	}

	public synchronized int blockCount() {
		return blocks.size();
	}

	public synchronized int paletteCount() {
		return palette.size();
	}

	public synchronized void addBlock(int relX, int relY, int relZ, BlockState state) {
		int index = indexOf(state);
		blocks.add(new CapturedBlock(relX, relY, relZ, index));
	}

	public synchronized void addPaletteBlock(int relX, int relY, int relZ, PaletteEntry entry) {
		int index = indexOfEntry(entry);
		blocks.add(new CapturedBlock(relX, relY, relZ, index));
	}

	/** Deep enough copy for background export without locking the live capture. */
	public synchronized RegionCapture snapshot() {
		RegionCapture copy = new RegionCapture(origin, sizeX, sizeY, sizeZ);
		for (PaletteEntry entry : palette) {
			copy.palette.add(entry);
			copy.paletteIndex.put(entry.key(), copy.palette.size() - 1);
		}
		copy.blocks.addAll(blocks);
		return copy;
	}

	private int indexOf(BlockState state) {
		return indexOfEntry(PaletteEntry.from(state));
	}

	private int indexOfEntry(PaletteEntry entry) {
		String key = entry.key();
		Integer existing = paletteIndex.get(key);
		if (existing != null) {
			return existing;
		}
		int index = palette.size();
		palette.add(entry);
		paletteIndex.put(key, index);
		return index;
	}

	public record CapturedBlock(int x, int y, int z, int paletteIndex) {
	}

	public record PaletteEntry(String name, Map<String, String> properties) {
		public String key() {
			if (properties.isEmpty()) {
				return name;
			}
			return name + properties;
		}

		public static PaletteEntry from(BlockState state) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			String name = id == null ? "minecraft:air" : id.toString();
			Map<String, String> props = new LinkedHashMap<>();
			for (Property<?> property : state.getProperties()) {
				props.put(property.getName(), nameValue(state, property));
			}
			return new PaletteEntry(name, props);
		}

		private static <T extends Comparable<T>> String nameValue(BlockState state, Property<T> property) {
			return property.getName(state.getValue(property));
		}
	}
}
