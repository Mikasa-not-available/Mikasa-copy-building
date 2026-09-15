package com.mikasa.copybuilding.export;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes vanilla structure-template compatible NBT with optional progress.
 * Author: Mikasa
 */
public final class NbtStructureExporter {
	private NbtStructureExporter() {
	}

	public static void write(RegionCapture capture, Path out) throws Exception {
		write(capture, out, ExportProgress.noop());
	}

	public static void write(RegionCapture capture, Path out, ExportProgress progress) throws Exception {
		progress.accept(1);
		CompoundTag root = new CompoundTag();

		ListTag size = new ListTag();
		size.addAndUnwrap(IntTag.valueOf(capture.sizeX()));
		size.addAndUnwrap(IntTag.valueOf(capture.sizeY()));
		size.addAndUnwrap(IntTag.valueOf(capture.sizeZ()));
		root.put("size", size);

		ListTag palette = new ListTag();
		List<RegionCapture.PaletteEntry> paletteEntries = capture.palette();
		for (int i = 0; i < paletteEntries.size(); i++) {
			RegionCapture.PaletteEntry entry = paletteEntries.get(i);
			CompoundTag state = new CompoundTag();
			state.putString("Name", entry.name());
			if (!entry.properties().isEmpty()) {
				CompoundTag props = new CompoundTag();
				for (Map.Entry<String, String> prop : entry.properties().entrySet()) {
					props.putString(prop.getKey(), prop.getValue());
				}
				state.put("Properties", props);
			}
			palette.addAndUnwrap(state);
			if ((i & 15) == 0) {
				progress.accept(5 + (int) (15.0 * (i + 1) / Math.max(1, paletteEntries.size())));
			}
		}
		root.put("palette", palette);
		progress.accept(25);

		ListTag blocks = new ListTag();
		List<RegionCapture.CapturedBlock> blockList = capture.blocks();
		int total = Math.max(1, blockList.size());
		for (int i = 0; i < blockList.size(); i++) {
			RegionCapture.CapturedBlock block = blockList.get(i);
			CompoundTag item = new CompoundTag();
			ListTag pos = new ListTag();
			pos.addAndUnwrap(IntTag.valueOf(block.x()));
			pos.addAndUnwrap(IntTag.valueOf(block.y()));
			pos.addAndUnwrap(IntTag.valueOf(block.z()));
			item.put("pos", pos);
			item.putInt("state", block.paletteIndex());
			blocks.addAndUnwrap(item);
			if ((i & 1023) == 0) {
				progress.accept(25 + (int) (60.0 * (i + 1) / total));
				Thread.yield();
			}
		}
		root.put("blocks", blocks);
		root.put("entities", new ListTag());

		CompoundTag origin = new CompoundTag();
		origin.putInt("x", capture.origin().getX());
		origin.putInt("y", capture.origin().getY());
		origin.putInt("z", capture.origin().getZ());
		root.put("mikasa_origin", origin);
		root.putString("mikasa_format", "mikasa-copy-building");
		root.putInt("mikasa_version", 1);

		progress.accept(90);
		NbtIo.writeCompressed(root, out);
		progress.accept(100);
	}
}
