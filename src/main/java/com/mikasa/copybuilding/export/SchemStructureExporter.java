package com.mikasa.copybuilding.export;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Sponge Schematic v2 (.schem) exporter.
 * Author: Mikasa
 */
public final class SchemStructureExporter {
	private SchemStructureExporter() {
	}

	public static void write(RegionCapture capture, Path out) throws Exception {
		write(capture, out, ExportProgress.noop());
	}

	public static void write(RegionCapture capture, Path out, ExportProgress progress) throws Exception {
		DenseBlockVolume volume = DenseBlockVolume.fromCapture(capture, false, progress);
		if (volume.sizeX() > Short.MAX_VALUE || volume.sizeY() > Short.MAX_VALUE || volume.sizeZ() > Short.MAX_VALUE) {
			throw new IllegalArgumentException("Region too large for .schem (max 32767 per axis)");
		}

		CompoundTag schematic = new CompoundTag();
		schematic.putInt("Version", 2);
		schematic.putInt("DataVersion", SharedConstants.WORLD_VERSION);
		schematic.putShort("Width", (short) volume.sizeX());
		schematic.putShort("Height", (short) volume.sizeY());
		schematic.putShort("Length", (short) volume.sizeZ());

		CompoundTag palette = new CompoundTag();
		List<RegionCapture.PaletteEntry> entries = volume.palette();
		for (int i = 0; i < entries.size(); i++) {
			palette.putInt(DenseBlockVolume.spongeStateString(entries.get(i)), i);
		}
		schematic.put("Palette", palette);
		schematic.putInt("PaletteMax", entries.size());

		byte[] blockData = encodeVarIntArray(volume.indicesYzx(), progress);
		schematic.put("BlockData", new ByteArrayTag(blockData));

		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", out.getFileName().toString());
		metadata.putString("Author", "Mikasa");
		metadata.putString("Software", "Copy That Building");
		schematic.put("Metadata", metadata);

		progress.accept(95);
		writeNamedGzip(schematic, "Schematic", out);
		progress.accept(100);
	}

	private static byte[] encodeVarIntArray(int[] values, ExportProgress progress) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(values.length + 64);
		for (int i = 0; i < values.length; i++) {
			writeVarInt(bos, values[i]);
			if ((i & 8191) == 0) {
				progress.accept(60 + (int) (30.0 * (i + 1) / Math.max(1, values.length)));
				Thread.yield();
			}
		}
		return bos.toByteArray();
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		int v = value;
		while ((v & ~0x7F) != 0) {
			out.write((v & 0x7F) | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}

	private static void writeNamedGzip(CompoundTag tag, String name, Path out) throws Exception {
		try (OutputStream file = Files.newOutputStream(out);
		     GZIPOutputStream gzip = new GZIPOutputStream(file);
		     DataOutputStream dos = new DataOutputStream(gzip)) {
			dos.writeByte(tag.getId());
			dos.writeUTF(name);
			tag.write(dos);
		}
	}
}
