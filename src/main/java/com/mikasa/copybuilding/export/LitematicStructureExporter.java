package com.mikasa.copybuilding.export;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Litematica (.litematic) exporter — single region, air at palette index 0.
 * Author: Mikasa
 */
public final class LitematicStructureExporter {
	private static final int LITEMATIC_VERSION = 6;
	private static final int LITEMATIC_SUBVERSION = 1;

	private LitematicStructureExporter() {
	}

	public static void write(RegionCapture capture, Path out) throws Exception {
		write(capture, out, ExportProgress.noop());
	}

	public static void write(RegionCapture capture, Path out, ExportProgress progress) throws Exception {
		DenseBlockVolume volume = DenseBlockVolume.fromCapture(capture, true, progress);
		long now = System.currentTimeMillis();
		int nonAir = countNonAir(volume);

		CompoundTag root = new CompoundTag();
		root.putInt("Version", LITEMATIC_VERSION);
		root.putInt("SubVersion", LITEMATIC_SUBVERSION);
		root.putInt("MinecraftDataVersion", SharedConstants.WORLD_VERSION);

		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", stripExt(out.getFileName().toString()));
		metadata.putString("Author", "Mikasa");
		metadata.putString("Description", "Exported by Copy That Building");
		metadata.putInt("RegionCount", 1);
		metadata.putLong("TimeCreated", now);
		metadata.putLong("TimeModified", now);
		metadata.putInt("TotalBlocks", nonAir);
		metadata.putInt("TotalVolume", volume.volume());
		CompoundTag enclosing = new CompoundTag();
		enclosing.putInt("x", volume.sizeX());
		enclosing.putInt("y", volume.sizeY());
		enclosing.putInt("z", volume.sizeZ());
		metadata.put("EnclosingSize", enclosing);
		root.put("Metadata", metadata);

		CompoundTag region = new CompoundTag();
		CompoundTag position = new CompoundTag();
		position.putInt("x", 0);
		position.putInt("y", 0);
		position.putInt("z", 0);
		region.put("Position", position);
		CompoundTag size = new CompoundTag();
		size.putInt("x", volume.sizeX());
		size.putInt("y", volume.sizeY());
		size.putInt("z", volume.sizeZ());
		region.put("Size", size);

		ListTag palette = new ListTag();
		for (RegionCapture.PaletteEntry entry : volume.palette()) {
			palette.addAndUnwrap(toBlockStateTag(entry));
		}
		region.put("BlockStatePalette", palette);
		region.put("Entities", new ListTag());
		region.put("TileEntities", new ListTag());
		region.put("PendingBlockTicks", new ListTag());
		region.put("PendingFluidTicks", new ListTag());

		int bits = bitsNeeded(volume.palette().size());
		long[] packed = packBlockStates(volume, bits, progress);
		region.putLongArray("BlockStates", packed);

		CompoundTag regions = new CompoundTag();
		regions.put("main", region);
		root.put("Regions", regions);

		progress.accept(95);
		NbtIo.writeCompressed(root, out);
		progress.accept(100);
	}

	private static CompoundTag toBlockStateTag(RegionCapture.PaletteEntry entry) {
		CompoundTag state = new CompoundTag();
		state.putString("Name", entry.name());
		if (!entry.properties().isEmpty()) {
			CompoundTag props = new CompoundTag();
			for (Map.Entry<String, String> prop : entry.properties().entrySet()) {
				props.putString(prop.getKey(), prop.getValue());
			}
			state.put("Properties", props);
		}
		return state;
	}

	private static int bitsNeeded(int paletteSize) {
		int bits = 2;
		while ((1 << bits) < paletteSize) {
			bits++;
		}
		return Math.max(2, bits);
	}

	private static long[] packBlockStates(DenseBlockVolume volume, int bits, ExportProgress progress) {
		int size = volume.volume();
		long maxEntryValue = (1L << bits) - 1L;
		int longCount = (int) Math.ceil(size * (double) bits / 64.0);
		long[] array = new long[Math.max(1, longCount)];
		int[] indices = volume.indicesYzx();
		for (int i = 0; i < size; i++) {
			long value = indices[i] & maxEntryValue;
			long startOffset = (long) i * bits;
			int startArrIndex = (int) (startOffset >>> 6);
			int endArrIndex = (int) (((i + 1L) * bits - 1L) >>> 6);
			int startBitOffset = (int) (startOffset & 63L);
			array[startArrIndex] = array[startArrIndex] & ~(maxEntryValue << startBitOffset) | (value << startBitOffset);
			if (startArrIndex != endArrIndex) {
				int endOffset = 64 - startBitOffset;
				array[endArrIndex] = array[endArrIndex] >>> bits - endOffset << bits - endOffset | (value >>> endOffset);
			}
			if ((i & 8191) == 0) {
				progress.accept(60 + (int) (30.0 * (i + 1) / Math.max(1, size)));
				Thread.yield();
			}
		}
		return array;
	}

	private static int countNonAir(DenseBlockVolume volume) {
		int air = 0;
		List<RegionCapture.PaletteEntry> palette = volume.palette();
		for (int i = 0; i < palette.size(); i++) {
			if ("minecraft:air".equals(palette.get(i).name())) {
				air = i;
				break;
			}
		}
		int count = 0;
		for (int idx : volume.indicesYzx()) {
			if (idx != air) {
				count++;
			}
		}
		return count;
	}

	private static String stripExt(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
}
