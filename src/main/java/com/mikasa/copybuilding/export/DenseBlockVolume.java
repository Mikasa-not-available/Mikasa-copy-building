package com.mikasa.copybuilding.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dense width×height×length volume of palette indices for schematic-style formats.
 * Author: Mikasa
 */
public final class DenseBlockVolume {
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<RegionCapture.PaletteEntry> palette;
	private final int[] indices;

	private DenseBlockVolume(int sizeX, int sizeY, int sizeZ, List<RegionCapture.PaletteEntry> palette, int[] indices) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = palette;
		this.indices = indices;
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

	public int volume() {
		return sizeX * sizeY * sizeZ;
	}

	public List<RegionCapture.PaletteEntry> palette() {
		return palette;
	}

	public int indexAt(int x, int y, int z) {
		return indices[linear(x, y, z)];
	}

	public int linear(int x, int y, int z) {
		return (y * sizeX * sizeZ) + (z * sizeX) + x;
	}

	public int[] indicesYzx() {
		return indices;
	}

	/**
	 * Builds a dense volume. Palette index 0 is always air when {@code airFirst} is true (Litematica).
	 */
	public static DenseBlockVolume fromCapture(RegionCapture capture, boolean airFirst, ExportProgress progress) {
		progress.accept(2);
		int sx = capture.sizeX();
		int sy = capture.sizeY();
		int sz = capture.sizeZ();
		int volume = Math.multiplyExact(Math.multiplyExact(sx, sy), sz);

		List<RegionCapture.PaletteEntry> outPalette = new ArrayList<>();
		Map<Integer, Integer> remap = new HashMap<>();
		RegionCapture.PaletteEntry air = new RegionCapture.PaletteEntry("minecraft:air", Map.of());
		if (airFirst) {
			outPalette.add(air);
		}

		List<RegionCapture.PaletteEntry> srcPalette = capture.palette();
		for (int i = 0; i < srcPalette.size(); i++) {
			RegionCapture.PaletteEntry entry = srcPalette.get(i);
			if (airFirst && isAir(entry)) {
				remap.put(i, 0);
				continue;
			}
			int existing = indexOf(outPalette, entry);
			if (existing < 0) {
				existing = outPalette.size();
				outPalette.add(entry);
			}
			remap.put(i, existing);
		}
		if (outPalette.isEmpty()) {
			outPalette.add(air);
		}
		int airIndex = indexOf(outPalette, air);
		if (airIndex < 0) {
			airIndex = outPalette.size();
			outPalette.add(air);
		}

		int[] data = new int[volume];
		java.util.Arrays.fill(data, airIndex);
		progress.accept(10);

		List<RegionCapture.CapturedBlock> blocks = capture.blocks();
		int total = Math.max(1, blocks.size());
		for (int i = 0; i < blocks.size(); i++) {
			RegionCapture.CapturedBlock b = blocks.get(i);
			if (b.x() < 0 || b.y() < 0 || b.z() < 0 || b.x() >= sx || b.y() >= sy || b.z() >= sz) {
				continue;
			}
			Integer mapped = remap.get(b.paletteIndex());
			if (mapped == null) {
				continue;
			}
			data[(b.y() * sx * sz) + (b.z() * sx) + b.x()] = mapped;
			if ((i & 4095) == 0) {
				progress.accept(10 + (int) (50.0 * (i + 1) / total));
				Thread.yield();
			}
		}
		progress.accept(60);
		return new DenseBlockVolume(sx, sy, sz, List.copyOf(outPalette), data);
	}

	private static boolean isAir(RegionCapture.PaletteEntry entry) {
		return "minecraft:air".equals(entry.name())
				|| "minecraft:cave_air".equals(entry.name())
				|| "minecraft:void_air".equals(entry.name());
	}

	private static int indexOf(List<RegionCapture.PaletteEntry> palette, RegionCapture.PaletteEntry entry) {
		for (int i = 0; i < palette.size(); i++) {
			if (palette.get(i).key().equals(entry.key())) {
				return i;
			}
		}
		return -1;
	}

	public static String spongeStateString(RegionCapture.PaletteEntry entry) {
		if (entry.properties().isEmpty()) {
			return entry.name();
		}
		StringBuilder sb = new StringBuilder(entry.name()).append('[');
		boolean first = true;
		for (Map.Entry<String, String> prop : entry.properties().entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append(prop.getKey()).append('=').append(prop.getValue());
		}
		return sb.append(']').toString();
	}
}
